import express, { RequestHandler, Router } from 'express'
import db from '../db/db.js'
import isAuth from '../middlewares/isAuth.js'
import { TechnologyType } from '../utils/enums.js'
import technologyModel from '../models/technologyModel.js'

interface Technology {
  id: number
  name: string
  icon_url: string | null
  type: TechnologyType
}

interface CreateTechnologyDTO {
  name: string
  icon_url?: string
  type: TechnologyType
}

type UpdateTechnologyDTO = Partial<CreateTechnologyDTO>

const router: Router = express.Router()
const validTypes = Object.values(TechnologyType)

// SAVE
router.post('/', isAuth, ((req, res) => {
  try {
    const technology = req.body as CreateTechnologyDTO

    if (!technology.name) {
      res.status(400).json({ message: 'Technology name is required.' })
      return
    }

    if (!technology.type || !validTypes.includes(technology.type)) {
      res.status(400).json({
        message: `Invalid type. Valid types are: ${validTypes.join(', ')}`,
      })
      return
    }

    const id = technologyModel.save(technology)
    res.status(201).json({
      message: 'Technology created successfully.',
      technology_id: id,
    })
  } catch (err) {
    const error = err as Error & { code?: string }
    if (error.code === 'SQLITE_CONSTRAINT_UNIQUE')
      return res
        .status(409)
        .json({ message: 'Technology name already exists.' })

    console.error(error.message)
    return res.status(500).json({ message: 'Failed to create technology.' })
  }
}) as RequestHandler)

// FIND ALL
router.get('/', ((req, res) => {
  try {
    const technologies = technologyModel.all()
    res.json(technologies)
  } catch (err) {
    console.error((err as Error).message)
    res.status(500).json({ message: 'Failed to fetch technologies.' })
  }
}) as RequestHandler)

// UPDATE
router.put('/:id', isAuth, ((req, res) => {
  try {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ message: 'Invalid ID format.' })
      return
    }

    const { name, icon_url, type } = req.body as UpdateTechnologyDTO

    if (type && !validTypes.includes(type)) {
      res.status(400).json({
        message: `Invalid type. Valid types are: ${validTypes.join(', ')}`,
      })
      return
    }

    if (!technologyModel.exists(id)) {
      res.status(404).json({ message: 'Technology not found.' })
      return
    }

    technologyModel.update(id, { name, icon_url, type })

    res.json({ message: 'Technology updated successfully.' })
  } catch (err) {
    const error = err as Error & { code?: string }
    if (error.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      res.status(409).json({ message: 'Technology name already exists.' })
      return
    }
    console.error(error.message)
    res.status(500).json({ message: 'Failed to update technology.' })
  }
}) as RequestHandler<{ id: string }, {}, UpdateTechnologyDTO>)

// DELETE
router.delete('/:id', isAuth, ((req, res) => {
  try {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ message: 'Invalid ID format.' })
      return
    }

    if (!technologyModel.exists(id)) {
      res.status(404).json({ message: 'Technology not found.' })
      return
    }

    technologyModel.delete(id)

    res.json({ message: 'Technology deleted successfully.' })
  } catch (err) {
    console.error((err as Error).message)
    res.status(500).json({ message: 'Failed to delete technology.' })
  }
}) as RequestHandler<{ id: string }>)

export default router
