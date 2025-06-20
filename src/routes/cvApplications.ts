import express, { RequestHandler, Router } from 'express'

import db from '../db/db.js'
import { memUpload, sanitizeFileName } from '../utils/uploads.js'
import { sendEmail } from '../utils/email.js'
import { uploadObjectToS3, getSignedS3URL } from '../utils/s3.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'
import { fileRequiredMiddleware } from '../middlewares/fileMiddleware.js'
import { multerErrorHandler } from '../middlewares/errorHandlers.js'

const router: Router = express.Router()

interface CVSubmissionDTO {
  fullName: string
  phone: string
  linkedIn?: string
  gitHub?: string
  course: string
  period: string
}

interface CV {
  id: number
  cv_key: string
  fullname: string
  phone: string
  linkedIn: string | null
  gitHub: string | null
  course: string
  period: string
}

router.post('/', memUpload.single('resume'), fileRequiredMiddleware, (async (
  req,
  res
) => {
  const { fullName, phone, linkedIn, gitHub, course, period } =
    req.body as CVSubmissionDTO

  try {
    const file = req.file!

    if (!file.mimetype.includes('pdf')) {
      res.status(400).send({ message: 'Only PDF files are allowed' })
      return
    }

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
      await sendEmail(process.env.TARGET_EMAIL!, subject, text, [
        {
          filename: file.originalname,
          content: file.buffer,
        },
      ])
      console.log(`Email \"${subject}\" sent successfully.`)
    } catch (emailError) {
      console.error('Failed to send email:', (emailError as Error).message)
    }

    res.status(200).send({ message: 'CV uploaded successfully' })
  } catch (err) {
    console.error('Error uploading CV:', err)
    res
      .status(500)
      .send({ message: 'Error uploading CV', error: (err as Error).message })
  }
}) as RequestHandler)

router.use(multerErrorHandler)

// FIND ALL CV APPLICATIONS
router.get('/', isAuth, isAdmin, (async (req, res) => {
  try {
    const applications = db
      .prepare('SELECT * FROM cv_application')
      .all() as CV[]

    const applicationsWithSignedURLs = await Promise.all(
      applications.map(async (app) => {
        const { cv_key, ...rest } = app
        const signedUrl = await getSignedS3URL(cv_key)
        return {
          ...rest,
          resume_url: signedUrl,
        }
      })
    )

    return res.json(applicationsWithSignedURLs)
  } catch (error) {
    console.error('Error fetching CV applications:', error)
    res.status(500).json({ message: 'Error retrieving CV applications' })
  }
}) as RequestHandler)

export default router
