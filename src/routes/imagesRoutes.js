import express from 'express'
import fs from 'fs'
import path from 'path'
import db from '../db.js'
import { uploadProjectMulter } from '../multerConfig.js'

// MIDDLEWARES
import authMiddleware from '../middlewares/authMiddleware.js'

const router = express.Router()

// -------- PROJECTS --------

// GET ALL IMAGE
router.get('/projects', (req, res) => {
  try {
    const files = fs.readdirSync('uploads/projects')

    const baseUrl = `${req.protocol}://${req.get('host')}`

    const images = files.map((file) => {
      const projectName = path.parse(file).name
      const imageUrl = `${baseUrl}/api/images/projects/${file}`
      return { project_name: projectName, image_url: imageUrl }
    })
    return res.json(images)
  } catch (error) {
    console.error(error.message)
    return res.status(500).json({ error: 'Failed to retrieve images' })
  }
})

// SUBMIT IMAGE
router.post(
  '/projects/:project_name',
  uploadProjectMulter.single('image'),
  async (req, res) => {
    const { project_name } = req.params

    if (!req.file)
      return res.status(400).json({ error: 'No image file uploaded' })

    try {
      const stmt = db.prepare('SELECT id FROM project WHERE name = ?')
      const project = stmt.get(project_name)

      if (!project)
        return res.status(404).json({ error: 'Invalid project name' })

      const ext = path.extname(req.file.originalname)
      const newFilename = `${project_name}${ext}`
      const newFilePath = path.join('uploads/projects', newFilename)

      fs.writeFileSync(newFilePath, req.file.buffer)

      const baseUrl = `${req.protocol}://${req.get('host')}`
      const imageUrl = `${baseUrl}/api/images/projects/${newFilename}`

      const updateStmt = db.prepare(
        'UPDATE project SET image_url = ?, updated_at = CURRENT_DATE WHERE id = ?'
      )
      updateStmt.run(imageUrl, project.id)

      return res.status(201).json({
        message: 'Image uploaded successfully',
        image_url: imageUrl,
      })
    } catch (error) {
      console.error('Error processing image:', error.message)
      res.status(500).json({ error: 'Failed to process image' })
    }
  }
)

// GET IMAGE
router.get('/projects/:filename', (req, res) => {
  const { filename } = req.params

  try {
    const files = fs.readdirSync('uploads/projects')

    const file = files.find((f) => f === filename)

    if (!file) return res.status(404).json({ error: 'Image not found' })

    const filePath = path.join('uploads/projects', file)
    return res.sendFile(filePath, { root: '.' })
  } catch (error) {
    console.error(error.message)
    return res.status(500).json({ error: 'Failed to retrieve image' })
  }
})

// DELETE IMAGE
router.delete('/projects/:filename', authMiddleware, (req, res) => {
  const { filename } = req.params

  try {
    const files = fs.readdirSync('uploads/projects')
    const file = files.find((f) => f === filename)

    if (!file) return res.status(404).json({ error: 'Image not found' })

    const filePath = path.join('uploads/projects', file)

    fs.unlinkSync(filePath)

    const stmt = db.prepare(
      'UPDATE project SET image_url = NULL, updated_at = CURRENT_DATE WHERE name = ?'
    )
    const result = stmt.run(path.parse(filename).name)

    if (result.changes === 0)
      return res.status(404).json({ error: 'Project not found in database' })

    return res.status(200).json({ message: 'Image deleted successfully' })
  } catch (error) {
    console.error(error.message)
    return res.status(500).json({ error: 'Failed to delete image' })
  }
})

export default router
