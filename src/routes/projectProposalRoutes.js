import express from 'express'
import db from '../db.js'
import authMiddleware from '../middleware/authMiddleware.js'
import { sendEmail } from '../utils/emailService.js'

const router = express.Router()

// SUBMIT PROJECT PROPOSAL
router.post('/', async (req, res) => {
  try {
    const {
      fullName,
      phone,
      projectDescription,
      appFeatures,
      visualIdentity,
      budget,
    } = req.body

    const submissionDate = new Date().toISOString()
    const stmt = db.prepare(`
        INSERT INTO project_proposals
        (name, phone, description, features, visual_identity, budget, date)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `)
    stmt.run(
      fullName,
      phone,
      projectDescription,
      appFeatures,
      visualIdentity,
      budget,
      submissionDate
    )

    res.status(200).json({ message: 'Data saved successfully' })

    const subject = `New Project Proposal - Submission from ${fullName}`
    const text = `
        Name: ${fullName}
        Phone: ${phone}
        Description: ${projectDescription}
        Features: ${appFeatures}
        Visual Identity: ${visualIdentity}
        Budget: ${budget}
        Date: ${submissionDate}`

    try {
      await sendEmail(process.env.TARGET_EMAIL, subject, text)
      console.log(`Email \"${subject}\" sent successfully.`)
    } catch (emailError) {
      console.error('Failed to send email:', emailError.message)
    }
  } catch (error) {
    res.status(500).json({ message: 'Error saving data' })
  }
})

// FIND ALL SUBMITS
router.get('/', authMiddleware, (req, res) => {
  try {
    const stmt = db.prepare('SELECT * FROM project_proposals')
    const proposals = stmt.all()
    res.status(200).json(proposals)
  } catch {
    res.status(500).json({ message: 'Error fetching data' })
  }
})

// FIND SUBMITS BY ID
router.get('/:id', authMiddleware, (req, res) => {
  try {
    const { id } = req.params
    const stmt = db.prepare('SELECT * FROM project_proposals WHERE id = ?')
    const proposal = stmt.get(id)
    if (!proposal) return res.status(404).json({ message: 'Not found' })

    res.status(200).json(proposal)
  } catch {
    res.status(500).json({ message: 'Error fetching data' })
  }
})

export default router
