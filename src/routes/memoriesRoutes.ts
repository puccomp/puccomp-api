import express, { RequestHandler, Router } from 'express'
import { memUpload, sanitizeFileName } from '../utils/uploads.js'
import { getS3URL, uploadObjectToS3, deleteObjectFromS3 } from '../utils/s3.js'
import { ImageMemory } from '@prisma/client'
import prisma from '../utils/prisma.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'
import { fileRequiredMiddleware } from '../middlewares/fileMiddleware.js'
import { multerErrorHandler } from '../middlewares/errorHandlers.js'

interface MemoryDTO {
  title: string
  description: string
  date: string
}

interface GetMemoriesQuery {
  sort_by?: 'date' | 'title' | 'id'
  order?: 'asc' | 'desc'
}

const router: Router = express.Router()

// SAVE
router.post(
  '/',
  isAuth,
  isAdmin,
  memUpload.single('image'),
  fileRequiredMiddleware,
  (async (req, res) => {
    const memoryImage = req.file!
    const { title, description, date } = req.body as MemoryDTO
    const imageKey = `memories/${sanitizeFileName(memoryImage.originalname)}`

    try {
      const existingMemory = await prisma.imageMemory.findUnique({
        where: { key: imageKey },
      })

      if (existingMemory) {
        res
          .status(409)
          .json({ message: 'Image with this name already exists.' })
        return
      }

      await uploadObjectToS3(memoryImage, imageKey)

      try {
        const newMemory = await prisma.imageMemory.create({
          data: { key: imageKey, title, description, date },
        })

        res.status(201).json({
          message: 'Memory image uploaded successfully.',
          image_url: getS3URL(newMemory.key),
        })
      } catch (dbError) {
        console.error(
          'Database insertion failed, attempting to clean up S3...',
          dbError
        )
        await deleteObjectFromS3(imageKey)
        throw dbError
      }
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to upload memory image.' })
    }
  }) as RequestHandler
)

router.use(multerErrorHandler)

// FIND ALL
router.get('/', (async (req, res) => {
  try {
    const { sort_by = 'date', order = 'desc' } = req.query as GetMemoriesQuery

    const allowedSortBy: (keyof ImageMemory)[] = ['date', 'title', 'id']
    const allowedOrder = ['asc', 'desc']

    if (!allowedSortBy.includes(sort_by)) {
      res.status(400).json({ message: 'Invalid sort by parameter.' })
      return
    }
    if (!allowedOrder.includes(order)) {
      res.status(400).json({ message: 'Invalid order parameter.' })
      return
    }

    const memories = await prisma.imageMemory.findMany({
      orderBy: {
        [sort_by]: order,
      },
    })

    const response = memories.map((mem) => ({
      id: mem.id,
      title: mem.title,
      description: mem.description,
      date: mem.date,
      image_url: getS3URL(mem.key),
    }))

    res.json(response)
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Failed to fetch images.' })
  }
}) as RequestHandler)

router.delete('/:id', isAuth, isAdmin, (async (req, res) => {
  const id = parseInt(req.params.id, 10)
  if (isNaN(id)) {
    res.status(400).json({ message: 'Invalid ID format.' })
    return
  }

  try {
    const memory = await prisma.imageMemory.findUnique({
      where: { id },
    })

    if (!memory) {
      res.status(404).json({ message: 'Image not found.' })
      return
    }

    await prisma.imageMemory.delete({ where: { id } })

    try {
      await deleteObjectFromS3(memory.key)
    } catch (s3Error) {
      console.error(
        `Failed to delete S3 object ${memory.key}, but DB record was removed.`,
        s3Error
      )
    }

    res.status(200).json({
      id: memory.id,
      message: 'Image deleted successfully.',
    })
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Failed to delete memory image.' })
  }
}) as RequestHandler<{ id: string }>)

export default router
