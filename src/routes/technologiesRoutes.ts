import express, { RequestHandler, Router } from 'express'
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'
import { BASE_URL } from '../index.js'
import { Prisma, Technology } from '@prisma/client'
import prisma from '../utils/prisma.js'
import { validate, IdParamSchema } from '../utils/validate.js'
import {
  CreateTechnologySchema,
  UpdateTechnologySchema,
} from '../schemas/technologySchemas.js'

const router: Router = express.Router()

// SAVE
router.post('/', isAuth, isAdmin, (async (req, res) => {
  const body = validate(CreateTechnologySchema, req.body, res)
  if (!body) return
  const { name, icon_url, type } = body

  try {
    const newTechnology = await prisma.technology.create({
      data: { name, iconUrl: icon_url, type },
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
      res.status(409).json({ message: 'Já existe uma tecnologia com este nome.' })
      return
    }
    console.error(err)
    res.status(500).json({ message: 'Falha ao criar a tecnologia.' })
  }
}) as RequestHandler)

// FIND ALL
router.get('/', (async (_req, res) => {
  try {
    const technologies: Technology[] = await prisma.technology.findMany({
      orderBy: { name: 'asc' },
    })
    res.json(technologies)
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Falha ao buscar as tecnologias.' })
  }
}) as RequestHandler)

// UPDATE
router.patch('/:id', isAuth, isAdmin, (async (req, res) => {
  const params = validate(IdParamSchema, req.params, res)
  if (!params) return

  const body = validate(UpdateTechnologySchema, req.body, res)
  if (!body) return
  const { name, icon_url, type } = body

  try {
    const updatedTechnology = await prisma.technology.update({
      where: { id: params.id },
      data: { name, iconUrl: icon_url, type },
    })

    res.json({
      message: 'Tecnologia atualizada com sucesso.',
      technology_url: `${BASE_URL}/api/technologies/${updatedTechnology.id}`,
    })
  } catch (err) {
    if (err instanceof Prisma.PrismaClientKnownRequestError) {
      if (err.code === 'P2002') {
        res.status(409).json({ message: 'Já existe uma tecnologia com este nome.' })
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
