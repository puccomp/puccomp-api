import express from 'express'
import db from '../db.js'
import authMiddleware from '../middlewares/authMiddleware.js'

const router = express.Router()

// SAVE
router.post('/', authMiddleware, (req, res) => {
  const { name, icon_url, type } = req.body

  if (!name)
    return res.status(400).json({ message: 'Technology name is required.' })

  const validTypes = ['language', 'framework', 'library', 'tool', 'other']
  if (!validTypes.includes(type))
    return res
      .status(400)
      .json({ message: `Invalid type. Valid types: ${validTypes.join(', ')}` })

  try {
    const insertTechnologyQuery = db.prepare(`
      INSERT INTO technology (name, icon_url, type)
      VALUES (?, ?, ?)
    `)
    const result = insertTechnologyQuery.run(name, icon_url || null, type)

    return res.status(201).json({
      message: 'Technology created successfully.',
      technology_id: result.lastInsertRowid,
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
    const getTechnologiesQuery = db.prepare('SELECT * FROM technology')
    const technologies = getTechnologiesQuery.all()
    res.json(technologies)
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to fetch technologies.' })
  }
})

// FIND BY ID
router.get('/:id', (req, res) => {
  const { id } = req.params

  try {
    const getTechnologyQuery = db.prepare(
      'SELECT * FROM technology WHERE id = ?'
    )
    const technology = getTechnologyQuery.get(id)

    if (!technology) {
      return res.status(404).json({ message: 'Technology not found.' })
    }

    res.json(technology)
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to fetch technology.' })
  }
})

// UPDATE
router.put('/:id', authMiddleware, (req, res) => {
  const { id } = req.params
  const { name, icon_url, type } = req.body

  const validTypes = ['language', 'framework', 'library', 'tool', 'other']
  if (type && !validTypes.includes(type))
    return res
      .status(400)
      .json({ message: `Invalid type. Valid types: ${validTypes.join(', ')}` })

  try {
    const checkTechnologyQuery = db.prepare(
      'SELECT * FROM technology WHERE id = ?'
    )
    const technology = checkTechnologyQuery.get(id)

    if (!technology) {
      return res.status(404).json({ message: 'Technology not found.' })
    }

    const updateTechnologyQuery = db.prepare(`
      UPDATE technology
      SET 
        name = COALESCE(?, name),
        icon_url = COALESCE(?, icon_url),
        type = COALESCE(?, type)
      WHERE id = ?
    `)
    updateTechnologyQuery.run(name, icon_url, type, id)

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
router.delete('/:id', authMiddleware, (req, res) => {
  const { id } = req.params

  try {
    const checkTechnologyQuery = db.prepare(
      'SELECT id FROM technology WHERE id = ?'
    )
    const technology = checkTechnologyQuery.get(id)

    if (!technology)
      return res.status(404).json({ message: 'Technology not found.' })

    const deleteTechnologyQuery = db.prepare(
      'DELETE FROM technology WHERE id = ?'
    )
    deleteTechnologyQuery.run(id)

    res.json({ message: 'Technology deleted successfully.' })
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to delete technology.' })
  }
})

export default router
