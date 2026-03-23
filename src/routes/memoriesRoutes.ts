import express, { RequestHandler, Router } from 'express'
import sharp from 'sharp'
import { memUpload, sanitizeFileName } from '../utils/uploads.js'
import { getS3URL, uploadObjectToS3, deleteObjectFromS3 } from '../utils/s3.js'
import prisma from '../utils/prisma.js'
import { Prisma } from '@prisma/client'
import { validate, IdParamSchema } from '../utils/validate.js'
import {
  MemoryQuerySchema,
  CreateMemorySchema,
  UpdateMemorySchema,
} from '../schemas/memorySchemas.js'

async function processMemoryImage(buffer: Buffer): Promise<Buffer> {
  return sharp(buffer)
    .resize(1200, 675, { fit: 'cover', position: 'centre' })
    .webp({ quality: 82 })
    .toBuffer()
}

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'
import { fileRequiredMiddleware } from '../middlewares/fileMiddlewares.js'
import { multerErrorHandler } from '../middlewares/errorHandlers.js'

const router: Router = express.Router()

// SAVE
router.post(
  '/',
  isAuth,
  isAdmin,
  memUpload.single('image'),
  fileRequiredMiddleware,
  (async (req, res) => {
    const body = validate(CreateMemorySchema, req.body, res)
    if (!body) return
    const memoryImage = req.file!
    const { title, description, date } = body
    const baseName = sanitizeFileName(memoryImage.originalname).replace(
      /\.[^.]+$/,
      ''
    )
    const imageKey = `memories/${Date.now()}_${baseName}.webp`

    try {
      const processedBuffer = await processMemoryImage(memoryImage.buffer)
      await uploadObjectToS3(
        {
          buffer: processedBuffer,
          mimetype: 'image/webp',
          originalname: `${baseName}.webp`,
        },
        imageKey
      )

      try {
        const newMemory = await prisma.imageMemory.create({
          data: { key: imageKey, title, description, date },
        })

        res.status(201).json({
          message: 'Imagem de memória enviada com sucesso.',
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
      res.status(500).json({ message: 'Falha ao enviar a imagem de memória.' })
    }
  }) as RequestHandler
)

router.use(multerErrorHandler)

// FIND ALL
router.get('/', (async (req, res) => {
  const query = validate(MemoryQuerySchema, req.query, res)
  if (!query) return
  const { sort_by, order, page, limit } = query
  const skip = (page - 1) * limit

  try {
    const [memories, total] = await Promise.all([
      prisma.imageMemory.findMany({
        orderBy: { [sort_by]: order },
        take: limit,
        skip,
      }),
      prisma.imageMemory.count(),
    ])

    res.json({
      data: memories.map((mem) => ({
        id: mem.id,
        title: mem.title,
        description: mem.description,
        date: mem.date,
        image_url: getS3URL(mem.key),
      })),
      pagination: {
        total,
        page,
        limit,
        total_pages: Math.ceil(total / limit),
      },
    })
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Falha ao buscar as imagens.' })
  }
}) as RequestHandler)

router.patch('/:id', isAuth, isAdmin, (async (req, res) => {
  const params = validate(IdParamSchema, req.params, res)
  if (!params) return
  const body = validate(UpdateMemorySchema, req.body, res)
  if (!body) return

  try {
    const updated = await prisma.imageMemory.update({
      where: { id: params.id },
      data: body,
    })

    res.json({
      message: 'Memória atualizada com sucesso.',
      id: updated.id,
    })
  } catch (err) {
    if (
      err instanceof Prisma.PrismaClientKnownRequestError &&
      err.code === 'P2025'
    ) {
      res.status(404).json({ message: 'Imagem não encontrada.' })
      return
    }
    console.error(err)
    res.status(500).json({ message: 'Falha ao atualizar a memória.' })
  }
}) as RequestHandler<{ id: string }>)

router.delete('/:id', isAuth, isAdmin, (async (req, res) => {
  const params = validate(IdParamSchema, req.params, res)
  if (!params) return

  try {
    const memory = await prisma.imageMemory.delete({
      where: { id: params.id },
    })

    try {
      await deleteObjectFromS3(memory.key)
    } catch (s3Error) {
      console.error(
        `Failed to delete S3 object ${memory.key}, but DB record was removed.`,
        s3Error
      )
    }

    res.json({ id: memory.id, message: 'Imagem excluída com sucesso.' })
  } catch (err) {
    if (
      err instanceof Prisma.PrismaClientKnownRequestError &&
      err.code === 'P2025'
    ) {
      res.status(404).json({ message: 'Imagem não encontrada.' })
      return
    }
    console.error(err)
    res.status(500).json({ message: 'Falha ao excluir a imagem de memória.' })
  }
}) as RequestHandler<{ id: string }>)

export default router
