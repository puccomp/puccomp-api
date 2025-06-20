import express, { RequestHandler, Router } from 'express'
import db from '../db/db.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'

interface Role {
  id: number
  name: string
  description: string | null
  level: number
  created_at: string
  updated_at: string
}

interface CreateRoleDTO {
  name: string
  description?: string
  level: number
}

type UpdateRoleDTO = Partial<CreateRoleDTO>

const router: Router = express.Router()

// SAVE ROLE
router.post('/', isAuth, isAdmin, ((req, res) => {
  const { name, description, level } = req.body as CreateRoleDTO

  if (!name || level === undefined) {
    res.status(400).json({ message: 'Role name and level are required.' })
    return
  }
  if (level < 0) {
    res.status(400).json({ message: 'Role level must be 0 or greater.' })
    return
  }

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
    const error = err as Error & { code?: string }
    if (error.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      res.status(409).json({ message: 'Role name already exists.' })
      return
    }

    console.error(error.message)
    res.status(500).json({ message: 'Failed to create role.' })
  }
}) as RequestHandler)

// FIND ALL ROLES
router.get('/', ((req, res) => {
  try {
    const getRolesQuery = db.prepare('SELECT * FROM role')
    const roles = getRolesQuery.all()

    res.json(roles)
  } catch (err) {
    console.error((err as Error).message)
    res.status(500).json({ message: 'Failed to fetch roles.' })
  }
}) as RequestHandler)

// FIND ROLE BY ID
router.get('/:id', ((req, res) => {
  try {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ message: 'Invalid ID format.' })
      return
    }

    const getRoleQuery = db.prepare('SELECT * FROM role WHERE id = ?')
    const role = getRoleQuery.get(id) as Role | undefined

    if (!role) {
      res.status(404).json({ message: 'Role not found.' })
      return
    }
    res.json(role)
  } catch (err) {
    console.error((err as Error).message)
    res.status(500).json({ message: 'Failed to fetch role.' })
  }
}) as RequestHandler<{ id: string }>)

// UPDATE
router.put('/:id', isAuth, isAdmin, ((req, res) => {
  try {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ message: 'Invalid ID format.' })
      return
    }

    const { name, description, level } = req.body as UpdateRoleDTO

    const role = db.prepare('SELECT id FROM role WHERE id = ?').get(id) as
      | { id: number }
      | undefined
    if (!role) {
      res.status(404).json({ message: 'Role not found.' })
      return
    }

    if (level !== undefined && level < 0) {
      res.status(400).json({ message: 'Role level must be 0 or greater.' })
      return
    }

    const fieldsToUpdate: string[] = []
    const params: (string | number)[] = []

    if (name !== undefined) {
      fieldsToUpdate.push('name = ?')
      params.push(name)
    }
    if (description !== undefined) {
      fieldsToUpdate.push('description = ?')
      params.push(description)
    }
    if (level !== undefined) {
      fieldsToUpdate.push('level = ?')
      params.push(level)
    }

    if (fieldsToUpdate.length === 0) {
      res.status(400).json({ message: 'No fields to update were provided.' })
      return
    }

    fieldsToUpdate.push('updated_at = CURRENT_TIMESTAMP')
    params.push(id)

    const updateQuery = db.prepare(
      `UPDATE role SET ${fieldsToUpdate.join(', ')} WHERE id = ?`
    )
    const info = updateQuery.run(params)

    if (info.changes === 0) {
      res.status(304).json({ message: 'No changes were made to the role.' })
      return
    }

    res.json({ message: 'Role updated successfully.' })
  } catch (err) {
    const error = err as Error & { code?: string }
    if (error.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      res.status(409).json({ message: 'Role name already exists.' })
      return
    }
    console.error(error.message)
    res.status(500).json({ message: 'Failed to update role.' })
  }
}) as RequestHandler<{ id: string }, {}, UpdateRoleDTO>)

// DELETE
router.delete('/:id', isAuth, isAdmin, ((req, res) => {
  try {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ message: 'Invalid ID format.' })
      return
    }

    const role = db.prepare('SELECT id FROM role WHERE id = ?').get(id) as
      | { id: number }
      | undefined
    if (!role) {
      res.status(404).json({ message: 'Role not found.' })
      return
    }

    const { count } = db
      .prepare('SELECT COUNT(*) AS count FROM member WHERE role_id = ?')
      .get(id) as { count: number }
    if (count > 0) {
      res.status(400).json({
        message: 'Cannot delete role. It is associated with existing members.',
      })
      return
    }

    const deleteRoleQuery = db.prepare('DELETE FROM role WHERE id = ?')
    deleteRoleQuery.run(id)

    res.json({ message: 'Role deleted successfully.' })
  } catch (err) {
    console.error((err as Error).message)
    res.status(500).json({ message: 'Failed to delete role.' })
  }
}) as RequestHandler<{ id: string }>)

export default router
