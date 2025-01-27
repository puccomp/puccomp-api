import express from 'express'
import db from '../db.js'
import authMiddleware from '../middlewares/authMiddleware.js'

const router = express.Router()

// FIND ALL (with optional filters)
router.get('/', (req, res) => {
  const { project_id, member_id } = req.query

  try {
    let query = `
      SELECT * 
      FROM contributor
    `
    const params = []

    if (project_id) {
      query += ' WHERE project_id = ?'
      params.push(project_id)
    }

    if (member_id) {
      query += project_id ? ' AND member_id = ?' : ' WHERE member_id = ?'
      params.push(member_id)
    }

    const contributorsQuery = db.prepare(query)
    const contributors = contributorsQuery.all(...params)

    if (contributors.length === 0)
      return res.status(404).json({ message: 'No contributors found.' })

    res.json(contributors)
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to fetch contributors.' })
  }
})

// POST
router.post('/', authMiddleware, (req, res) => {
  const { member_id, project_id } = req.body

  if (!member_id || !project_id)
    return res
      .status(400)
      .json({ message: 'Member ID and Project ID are required.' })

  try {
    const associateQuery = db.prepare(`
      INSERT INTO contributor (member_id, project_id)
      VALUES (?, ?)
    `)

    associateQuery.run(member_id, project_id)

    res.status(201).json({ message: 'Contributor added successfully.' })
  } catch (err) {
    if (err.code === 'SQLITE_CONSTRAINT')
      return res.status(409).json({ message: 'Contributor already exists.' })

    console.error(err.message)
    res.status(500).json({ message: 'Failed to add contributor.' })
  }
})

// DELETE
router.delete('/', authMiddleware, (req, res) => {
  const { member_id, project_id } = req.body

  if (!member_id || !project_id)
    return res
      .status(400)
      .json({ message: 'Member ID and Project ID are required.' })

  try {
    const deleteQuery = db.prepare(`
      DELETE FROM contributor
      WHERE member_id = ? AND project_id = ?
    `)

    const result = deleteQuery.run(member_id, project_id)

    if (result.changes === 0)
      return res.status(404).json({ message: 'Contributor not found.' })

    res.json({ message: 'Contributor removed successfully.' })
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to remove contributor.' })
  }
})

export default router
