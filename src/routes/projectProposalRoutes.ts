import express, { Router } from 'express'
import isAuth from '../middlewares/isAuth.js'
import { sendEmail } from '../utils/email.js'
import prisma from '../utils/prisma.js'

const router: Router = express.Router()

// SUBMIT PROJECT PROPOSAL
router.post('/', async (req, res) => {
  try {
    const { fullName, phone, projectDescription, appFeatures, visualIdentity } =
      req.body

    const newProposal = await prisma.projectProposal.create({
      data: {
        name: fullName,
        phone,
        description: projectDescription,
        features: appFeatures,
        visualIdentity,
      },
    })

    res.status(200).json({ message: 'Data saved successfully' })

    const subject = `Nova Proposta de Projeto - Enviada por ${fullName}`
    const text = `
        Nome: ${fullName}
        Telefone: ${phone}
        Descrição: ${projectDescription}
        Features: ${appFeatures}
        Identidade Visual: ${visualIdentity}
        Data de envio: ${newProposal.date.toISOString()}`

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
router.get('/', isAuth, async (_req, res) => {
  try {
    const proposals = await prisma.projectProposal.findMany()
    res.status(200).json(proposals)
  } catch {
    res.status(500).json({ message: 'Error fetching data' })
  }
})

// FIND SUBMITS BY ID
router.get('/:id', isAuth, async (req, res) => {
  try {
    const { id } = req.params
    const proposal = await prisma.projectProposal.findUnique({
      where: { id: Number(id) },
    })
    if (!proposal) {
      res.status(404).json({ message: 'Not found' })
      return
    }
    res.status(200).json(proposal)
  } catch {
    res.status(500).json({ message: 'Error fetching data' })
  }
})

export default router
