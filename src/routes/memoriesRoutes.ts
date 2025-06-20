import express, { RequestHandler, Router } from 'express'
import db from '../db/db.js'
import { memUpload, sanitizeFileName } from '../utils/uploads.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'
import { fileRequiredMiddleware } from '../middlewares/fileMiddleware.js'
import { getS3URL, uploadObjectToS3, deleteObjectFromS3 } from '../utils/s3.js'
import { multerErrorHandler } from '../middlewares/errorHandlers.js'

interface MemoryDTO {
  title: string
  description: string
  date: string
}

interface ImageMemory {
  id: number
  key: string
  title: string
  description: string
  date: string
}

interface GetMemoriesQuery {
  sort_by?: 'date' | 'title' | 'id'
  order?: 'asc' | 'desc'
}

const router: Router = express.Router()

router.post(
  '/',
  isAuth,
  isAdmin,
  memUpload.single('image'),
  fileRequiredMiddleware,
  ((req, res) => {
    const memoryImage = req.file!
    const { title, description, date } = req.body as MemoryDTO
    try {
      const imageKey = `memories/${sanitizeFileName(memoryImage.originalname)}`

      if (exists(imageKey)) {
        res
          .status(409)
          .json({ message: 'Image with this name already exists.' })
        return
      }

      db.prepare(
        'INSERT INTO image_memory (key, title, description, date) VALUES (?, ?, ?, ?)'
      ).run(imageKey, title, description, date)

      uploadObjectToS3(memoryImage, imageKey)

      res.status(201).json({
        message: 'Memory image uploaded successfully.',
        image_url: getS3URL(imageKey),
      })
    } catch (err) {
      console.error((err as Error).message)
      res.status(500).json({ message: 'Failed to upload memory image.' })
    }
  }) as RequestHandler
)

router.use(multerErrorHandler)

router.get('/', ((req, res) => {
  try {
    const { sort_by = 'date', order = 'desc' } = req.query as GetMemoriesQuery

    const allowedSortBy = ['date', 'title', 'id']
    const allowedOrder = ['asc', 'desc']

    if (!allowedSortBy.includes(sort_by)) {
      res.status(400).json({ message: 'Invalid sort by parameter.' })
      return
    }

    if (!allowedOrder.includes(order)) {
      res.status(400).json({ message: 'Invalid order parameter.' })
      return
    }

    const images = db
      .prepare('SELECT * FROM image_memory')
      .all() as ImageMemory[]

    images.sort((a, b) => {
      const valA = a[sort_by]
      const valB = b[sort_by]

      if (
        valA === undefined ||
        valB === undefined ||
        valA === null ||
        valB === null
      )
        return 0

      if (typeof valA === 'string' && typeof valB === 'string') {
        return order === 'asc'
          ? valA.localeCompare(valB)
          : valB.localeCompare(valA)
      }

      return order === 'asc'
        ? (valA as any) - (valB as any)
        : (valB as any) - (valA as any)
    })

    res.json(
      images.map(({ key, ...rest }) => ({
        ...rest,
        image_url: getS3URL(key),
      }))
    )
  } catch (err) {
    console.error((err as Error).message)
    res.status(500).json({ message: 'Failed to fetch images.' })
  }
}) as RequestHandler)

router.delete('/:id', isAuth, isAdmin, (async (req, res) => {
  try {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ message: 'Invalid ID format.' })
      return
    }

    const memory = db
      .prepare('SELECT id, key FROM image_memory WHERE id = ?')
      .get(id) as Pick<ImageMemory, 'id' | 'key'> | undefined

    if (!memory) {
      res.status(404).json({ message: 'Image not found.' })
      return
    }

    await deleteObjectFromS3(memory.key)

    db.prepare('DELETE FROM image_memory WHERE id = ?').run(id)

    res.status(200).json({
      key: memory.id,
      message: 'Image deleted successfully.',
    })
  } catch (err) {
    console.error((err as Error).message)
    res.status(500).json({ message: 'Failed to delete memory image.' })
  }
}) as RequestHandler<{ id: string }>)

const exists = (imageKey: string): boolean => {
  const result = db
    .prepare('SELECT 1 FROM image_memory WHERE key = ?')
    .get(imageKey)
  return !!result
}

export default router
