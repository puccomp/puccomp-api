import express, { RequestHandler, Router } from 'express'
import { BASE_URL, prisma } from '../index.js'
import { Prisma, Role } from '@prisma/client'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'
import { formatDate } from '../utils/formats.js'

interface CreateRoleDTO {
  name: string
  description?: string
  level: number
}

type UpdateRoleDTO = Partial<CreateRoleDTO>

const router: Router = express.Router()

// SAVE ROLE
router.post('/', isAuth, isAdmin, (async (req, res) => {
  try {
    const { name, description, level } = req.body as CreateRoleDTO

    if (!name || level === undefined) {
      res.status(400).json({ message: 'Role name and level are required.' })
      return
    }
    if (level < 0) {
      res.status(400).json({ message: 'Role level must be 0 or greater.' })
      return
    }

    const newRole = await prisma.role.create({
      data: { name, description, level },
    })

    res.status(201).json({
      message: 'Role created successfully.',
      role_url: `${BASE_URL}/api/roles/${newRole.id}`,
    })
  } catch (err) {
    if (
      err instanceof Prisma.PrismaClientKnownRequestError &&
      err.code === 'P2002'
    ) {
      res.status(409).json({ message: 'Role name already exists.' })
      return
    }
    console.error(err)
    res.status(500).json({ message: 'Failed to create role.' })
  }
}) as RequestHandler)

// FIND ALL ROLES
router.get('/', (async (_req, res) => {
  try {
    const roles: Role[] = await prisma.role.findMany()
    res.json(
      roles.map((role) => ({
        ...role,
        createdAt: formatDate(role.createdAt),
        updatedAt: formatDate(role.updatedAt),
      }))
    )
  } catch (err) {
    console.error((err as Error).message)
    res.status(500).json({ message: 'Failed to fetch roles.' })
  }
}) as RequestHandler)

// FIND ROLE BY ID
router.get('/:id', (async (req, res) => {
  try {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ message: 'Invalid ID format.' })
      return
    }

    const role = await prisma.role.findUnique({
      where: { id },
    })

    if (!role) {
      res.status(404).json({ message: 'Role not found.' })
      return
    }
    res.json({
      ...role,
      createdAt: formatDate(role.createdAt),
      updatedAt: formatDate(role.updatedAt),
    })
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Failed to fetch role.' })
  }
}) as RequestHandler<{ id: string }>)

// UPDATE
router.put('/:id', isAuth, isAdmin, (async (req, res) => {
  try {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ message: 'Invalid ID format.' })
      return
    }

    const { name, description, level } = req.body as UpdateRoleDTO

    if (level !== undefined && level < 0) {
      res.status(400).json({ message: 'Role level must be 0 or greater.' })
      return
    }

    const updatedRole = await prisma.role.update({
      where: { id },
      data: { name, description, level },
    })

    res.json({
      message: 'Role updated successfully.',
      role_url: `${BASE_URL}/api/roles/${updatedRole.id}`,
    })
  } catch (err) {
    if (err instanceof Prisma.PrismaClientKnownRequestError) {
      if (err.code === 'P2002') {
        res.status(409).json({ message: 'Role name already exists.' })
        return
      }
      if (err.code === 'P2025') {
        res.status(404).json({ message: 'Role not found.' })
        return
      }
    }
    console.error(err)
    res.status(500).json({ message: 'Failed to update role.' })
  }
}) as RequestHandler<{ id: string }, {}, UpdateRoleDTO>)

// DELETE
router.delete('/:id', isAuth, isAdmin, (async (req, res) => {
  try {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ message: 'Invalid ID format.' })
      return
    }

    const roleWithMemberCount = await prisma.role.findUnique({
      where: { id },
      include: {
        _count: {
          select: { members: true },
        },
      },
    })

    if (!roleWithMemberCount) {
      res.status(404).json({ message: 'Role not found.' })
      return
    }

    if (roleWithMemberCount._count.members > 0) {
      res.status(400).json({
        message: 'Cannot delete role. It is associated with existing members.',
      })
      return
    }

    await prisma.role.delete({
      where: { id },
    })

    res.json({ message: 'Role deleted successfully.' })
    return
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Failed to delete role.' })
    return
  }
}) as RequestHandler<{ id: string }>)

export default router
