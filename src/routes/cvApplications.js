import express from 'express'

import db from '../db/db.js'
import { memUpload, sanitizeFileName} from '../utils/uploads.js'
import { sendEmail } from '../utils/email.js'
import { uploadObjectToS3, getSignedS3URL } from '../utils/s3.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'
import { fileRequiredMiddleware } from '../middlewares/fileMiddleware.js'

const router = express.Router()

router.post(
  '/',
  memUpload.single('resume'),
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
router.get('/', isAuth, isAdmin, async (req, res) => {
  try {
    const applications = db.prepare('SELECT * FROM cv_application').all()

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
})


export default router
