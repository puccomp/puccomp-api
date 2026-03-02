import express, { Router } from 'express'
import { Prisma, ProjectProposal } from '@prisma/client'
import isAuth from '../middlewares/isAuth.js'
import { sendEmail } from '../utils/email.js'
import { formatDate } from '../utils/formats.js'
import prisma from '../utils/prisma.js'
import { validate, IdParamSchema } from '../utils/validate.js'
import {
  CreateProposalSchema,
  UpdateProposalSchema,
  ProposalQuerySchema,
} from '../schemas/proposalSchemas.js'

const router: Router = express.Router()

const sortMap: Record<string, string> = {
  created_at: 'createdAt',
  name: 'name',
  status: 'status',
}

const formatProposal = (p: ProjectProposal) => ({
  id: p.id,
  name: p.name,
  phone: p.phone,
  problem_description: p.problemDescription,
  solution_overview: p.solutionOverview,
  features: p.features,
  visual_identity: p.visualIdentity,
  reference_links: p.referenceLinks,
  budget_range: p.budgetRange,
  status: p.status,
  internal_notes: p.internalNotes,
  created_at: formatDate(p.createdAt),
  updated_at: formatDate(p.updatedAt),
})

// SUBMIT PROJECT PROPOSAL
router.post('/', async (req, res) => {
  const body = validate(CreateProposalSchema, req.body, res)
  if (!body) return

  const {
    full_name,
    phone,
    problem_description,
    solution_overview,
    app_features,
    visual_identity,
    reference_links,
    budget_range,
  } = body

  try {
    const newProposal = await prisma.projectProposal.create({
      data: {
        name: full_name,
        phone,
        problemDescription: problem_description,
        solutionOverview: solution_overview,
        features: app_features,
        visualIdentity: visual_identity,
        referenceLinks: reference_links,
        budgetRange: budget_range,
      },
    })

    res.status(201).json({ message: 'Dados salvos com sucesso.' })

    const subject = `Nova Proposta de Projeto - Enviada por ${full_name}`
    const text = `
        Nome: ${full_name}
        Telefone: ${phone}
        Problema: ${problem_description}
        Visão de solução: ${solution_overview ?? '-'}
        Features: ${app_features ?? '-'}
        Identidade Visual: ${visual_identity ?? '-'}
        Links de referência: ${reference_links?.join(', ') ?? '-'}
        Faixa de investimento: ${budget_range ?? '-'}
        Data de envio: ${newProposal.createdAt.toISOString()}`

    try {
      await sendEmail(process.env.TARGET_EMAIL!, subject, text)
      console.log(`Email "${subject}" sent successfully.`)
    } catch (emailError) {
      console.error('Failed to send email:', (emailError as Error).message)
    }
  } catch {
    res.status(500).json({ message: 'Erro ao salvar os dados.' })
  }
})

// LIST ALL PROPOSALS
router.get('/', isAuth, async (req, res) => {
  const query = validate(ProposalQuerySchema, req.query, res)
  if (!query) return

  const { search, status, date_from, date_to, page, limit, sort_by, order } =
    query

  const where: Prisma.ProjectProposalWhereInput = {}

  if (search) {
    where.OR = [
      { name: { contains: search, mode: 'insensitive' } },
      { problemDescription: { contains: search, mode: 'insensitive' } },
    ]
  }

  if (status) {
    where.status = status
  }

  if (date_from || date_to) {
    where.createdAt = {
      ...(date_from && { gte: new Date(date_from) }),
      ...(date_to && { lte: new Date(`${date_to}T23:59:59.999Z`) }),
    }
  }

  try {
    const [proposals, total] = await Promise.all([
      prisma.projectProposal.findMany({
        where,
        orderBy: { [sortMap[sort_by]]: order },
        skip: (page - 1) * limit,
        take: limit,
      }),
      prisma.projectProposal.count({ where }),
    ])

    res.json({
      data: proposals.map(formatProposal),
      pagination: {
        total,
        page,
        limit,
        total_pages: Math.ceil(total / limit),
      },
    })
  } catch {
    res.status(500).json({ message: 'Erro ao buscar os dados.' })
  }
})

// GET PROPOSAL BY ID
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
    res.json(formatProposal(proposal))
  } catch {
    res.status(500).json({ message: 'Erro ao buscar os dados.' })
  }
})

// UPDATE STATUS / INTERNAL NOTES
router.patch('/:id', isAuth, async (req, res) => {
  const params = validate(IdParamSchema, req.params, res)
  if (!params) return

  const body = validate(UpdateProposalSchema, req.body, res)
  if (!body) return

  try {
    const updated = await prisma.projectProposal.update({
      where: { id: params.id },
      data: {
        status: body.status,
        internalNotes: body.internal_notes,
      },
    })
    res.json({
      message: 'Proposta atualizada com sucesso.',
      proposal: formatProposal(updated),
    })
  } catch (err) {
    if (
      err instanceof Prisma.PrismaClientKnownRequestError &&
      err.code === 'P2025'
    ) {
      res.status(404).json({ message: 'Proposta não encontrada.' })
      return
    }
    res.status(500).json({ message: 'Erro ao atualizar a proposta.' })
  }
})

export default router
