import express, { Router } from 'express'
import isAuth from '../middlewares/isAuth.js'
import { sendEmail } from '../utils/email.js'
import prisma from '../utils/prisma.js'
import { validate, IdParamSchema } from '../utils/validate.js'
import { CreateProposalSchema } from '../schemas/proposalSchemas.js'

const router: Router = express.Router()

// SUBMIT PROJECT PROPOSAL
router.post('/', async (req, res) => {
  const body = validate(CreateProposalSchema, req.body, res)
  if (!body) return
  const { fullName, phone, projectDescription, appFeatures, visualIdentity } =
    body

  try {
    const newProposal = await prisma.projectProposal.create({
      data: {
        name: fullName,
        phone,
        description: projectDescription,
        features: appFeatures,
        visualIdentity,
      },
    })

    res.status(201).json({ message: 'Dados salvos com sucesso.' })

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
    res.status(500).json({ message: 'Erro ao salvar os dados.' })
  }
})

// FIND ALL SUBMITS
router.get('/', isAuth, async (_req, res) => {
  try {
    const proposals = await prisma.projectProposal.findMany()
    res.status(200).json(proposals)
  } catch {
    res.status(500).json({ message: 'Erro ao buscar os dados.' })
  }
})

// FIND SUBMIT BY ID
router.get('/:id', isAuth, async (req, res) => {
  const params = validate(IdParamSchema, req.params, res)
  if (!params) return

  try {
    const proposal = await prisma.projectProposal.findUnique({
      where: { id: params.id },
    })
    if (!proposal) {
      res.status(404).json({ message: 'Não encontrado.' })
      return
    }
    res.status(200).json(proposal)
  } catch {
    res.status(500).json({ message: 'Erro ao buscar os dados.' })
  }
})

export default router
