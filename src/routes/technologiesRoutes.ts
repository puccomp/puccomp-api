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
      message: 'Technology created successfully.',
      technology_url: `${BASE_URL}/api/technologies/${newTechnology.id}`,
    })
  } catch (err) {
    if (
      err instanceof Prisma.PrismaClientKnownRequestError &&
      err.code === 'P2002'
    ) {
      res.status(409).json({ message: 'Technology name already exists.' })
      return
    }
    console.error(err)
    res.status(500).json({ message: 'Failed to create technology.' })
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
    res.status(500).json({ message: 'Failed to fetch technologies.' })
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
      message: 'Technology updated successfully.',
      technology_url: `${BASE_URL}/api/technologies/${updatedTechnology.id}`,
    })
  } catch (err) {
    if (err instanceof Prisma.PrismaClientKnownRequestError) {
      if (err.code === 'P2002') {
        res.status(409).json({ message: 'Technology name already exists.' })
        return
      }
      if (err.code === 'P2025') {
        res.status(404).json({ message: 'Technology not found.' })
        return
      }
    }
    console.error(err)
    res.status(500).json({ message: 'Failed to update technology.' })
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
      res.status(404).json({ message: 'Technology not found.' })
      return
    }

    if (techWithProjects._count.projects > 0) {
      res.status(400).json({
        message: `Cannot delete technology. It is being used by ${techWithProjects._count.projects} project(s).`,
      })
      return
    }

    await prisma.technology.delete({ where: { id: params.id } })
    res.json({ message: 'Technology deleted successfully.' })
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Failed to delete technology.' })
  }
}) as RequestHandler<{ id: string }>)

export default router
