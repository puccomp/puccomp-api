import express, { RequestHandler, Router } from 'express'

import { createUpload } from '../utils/uploads.js'
import { sendEmail } from '../utils/email.js'
import {
  uploadObjectToS3,
  getSignedS3URL,
  deleteObjectFromS3,
} from '../utils/s3.js'
import { sanitizeFileName } from '../utils/uploads.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'
import {
  fileRequiredMiddleware,
  validatePdfFileMiddleware,
} from '../middlewares/fileMiddlewares.js'
import { multerErrorHandler } from '../middlewares/errorHandlers.js'
import prisma from '../utils/prisma.js'
import { validate } from '../utils/validate.js'
import { CVSubmissionSchema, CVQuerySchema } from '../schemas/cvSchemas.js'

const router: Router = express.Router()

const cvUpload = createUpload(5)

router.post(
  '/',
  cvUpload.single('resume'),
  fileRequiredMiddleware,
  validatePdfFileMiddleware,
  (async (req, res) => {
    const body = validate(CVSubmissionSchema, req.body, res)
    if (!body) return
    const { fullName, phone, linkedIn, gitHub, course, period } = body
    const file = req.file!

    const sanitizedFileName = sanitizeFileName(file.originalname)
    const cvKey = `resumes/${Date.now()}_${sanitizedFileName}`

    try {
      await uploadObjectToS3(file, cvKey)

      try {
        await prisma.cvApplication.create({
          data: {
            cvKey,
            fullname: fullName,
            phone,
            linkedin: linkedIn,
            github: gitHub,
            course,
            period,
          },
        })
      } catch (dbError) {
        console.error(
          'Database insertion failed. Rolling back S3 upload...',
          dbError
        )
        await deleteObjectFromS3(cvKey)
        throw dbError
      }
      res.status(201).send({ message: 'Currículo enviado com sucesso.' })

      try {
        const subject = `CV - Enviada por ${fullName}`
        const text = `
          Nome: ${fullName}
          Telefone: ${phone}
          LinkedIn: ${linkedIn || 'N/A'}
          GitHub: ${gitHub || 'N/A'}
          Curso: ${course}
          Período: ${period}
        `
        await sendEmail(process.env.TARGET_EMAIL!, subject, text, [
          { filename: file.originalname, content: file.buffer },
        ])
        console.log(`Email "${subject}" sent successfully.`)
      } catch (emailError) {
        console.error(
          'CV was saved, but failed to send notification email:',
          emailError
        )
      }
    } catch (err) {
      console.error('Error during CV submission process:', err)
      res.status(500).send({ message: 'Erro ao enviar o currículo.' })
    }
  }) as RequestHandler
)

router.use(multerErrorHandler)

// FIND ALL CV APPLICATIONS
router.get('/', isAuth, isAdmin, (async (req, res) => {
  const query = validate(CVQuerySchema, req.query, res)
  if (!query) return
  const {
    page,
    limit,
    sort_by,
    order,
    search,
    course,
    period,
    submitted_from,
    submitted_to,
  } = query
  const skip = (page - 1) * limit

  const where = {
    ...(search && {
      fullname: { contains: search, mode: 'insensitive' as const },
    }),
    ...(course && { course: { equals: course, mode: 'insensitive' as const } }),
    ...(period && { period }),
    ...((submitted_from || submitted_to) && {
      submittedAt: {
        ...(submitted_from && { gte: new Date(submitted_from) }),
        ...(submitted_to && { lte: new Date(submitted_to) }),
      },
    }),
  }

  try {
    const [applications, total] = await Promise.all([
      prisma.cvApplication.findMany({
        where,
        orderBy: { [sort_by]: order },
        take: limit,
        skip,
      }),
      prisma.cvApplication.count({ where }),
    ])

    const applicationsWithSignedURLs = await Promise.all(
      applications.map(async (app) => {
        const signedUrl = await getSignedS3URL(app.cvKey)
        return {
          fullname: app.fullname,
          phone: app.phone,
          linkedin: app.linkedin,
          github: app.github,
          course: app.course,
          period: app.period,
          submitted_at: app.submittedAt.toISOString(),
          resume_url: signedUrl,
        }
      })
    )

    res.json({
      data: applicationsWithSignedURLs,
      pagination: {
        total,
        page,
        limit,
        total_pages: Math.ceil(total / limit),
      },
    })
  } catch (error) {
    console.error('Error fetching CV applications:', error)
    res.status(500).json({ message: 'Erro ao buscar as candidaturas.' })
  }
}) as RequestHandler)

export default router
