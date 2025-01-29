import express from 'express'
import path from 'path'
import { uploadResumeMulter } from '../multerConfig.js'
import database from '../db.js'
import { sendEmail } from '../utils/emailService.js'

//MIDDLEWARES
import authMiddleware from '../middlewares/authMiddleware.js'
import adminMiddleware from '../middlewares/adminMiddleware.js'

const router = express.Router()

// SUBMIT CV
router.post('/', uploadResumeMulter.single('resume'), async (req, res) => {
  const { fullName, phone, linkedIn, gitHub, course, period } = req.body

  if (!req.file)
    return res.status(400).json({ error: 'Resume file is required' })

  const filename = req.file.filename
  const resumeFilePath = path.join('uploads/resumes', req.file.filename)

  try {
    const stmt = database.prepare(`
        INSERT INTO cv_application (cv_filename, fullname, phone, linkedIn, gitHub, course, period)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `)

    stmt.run(filename, fullName, phone, linkedIn, gitHub, course, period)

    res.status(201).json({ message: 'CV submitted successfully' })

    const subject = `CV - Enviada por ${fullName}`
    const text = `
        Nome: ${fullName}
        Telefone: ${phone}
        LinkedIn: ${linkedIn}
        GitHub: ${gitHub}
        Curso: ${course}
        Período: ${period}
      `

    try {
      await sendEmail(process.env.TARGET_EMAIL, subject, text, [
        {
          filename: req.file.originalname,
          path: resumeFilePath,
        },
      ])
      console.log(`Email \"${subject}\" sent successfully.`)
    } catch (emailError) {
      console.error('Failed to send email:', emailError.message)
    }
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to save application' })
  }
})

// FIND ALL SUBMITS
router.get('/', authMiddleware, adminMiddleware, (req, res) => {
  try {
    const stmt = database.prepare('SELECT * FROM cv_application')
    const applications = stmt.all()

    const baseUrl = `${req.protocol}://${req.get('host')}`

    res.json(
      applications.map((app) => {
        const { cv_filename, ...rest } = app
        return {
          ...rest,
          cv_url: `${baseUrl}/api/cv-applications/${cv_filename}`,
        }
      })
    )
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to retrieve applications' })
  }
})

// FIND CV FILE
router.get('/:filename', authMiddleware, adminMiddleware, (req, res) => {
  const { filename } = req.params

  const filePath = path.join('uploads/resumes', filename)
  console.log(filePath)
  res.sendFile(filePath, { root: '.' }, (err) => {
    if (err) {
      console.error(err.message)
      res.status(404).json({ error: 'File not found' })
    }
  })
})

export default router
