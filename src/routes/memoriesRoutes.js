import express from 'express'
import db from '../db/db.js'
import { memUpload, sanitizeFileName } from '../utils/uploads.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'
import { fileRequiredMiddleware } from '../middlewares/fileMiddleware.js'
import { getS3URL, uploadObjectToS3, deleteObjectFromS3 } from '../utils/s3.js'
import { multerErrorHandler } from '../middlewares/errorHandlers.js'

const router = express.Router()

router.post(
  '/',
  isAuth,
  isAdmin,
  memUpload.single('image'),
  fileRequiredMiddleware,
  (req, res) => {
    const memoryImage = req.file
    const { title, description, date } = req.body
    try {
      const imageKey = `memories/${sanitizeFileName(memoryImage.originalname)}`

      if (exists(imageKey))
        return res.status(409).json({ message: 'Image already exists.' })

      db.prepare(
        'INSERT INTO image_memory (key, title, description, date) VALUES (?, ?, ?, ?)'
      ).run(imageKey, title, description, date)

      uploadObjectToS3(memoryImage, imageKey)

      res.status(201).json({
        message: 'Memory image uploaded successfully.',
        image_url: getS3URL(imageKey),
      })
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to upload memory image.' })
    }
  }
)

router.use(multerErrorHandler)

router.get('/', (req, res) => {
  try {
    const { sort_by = 'date', order = 'desc' } = req.query

    const allowedSortBy = ['date', 'title', 'id']
    const allowedOrder = ['asc', 'desc']

    if (!allowedSortBy.includes(sort_by))
      return res.status(400).json({ message: 'Invalid sort_by parameter.' })

    if (!allowedOrder.includes(order))
      return res.status(400).json({ message: 'Invalid order parameter.' })

    const images = db
      .prepare('SELECT * FROM image_memory')
      .all()
      .sort((a, b) => {
        const valA = a[sort_by]
        const valB = b[sort_by]

        if (valA === undefined || valB === undefined) return 0

        if (typeof valA === 'string' && typeof valB === 'string')
          return order === 'asc'
            ? valA.localeCompare(valB)
            : valB.localeCompare(valA)

        return order === 'asc' ? valA - valB : valB - valA
      })

    res.json(
      images.map(({ key, ...rest }) => ({
        ...rest,
        image_url: getS3URL(key),
      }))
    )
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to fetch images.' })
  }
})

router.delete('/:id', isAuth, isAdmin, async (req, res) => {
  const id = req.params.id
  try {
    const memory = db
      .prepare('SELECT id, key FROM image_memory WHERE id = ?')
      .get(id)

    if (!memory) return res.status(404).json({ message: 'Image not found.' })

    await deleteObjectFromS3(memory.key)

    db.prepare('DELETE FROM image_memory WHERE id = ?').run(id)

    res.status(200).json({
      key: memory.id,
      message: 'Image deleted successfully.',
    })
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to delete memory image.' })
  }
})

const exists = (imageKey) =>
  db.prepare('SELECT 1 FROM image_memory WHERE key = ?').get(imageKey)

export default router
