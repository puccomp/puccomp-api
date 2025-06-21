import express, { RequestHandler, Router } from 'express'

import { memUpload, sanitizeFileName } from '../utils/uploads.js'
import { sendEmail } from '../utils/email.js'
import {
  uploadObjectToS3,
  getSignedS3URL,
  deleteObjectFromS3,
} from '../utils/s3.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'
import { fileRequiredMiddleware } from '../middlewares/fileMiddleware.js'
import { multerErrorHandler } from '../middlewares/errorHandlers.js'
import { prisma } from '../index.js'

const router: Router = express.Router()

interface CVSubmissionDTO {
  fullName: string
  phone: string
  linkedIn?: string
  gitHub?: string
  course: string
  period: string
}

router.post('/', memUpload.single('resume'), fileRequiredMiddleware, (async (
  req,
  res
) => {
  const { fullName, phone, linkedIn, gitHub, course, period } =
    req.body as CVSubmissionDTO
  const file = req.file!

  if (!file.mimetype.includes('pdf')) {
    res.status(400).send({ message: 'Only PDF files are allowed' })
    return
  }

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
        {
          filename: file.originalname,
          content: file.buffer,
        },
      ])
      console.log(`Email "${subject}" sent successfully.`)
    } catch (emailError) {
      console.error(
        'CV was saved, but failed to send notification email:',
        emailError
      )
    }
    res.status(200).send({ message: 'CV uploaded successfully' })
  } catch (err) {
    console.error('Error during CV submission process:', err)
    res.status(500).send({ message: 'Error uploading CV' })
  }
}) as RequestHandler)

router.use(multerErrorHandler)

// FIND ALL CV APPLICATIONS
router.get('/', isAuth, isAdmin, (async (_req, res) => {
  try {
    const applications = await prisma.cvApplication.findMany()

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
          resume_url: signedUrl,
        }
      })
    )

    res.json(applicationsWithSignedURLs)
  } catch (error) {
    console.error('Error fetching CV applications:', error)
    res.status(500).json({ message: 'Error retrieving CV applications' })
  }
}) as RequestHandler)

export default router
