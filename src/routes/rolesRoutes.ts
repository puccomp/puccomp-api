import express, { RequestHandler, Router } from 'express'
import { BASE_URL } from '../index.js'
import { Prisma, Role } from '@prisma/client'
import { formatDate } from '../utils/formats.js'
import prisma from '../utils/prisma.js'
import { validate, IdParamSchema } from '../utils/validate.js'
import { CreateRoleSchema, UpdateRoleSchema } from '../schemas/roleSchemas.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'

const router: Router = express.Router()

// SAVE ROLE
router.post('/', isAuth, isAdmin, (async (req, res) => {
  const body = validate(CreateRoleSchema, req.body, res)
  if (!body) return
  const { name, description, level } = body

  try {
    const newRole = await prisma.role.create({
      data: { name, description, level },
    })

    res.status(201).json({
      message: 'Cargo criado com sucesso.',
      role_url: `${BASE_URL}/api/roles/${newRole.id}`,
    })
  } catch (err) {
    if (
      err instanceof Prisma.PrismaClientKnownRequestError &&
      err.code === 'P2002'
    ) {
      res.status(409).json({ message: 'Já existe um cargo com este nome.' })
      return
    }
    console.error(err)
    res.status(500).json({ message: 'Falha ao criar o cargo.' })
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
    res.status(500).json({ message: 'Falha ao buscar os cargos.' })
  }
}) as RequestHandler)

// FIND ROLE BY ID
router.get('/:id', (async (req, res) => {
  const params = validate(IdParamSchema, req.params, res)
  if (!params) return

  try {
    const role = await prisma.role.findUnique({ where: { id: params.id } })

    if (!role) {
      res.status(404).json({ message: 'Cargo não encontrado.' })
      return
    }
    res.json({
      ...role,
      createdAt: formatDate(role.createdAt),
      updatedAt: formatDate(role.updatedAt),
    })
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Falha ao buscar o cargo.' })
  }
}) as RequestHandler<{ id: string }>)

// UPDATE
router.patch('/:id', isAuth, isAdmin, (async (req, res) => {
  const params = validate(IdParamSchema, req.params, res)
  if (!params) return

  const body = validate(UpdateRoleSchema, req.body, res)
  if (!body) return
  const { name, description, level } = body

  try {
    const updatedRole = await prisma.role.update({
      where: { id: params.id },
      data: { name, description, level },
    })

    res.json({
      message: 'Cargo atualizado com sucesso.',
      role_url: `${BASE_URL}/api/roles/${updatedRole.id}`,
    })
  } catch (err) {
    if (err instanceof Prisma.PrismaClientKnownRequestError) {
      if (err.code === 'P2002') {
        res.status(409).json({ message: 'Já existe um cargo com este nome.' })
        return
      }
      if (err.code === 'P2025') {
        res.status(404).json({ message: 'Cargo não encontrado.' })
        return
      }
    }
    console.error(err)
    res.status(500).json({ message: 'Falha ao atualizar o cargo.' })
  }
}) as RequestHandler<{ id: string }>)

// DELETE
router.delete('/:id', isAuth, isAdmin, (async (req, res) => {
  const params = validate(IdParamSchema, req.params, res)
  if (!params) return

  try {
    const roleWithMemberCount = await prisma.role.findUnique({
      where: { id: params.id },
      include: { _count: { select: { members: true } } },
    })

    if (!roleWithMemberCount) {
      res.status(404).json({ message: 'Cargo não encontrado.' })
      return
    }

    if (roleWithMemberCount._count.members > 0) {
      res.status(400).json({
        message: 'Não é possível excluir o cargo. Ele está associado a membros existentes.',
      })
      return
    }

    await prisma.role.delete({ where: { id: params.id } })
    res.json({ message: 'Cargo excluído com sucesso.' })
  } catch (err) {
    console.error(err)
    res.status(500).json({ message: 'Falha ao excluir o cargo.' })
  }
}) as RequestHandler<{ id: string }>)

export default router
