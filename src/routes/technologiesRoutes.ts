import express, { RequestHandler, Router } from 'express'
import isAuth from '../middlewares/isAuth.js'
import { BASE_URL, } from '../index.js'
import { Prisma, Technology, TechnologyType } from '@prisma/client'
import prisma from '../utils/prisma.js'

interface CreateTechnologyDTO {
  name: string
  icon_url?: string
  type: TechnologyType
}

type UpdateTechnologyDTO = Partial<CreateTechnologyDTO>

const router: Router = express.Router()

const validTypes = Object.values(TechnologyType)

// SAVE
router.post('/', isAuth, (async (req, res) => {
  try {
    const { name, icon_url, type } = req.body as CreateTechnologyDTO

    if (!name) {
      res.status(400).json({ message: 'Technology name is required.' })
      return
    }

    if (!type) {
      res.status(400).json({ message: 'Parameter "type" is required.' })
      return
    }

    const normalizedType = type.toUpperCase().trim() as TechnologyType

    if (!validTypes.includes(normalizedType)) {
      res.status(400).json({
        message: `Invalid type. Valid types are: ${validTypes.join(', ')}`,
      })
      return
    }

    const newTechnology = await prisma.technology.create({
      data: {
        name,
        iconUrl: icon_url,
        type: normalizedType,
      },
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
router.put('/:id', isAuth, (async (req, res) => {
  try {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ message: 'Invalid ID format.' })
      return
    }

    const { name, icon_url, type } = req.body as UpdateTechnologyDTO

    let normalizedType: TechnologyType | undefined = undefined

    if (type) {
      normalizedType = type.toUpperCase() as TechnologyType

      if (!validTypes.includes(normalizedType)) {
        res.status(400).json({
          message: `Invalid type '${type}'. Valid types are: ${validTypes.join(', ')}`,
        })
        return
      }
    }

    const updatedTechnology = await prisma.technology.update({
      where: { id },
      data: {
        name,
        iconUrl: icon_url,
        type: normalizedType,
      },
    })

    res.json({
      message: 'Technology updated successfully.',
      technology_url: `${BASE_URL}/api/technologies/${updatedTechnology.id}`,
    })
    return
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
    return
  }
}) as RequestHandler<{ id: string }, {}, UpdateTechnologyDTO>)

// DELETE
router.delete('/:id', isAuth, (async (req, res) => {
  try {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ message: 'Invalid ID format.' })
      return
    }

    const techWithProjects = await prisma.technology.findUnique({
      where: { id },
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

    await prisma.technology.delete({ where: { id } })
    res.json({ message: 'Technology deleted successfully.' })
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Failed to delete technology.' })
  }
}) as RequestHandler<{ id: string }>)

export default router
