import express from 'express'
import db from '../db.js'

// MIDDLEWARES
import authMiddleware from '../middlewares/authMiddleware.js'
import adminMiddleware from '../middlewares/adminMiddleware.js'

const router = express.Router()

// SAVE ROLE
router.post('/', authMiddleware, adminMiddleware, (req, res) => {
  const { name, description, level } = req.body

  if (!name || level === undefined)
    return res
      .status(400)
      .json({ message: 'Role name and level are required.' })

  try {
    const insertRoleQuery = db.prepare(`
      INSERT INTO role (name, description, level, created_at, updated_at)
      VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE)
    `)
    const result = insertRoleQuery.run(name, description || null, level)

    return res.status(201).json({
      message: 'Role created successfully.',
      role_id: result.lastInsertRowid,
    })
  } catch (err) {
    if (err.code === 'SQLITE_CONSTRAINT_UNIQUE')
      return res.status(409).json({ message: 'Role name already exists.' })

    console.error(err.message)
    res.status(500).json({ message: 'Failed to create role.' })
  }
})

// FIND ALL ROLES
router.get('/', (req, res) => {
  try {
    const getRolesQuery = db.prepare('SELECT * FROM role')
    const roles = getRolesQuery.all()

    return res.json(roles)
  } catch (err) {
    console.error(err.message)
    return res.status(500).json({ message: 'Failed to fetch roles.' })
  }
})

// FIND ROLE BY ID
router.get('/:id', (req, res) => {
  const { id } = req.params

  try {
    const getRoleQuery = db.prepare('SELECT * FROM role WHERE id = ?')
    const role = getRoleQuery.get(id)

    if (!role) return res.status(404).json({ message: 'Role not found.' })

    return res.json(role)
  } catch (err) {
    console.error(err.message)
    return res.status(500).json({ message: 'Failed to fetch role.' })
  }
})

// UPDATE
router.put('/:id', authMiddleware, adminMiddleware, (req, res) => {
  const { id } = req.params
  const { name, description, level } = req.body

  if (level !== undefined && level < 0)
    return res.status(400).json({ message: 'Role level must be 0 or greater.' })

  try {
    const checkRoleQuery = db.prepare('SELECT * FROM role WHERE id = ?')
    const role = checkRoleQuery.get(id)

    if (!role) return res.status(404).json({ message: 'Role not found.' })

    const updateRoleQuery = db.prepare(`
      UPDATE role
      SET 
        name = COALESCE(?, name),
        description = COALESCE(?, description),
        level = COALESCE(?, level),
        updated_at = CURRENT_DATE
      WHERE id = ?
    `)
    updateRoleQuery.run(name, description, level, id)

    return res.json({ message: 'Role updated successfully.' })
  } catch (err) {
    if (err.code === 'SQLITE_CONSTRAINT_UNIQUE')
      return res.status(409).json({ message: 'Role name already exists.' })

    console.error(err.message)
    return res.status(500).json({ message: 'Failed to update role.' })
  }
})

// DELETE
router.delete('/:id', authMiddleware, adminMiddleware, (req, res) => {
  const { id } = req.params

  try {
    const checkRoleQuery = db.prepare('SELECT * FROM role WHERE id = ?')
    const role = checkRoleQuery.get(id)

    if (!role) return res.status(404).json({ message: 'Role not found.' })

    const checkMembersQuery = db.prepare(
      'SELECT COUNT(*) AS count FROM member WHERE role_id = ?'
    )
    const { count } = checkMembersQuery.get(id)

    if (count > 0)
      return res.status(400).json({
        message: 'Cannot delete role. It is associated with existing members.',
      })

    const deleteRoleQuery = db.prepare('DELETE FROM role WHERE id = ?')
    deleteRoleQuery.run(id)

    return res.json({ message: 'Role deleted successfully.' })
  } catch (err) {
    console.error(err.message)
    return res.status(500).json({ message: 'Failed to delete role.' })
  }
})

export default router
