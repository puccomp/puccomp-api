import express, { RequestHandler, Router } from 'express'
import db from '../db/db.js'
import isAuth from '../middlewares/isAuth.js'
import { sendEmail } from '../utils/email.js'

const router: Router = express.Router()

// SUBMIT PROJECT PROPOSAL
router.post('/', async (req, res) => {
  try {
    const { fullName, phone, projectDescription, appFeatures, visualIdentity } =
      req.body

    const submissionDate = new Date().toISOString()
    const stmt = db.prepare(`
        INSERT INTO project_proposal
        (name, phone, description, features, visual_identity, date)
        VALUES (?, ?, ?, ?, ?, ?)
      `)
    stmt.run(
      fullName,
      phone,
      projectDescription,
      appFeatures,
      visualIdentity,
      submissionDate
    )

    res.status(200).json({ message: 'Data saved successfully' })

    const subject = `Nova Proposta de Projeto - Enviada por ${fullName}`
    const text = `
        Nome: ${fullName}
        Telefone: ${phone}
        Descrição: ${projectDescription}
        Features: ${appFeatures}
        Identidade Visual: ${visualIdentity}
        Data de envio: ${submissionDate}`

    try {
      await sendEmail(process.env.TARGET_EMAIL!, subject, text)
      console.log(`Email "${subject}" sent successfully.`)
    } catch (emailError) {
      console.error('Failed to send email:', (emailError as Error).message)
    }
  } catch (error) {
    res.status(500).json({ message: 'Error saving data' })
  }
})

// FIND ALL SUBMITS
router.get('/', isAuth, (req, res) => {
  try {
    const stmt = db.prepare('SELECT * FROM project_proposal')
    const proposals = stmt.all()
    res.status(200).json(proposals)
  } catch {
    res.status(500).json({ message: 'Error fetching data' })
  }
})

// FIND SUBMITS BY ID
router.get('/:id', isAuth, ((req, res) => {
  try {
    const { id } = req.params
    const stmt = db.prepare('SELECT * FROM project_proposal WHERE id = ?')
    const proposal = stmt.get(id)
    if (!proposal) return res.status(404).json({ message: 'Not found' })

    res.status(200).json(proposal)
  } catch {
    res.status(500).json({ message: 'Error fetching data' })
  }
}) as RequestHandler)

export default router
