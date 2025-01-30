import express from 'express'
import multer from 'multer'

import db from '../db/db.js'
import { sendEmail } from '../utils/emailService.js'
import {
  uploadObjectToS3,
  deleteObjectFromS3,
  getSignedS3URL,
} from '../utils/s3Service.js.js'

// MIDDLEWARES
import authMiddleware from '../middlewares/authMiddleware.js'
import adminMiddleware from '../middlewares/adminMiddleware.js'
import { projectExistsMiddleware } from '../middlewares/projectMiddleware.js'
import { fileRequiredMiddleware } from '../middlewares/fileMiddleware.js'

const router = express.Router()

const storage = multer.memoryStorage()
const upload = multer({ storage })

function sanitizeFileName(filename) {
  return filename
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9._-]/g, '_')
    .replace(/\s+/g, '_')
    .toLowerCase()
}

// UPLOAD PROJECT IMAGE
router.post(
  '/projects/:project_name',
  authMiddleware,
  projectExistsMiddleware,
  upload.single('image'),
  fileRequiredMiddleware,
  async (req, res) => {
    try {
      const file = req.file
      const oldImageKey = req.project.image_key

      if (oldImageKey) await deleteObjectFromS3(oldImageKey)

      const sanitizedFileName = sanitizeFileName(file.originalname)
      const imageKey = `projects/${Date.now()}_${sanitizedFileName}`

      updateProjectImageKey(req.project, imageKey)

      const data = await uploadObjectToS3(file, imageKey)

      res.status(200).send({
        message: oldImageKey
          ? 'Previous image replaced successfully'
          : 'File uploaded successfully',
        image_url: data.Location,
      })
    } catch (err) {
      console.error('Error uploading file:', err)
      res.status(500).send({ message: 'Error uploading file:', err })
    }
  }
)

// DELETE PROJECT IMAGE
router.delete(
  '/projects/:project_name',
  authMiddleware,
  projectExistsMiddleware,
  async (req, res) => {
    try {
      const imageKey = req.project.image_key

      if (!imageKey)
        return res
          .status(404)
          .send({ message: 'No image found for this project' })

      await deleteObjectFromS3(imageKey)

      clearProjectImageKey(req.project)

      res.status(200).send({ message: 'Image deleted successfully' })
    } catch (err) {
      console.error('Error deleting image:', err)
      res
        .status(500)
        .send({ message: 'Error deleting image', error: err.message })
    }
  }
)

// UPLOAD CV APPLICATION
router.post(
  '/cv-applications',
  upload.single('resume'),
  fileRequiredMiddleware,
  async (req, res) => {
    const { fullName, phone, linkedIn, gitHub, course, period } = req.body

    try {
      const file = req.file

      if (!file.mimetype.includes('pdf'))
        return res.status(400).send({ message: 'Only PDF files are allowed' })

      const sanitizedFileName = sanitizeFileName(file.originalname)
      const cvKey = `resumes/${Date.now()}_${sanitizedFileName}`

      db.prepare(
        `
        INSERT INTO cv_application (cv_key, fullname, phone, linkedIn, gitHub, course, period)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `
      ).run(cvKey, fullName, phone, linkedIn, gitHub, course, period)

      await uploadObjectToS3(file, cvKey)

      try {
        const subject = `CV - Enviada por ${fullName}`
        const text = `
          Nome: ${fullName}
          Telefone: ${phone}
          LinkedIn: ${linkedIn}
          GitHub: ${gitHub}
          Curso: ${course}
          Período: ${period}
        `
        await sendEmail(process.env.TARGET_EMAIL, subject, text, [
          {
            filename: file.originalname,
            content: file.buffer,
          },
        ])
        console.log(`Email \"${subject}\" sent successfully.`)
      } catch (emailError) {
        console.error('Failed to send email:', emailError.message)
      }

      return res.status(200).send({ message: 'CV uploaded successfully' })
    } catch (err) {
      console.error('Error uploading CV:', err)
      res
        .status(500)
        .send({ message: 'Error uploading CV', error: err.message })
    }
  }
)

// FIND ALL CV APPLICATIONS
router.get('/cv-applications', authMiddleware, adminMiddleware, (req, res) => {
  const stmt = db.prepare('SELECT * FROM cv_application')
  const applications = stmt.all()

  return res.json(
    applications.map((app) => {
      const { cv_key, ...rest } = app
      return {
        ...rest,
        resume_url: getSignedS3URL(cv_key),
      }
    })
  )
})

function updateProjectImageKey(project, imageKey) {
  const result = db
    .prepare(
      'UPDATE project SET image_key = ?, updated_at = CURRENT_DATE WHERE id = ?'
    )
    .run(imageKey, project.id)
  if (result.changes === 0) throw new Error('Project not found')
}

function clearProjectImageKey(project) {
  db.prepare(
    'UPDATE project SET image_key = NULL, updated_at = CURRENT_DATE WHERE id = ?'
  ).run(project.id)
}

export default router
