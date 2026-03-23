import express, { RequestHandler, Router } from 'express'
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'
import { BASE_URL } from '../index.js'
import { Prisma, Technology } from '@prisma/client'
import prisma from '../utils/prisma.js'
import { validate, IdParamSchema } from '../utils/validate.js'
import { formatDate } from '../utils/formats.js'
import { generateSlug } from '../utils/slug.js'
import {
  CreateTechnologySchema,
  UpdateTechnologySchema,
  TechnologyQuerySchema,
} from '../schemas/technologySchemas.js'

const router: Router = express.Router()

type TechnologyWithCount = Technology & { _count: { projects: number } }

const formatTechnology = (tech: TechnologyWithCount) => {
  const { iconUrl, createdAt, updatedAt, _count, ...rest } = tech
  return {
    ...rest,
    icon_url: iconUrl,
    project_count: _count.projects,
    created_at: formatDate(createdAt),
    updated_at: formatDate(updatedAt),
  }
}

// FIND ALL
router.get('/', (async (req, res) => {
  const query = validate(TechnologyQuerySchema, req.query, res)
  if (!query) return

  const { search, type, exclude_project, limit } = query

  try {
    const technologies = await prisma.technology.findMany({
      where: {
        ...(search && { name: { contains: search, mode: 'insensitive' } }),
        ...(type && { type }),
        ...(exclude_project && {
          NOT: { projects: { some: { project: { slug: exclude_project } } } },
        }),
      },
      include: { _count: { select: { projects: true } } },
      orderBy: { name: 'asc' },
      take: limit,
    })
    res.json(technologies.map(formatTechnology))
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Falha ao buscar as tecnologias.' })
  }
}) as RequestHandler)

// FIND BY ID
router.get('/:id', (async (req, res) => {
  const params = validate(IdParamSchema, req.params, res)
  if (!params) return

  try {
    const technology = await prisma.technology.findUnique({
      where: { id: params.id },
      include: { _count: { select: { projects: true } } },
    })
    if (!technology) {
      res.status(404).json({ message: 'Tecnologia não encontrada.' })
      return
    }
    res.json(formatTechnology(technology))
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Falha ao buscar a tecnologia.' })
  }
}) as RequestHandler)

// SAVE
router.post('/', isAuth, isAdmin, (async (req, res) => {
  const body = validate(CreateTechnologySchema, req.body, res)
  if (!body) return

  const { name, icon_url, type, color, description } = body
  const slug = generateSlug(name)

  try {
    const newTechnology = await prisma.technology.create({
      data: { name, slug, iconUrl: icon_url, type, color, description },
    })
    res.status(201).json({
      message: 'Tecnologia criada com sucesso.',
      technology_url: `${BASE_URL}/api/technologies/${newTechnology.id}`,
    })
  } catch (err) {
    if (
      err instanceof Prisma.PrismaClientKnownRequestError &&
      err.code === 'P2002'
    ) {
      res
        .status(409)
        .json({ message: 'Já existe uma tecnologia com este nome.' })
      return
    }
    console.error(err)
    res.status(500).json({ message: 'Falha ao criar a tecnologia.' })
  }
}) as RequestHandler)

// UPDATE
router.patch('/:id', isAuth, isAdmin, (async (req, res) => {
  const params = validate(IdParamSchema, req.params, res)
  if (!params) return

  const body = validate(UpdateTechnologySchema, req.body, res)
  if (!body) return

  const { name, icon_url, type, color, description } = body

  try {
    const updatedTechnology = await prisma.technology.update({
      where: { id: params.id },
      data: {
        name,
        ...(name !== undefined && { slug: generateSlug(name) }),
        iconUrl: icon_url,
        type,
        color,
        description,
      },
    })
    res.json({
      message: 'Tecnologia atualizada com sucesso.',
      technology_url: `${BASE_URL}/api/technologies/${updatedTechnology.id}`,
    })
  } catch (err) {
    if (err instanceof Prisma.PrismaClientKnownRequestError) {
      if (err.code === 'P2002') {
        res
          .status(409)
          .json({ message: 'Já existe uma tecnologia com este nome.' })
        return
      }
      if (err.code === 'P2025') {
        res.status(404).json({ message: 'Tecnologia não encontrada.' })
        return
      }
    }
    console.error(err)
    res.status(500).json({ message: 'Falha ao atualizar a tecnologia.' })
  }
}) as RequestHandler<{ id: string }>)

// DELETE
router.delete('/:id', isAuth, isAdmin, (async (req, res) => {
  const params = validate(IdParamSchema, req.params, res)
  if (!params) return

  try {
    const techWithProjects = await prisma.technology.findUnique({
      where: { id: params.id },
      include: { _count: { select: { projects: true } } },
    })

    if (!techWithProjects) {
      res.status(404).json({ message: 'Tecnologia não encontrada.' })
      return
    }

    if (techWithProjects._count.projects > 0) {
      res.status(400).json({
        message: `Não é possível excluir a tecnologia. Ela está sendo utilizada em ${techWithProjects._count.projects} projeto(s).`,
      })
      return
    }

    await prisma.technology.delete({ where: { id: params.id } })
    res.json({ message: 'Tecnologia excluída com sucesso.' })
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Falha ao excluir a tecnologia.' })
  }
}) as RequestHandler<{ id: string }>)

export default router
