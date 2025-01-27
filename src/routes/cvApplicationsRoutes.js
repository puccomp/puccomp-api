import express from 'express'
import path from 'path'
import upload from '../multerConfig.js'
import database from '../db.js'
import { sendEmail } from '../utils/emailService.js'

//MIDDLEWARES
import authMiddleware from '../middlewares/authMiddleware.js'
import adminMiddleware from '../middlewares/adminMiddleware.js'

const router = express.Router()

// SUBMIT CV
router.post('/', upload.single('resume'), async (req, res) => {
  const { fullName, phone, linkedIn, gitHub, course, period } = req.body

  if (!req.file)
    return res.status(400).json({ error: 'Resume file is required' })

  const resumeFilePath = path.join('uploads', req.file.filename)

  try {
    const stmt = database.prepare(`
        INSERT INTO cv_application (name, phone, linkedIn, gitHub, course, period, resume)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `)

    stmt.run(fullName, phone, linkedIn, gitHub, course, period, resumeFilePath)

    res.status(201).json({ message: 'CV Application submitted successfully' })

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

    res.json(applications)
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to retrieve applications' })
  }
})

// FIND CV FILE
router.get('/resume/:filename', authMiddleware, adminMiddleware, (req, res) => {
  const { filename } = req.params

  const filePath = path.join('uploads', filename)
  res.sendFile(filePath, { root: '.' }, (err) => {
    if (err) {
      console.error(err.message)
      res.status(404).json({ error: 'File not found' })
    }
  })
})

export default router
