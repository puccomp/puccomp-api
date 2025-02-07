import express from 'express'
import db from '../db/db.js'
import isAuth from '../middlewares/isAuth.js'
import { TechnologyType } from '../utils/enums.js'
import technologyModel from '../models/technologyModel.js'

const validTypes = Object.values(TechnologyType)

const router = express.Router()

// SAVE
router.post('/', isAuth, (req, res) => {
  const { name, icon_url, type } = req.body

  if (!name)
    return res.status(400).json({ message: 'Technology name is required.' })

  if(!validTypes.includes(type))
    return res.status(400).json({ message: `Invalid type. Valid types: ${validTypes.join(', ')}` })

  try {
    const id = technologyModel.save(name, icon_url, type)
    return res.status(201).json({
      message: 'Technology created successfully.',
      technology_id: id,
    })
  } catch (err) {
    if (err.code === 'SQLITE_CONSTRAINT_UNIQUE')
      return res
        .status(409)
        .json({ message: 'Technology name already exists.' })

    console.error(err.message)
    return res.status(500).json({ message: 'Failed to create technology.' })
  }
})

// FIND ALL
router.get('/', (req, res) => {
  try {
    const technologies = technologyModel.all()
    res.json(technologies)
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to fetch technologies.' })
  }
})

// UPDATE
router.put('/:id', isAuth, (req, res) => {
  const { id } = req.params
  const { name, icon_url, type } = req.body

  if(type && !validTypes.includes(type))
    return res.status(400).json({ message: `Invalid type. Valid types: ${validTypes.join(', ')}` })

  try {
    if (!technologyModel.exists(id)) 
      return res.status(404).json({ message: 'Technology not found.' })

    technologyModel.update(name, icon_url, type, id)

    res.json({ message: 'Technology updated successfully.' })
  } catch (err) {
    if (err.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      return res
        .status(409)
        .json({ message: 'Technology name already exists.' })
    }
    console.error(err.message)
    res.status(500).json({ message: 'Failed to update technology.' })
  }
})

// DELETE
router.delete('/:id', isAuth, (req, res) => {
  const { id } = req.params

  try {
    if (!technologyModel.exists(id))
      return res.status(404).json({ message: 'Technology not found.' })

    technologyModel.delete(id)

    res.json({ message: 'Technology deleted successfully.' })
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to delete technology.' })
  }
})

export default router
