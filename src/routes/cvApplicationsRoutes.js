import express from 'express'
import path from 'path'
import upload from '../multerConfig.js'
import database from '../db.js'
import authMiddleware from '../middleware/authMiddleware.js'
import { sendEmail } from '../utils/emailService.js'

const router = express.Router()

// SUBMIT CV
router.post('/', upload.single('resume'), async (req, res) => {
  const { fullName, phone, linkedIn, gitHub, course, period } = req.body

  if (!req.file)
    return res.status(400).json({ error: 'Resume file is required' })

  const resumeFilePath = path.join('uploads', req.file.filename)

  try {
    const stmt = database.prepare(`
        INSERT INTO cv_pplications (name, phone, linkedIn, gitHub, course, period, resume)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `)

    stmt.run(fullName, phone, linkedIn, gitHub, course, period, resumeFilePath)

    res.status(201).json({ message: 'CV Application submitted successfully' })

    const subject = `CV - Submission from ${fullName}`
    const text = `
        Name: ${fullName}
        Phone: ${phone}
        LinkedIn: ${linkedIn}
        GitHub: ${gitHub}
        Course: ${course}
        Period: ${period}
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
router.get('/', authMiddleware, (req, res) => {
  try {
    const stmt = database.prepare('SELECT * FROM cv_pplications')
    const applications = stmt.all()

    res.json(applications)
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to retrieve applications' })
  }
})

// FIND CV FILE
router.get('/resume/:filename', authMiddleware, (req, res) => {
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
