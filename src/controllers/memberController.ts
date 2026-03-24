import { RequestHandler } from 'express'
import bcrypt from 'bcryptjs'
import { Member, Prisma, Role } from '@prisma/client'
import { BASE_URL } from '../index.js'
import { formatDate, keysToSnakeCase } from '../utils/formats.js'
import prisma from '../utils/prisma.js'
import { validate, IdParamSchema } from '../utils/validate.js'
import {
  CreateMemberSchema,
  UpdateMemberSchema,
  MemberQuerySchema,
} from '../schemas/memberSchemas.js'

const memberController = {
  insert: (async (req, res) => {
    const body = validate(CreateMemberSchema, req.body, res)
    if (!body) return
    const { email, name, surname, course, role_id, entry_date, ...rest } = body

    try {
      const newMember = await prisma.member.create({
        data: {
          email,
          name,
          surname,
          course,
          entryDate: new Date(entry_date),
          bio: rest.bio,
          avatarUrl: rest.avatar_url,
          exitDate: rest.exit_date ? new Date(rest.exit_date) : null,
          status: rest.status ?? 'PENDING',
          githubUrl: rest.github_url,
          instagramUrl: rest.instagram_url,
          linkedinUrl: rest.linkedin_url,
          isAdmin: rest.is_admin ?? false,
          role: { connect: { id: role_id } },
        },
      })

      res.status(201).json({
        message: 'Membro criado com sucesso.',
        member_url: `${BASE_URL}/api/members/${newMember.id}`,
      })
    } catch (err) {
      if (err instanceof Prisma.PrismaClientKnownRequestError) {
        if (err.code === 'P2002') {
          res.status(409).json({ message: `O e-mail '${email}' já está cadastrado.` })
          return
        }
        if (err.code === 'P2025') {
          res
            .status(400)
            .json({ message: `Cargo com id '${role_id}' não encontrado.` })
          return
        }
      }
      console.error(err)
      res.status(500).json({ message: 'Falha ao criar o membro.' })
    }
  }) as RequestHandler,

  get: (async (req, res) => {
    const params = validate(IdParamSchema, req.params, res)
    if (!params) return

    try {
      const member = await prisma.member.findUnique({
        where: { id: params.id },
        include: { role: true },
      })

      if (!member) {
        res.status(404).json({ message: 'Membro não encontrado.' })
        return
      }

      res.json(sanitizeMemberForResponse(member))
    } catch (error) {
      console.error(error)
      res.status(500).json({ message: 'Falha ao buscar o membro.' })
    }
  }) as RequestHandler<{ id: string }>,

  all: (async (req, res) => {
    const query = validate(MemberQuerySchema, req.query, res)
    if (!query) return

    const { status, role_id, is_admin, search, course, exclude_project, page, limit, sort_by, order } =
      query

    const where: Prisma.MemberWhereInput = {}
    if (status) where.status = status
    if (role_id !== undefined) where.roleId = role_id
    if (is_admin !== undefined) where.isAdmin = is_admin
    if (course) where.course = { contains: course, mode: 'insensitive' }
    if (exclude_project !== undefined) {
      where.NOT = { projects: { some: { project: { slug: exclude_project } } } }
    }
    if (search) {
      where.OR = [
        { name: { contains: search, mode: 'insensitive' } },
        { surname: { contains: search, mode: 'insensitive' } },
        { email: { contains: search, mode: 'insensitive' } },
      ]
    }

    const orderBy: Prisma.MemberOrderByWithRelationInput =
      sort_by === 'entry_date'
        ? { entryDate: order }
        : sort_by === 'exit_date'
          ? { exitDate: order }
          : { name: order }

    try {
      const [members, total] = await Promise.all([
        prisma.member.findMany({
          where,
          include: { role: true },
          orderBy,
          skip: (page - 1) * limit,
          take: limit,
        }),
        prisma.member.count({ where }),
      ])

      res.json({
        data: members.map(sanitizeMemberForResponse),
        pagination: {
          total,
          page,
          limit,
          total_pages: Math.ceil(total / limit),
        },
      })
    } catch (error) {
      console.error(error)
      res.status(500).json({ message: 'Falha ao buscar os membros.' })
    }
  }) as RequestHandler,

  update: (async (req, res) => {
    const params = validate(IdParamSchema, req.params, res)
    if (!params) return

    const body = validate(UpdateMemberSchema, req.body, res)
    if (!body) return

    const currentMember = await prisma.member.findUnique({
      where: { id: params.id },
    })

    if (!currentMember) {
      res.status(404).json({ message: 'Membro não encontrado.' })
      return
    }

    if (currentMember.status === 'PENDING') {
      res.status(422).json({
        message:
          'Não é possível editar um membro com convite pendente. Delete e convide novamente se necessário.',
      })
      return
    }

    if (body.status === 'PENDING') {
      res.status(422).json({
        message: 'Não é possível definir o status de um membro como PENDING manualmente.',
      })
      return
    }

    const effectiveStatus = body.status ?? currentMember.status
    const effectiveExitDate =
      body.exit_date !== undefined
        ? body.exit_date
        : body.status === 'ACTIVE'
          ? null
          : currentMember.exitDate

    if (effectiveStatus === 'INACTIVE' && !effectiveExitDate) {
      res
        .status(422)
        .json({ message: 'Membros inativos devem ter uma data de saída.' })
      return
    }
    if (effectiveStatus !== 'INACTIVE' && effectiveExitDate) {
      res.status(422).json({
        message: 'Membros ativos ou pendentes não podem ter data de saída.',
      })
      return
    }

    const { password, entry_date, exit_date, role_id, status, ...rest } = body

    try {
      const dataToUpdate: Prisma.MemberUpdateInput = {
        name: rest.name,
        surname: rest.surname,
        course: rest.course,
        bio: rest.bio,
        avatarUrl: rest.avatar_url,
        status,
        githubUrl: rest.github_url,
        instagramUrl: rest.instagram_url,
        linkedinUrl: rest.linkedin_url,
        isAdmin: rest.is_admin,
      }

      if (password) dataToUpdate.password = await bcrypt.hash(password, 10)
      if (entry_date) dataToUpdate.entryDate = new Date(entry_date)
      if (exit_date !== undefined)
        dataToUpdate.exitDate = exit_date ? new Date(exit_date) : null
      else if (status === 'ACTIVE')
        dataToUpdate.exitDate = null
      if (role_id) dataToUpdate.role = { connect: { id: role_id } }

      const updatedMember = await prisma.member.update({
        where: { id: params.id },
        data: dataToUpdate,
        include: { role: true },
      })

      res.json({
        message: 'Membro atualizado com sucesso.',
        member: sanitizeMemberForResponse(updatedMember),
      })
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2025'
      ) {
        res.status(404).json({ message: 'Membro não encontrado.' })
        return
      }
      console.error(error)
      res.status(500).json({ message: 'Falha ao atualizar o membro.' })
    }
  }) as RequestHandler<{ id: string }>,

  delete: (async (req, res) => {
    const params = validate(IdParamSchema, req.params, res)
    if (!params) return

    try {
      const member = await prisma.member.findUnique({
        where: { id: params.id },
      })

      if (!member) {
        res.status(404).json({ message: 'Membro não encontrado.' })
        return
      }

      if (member.isAdmin) {
        const adminCount = await prisma.member.count({
          where: { isAdmin: true },
        })
        if (adminCount === 1) {
          res
            .status(403)
            .json({ message: 'Não é possível excluir o último administrador.' })
          return
        }
      }

      await prisma.$transaction(async (tx) => {
        await tx.contributor.deleteMany({ where: { memberId: params.id } })
        await tx.member.delete({ where: { id: params.id } })
      })

      res.json({ message: 'Membro excluído com sucesso.' })
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2025'
      ) {
        res.status(404).json({ message: 'Membro não encontrado.' })
        return
      }
      console.error(error)
      res.status(500).json({ message: 'Falha ao excluir o membro.' })
    }
  }) as RequestHandler<{ id: string }>,
}

export const sanitizeMemberForResponse = (
  member: Member & { role?: Role | null }
) => {
  const {
    password: _password,
    inviteToken: _inviteToken,
    inviteTokenExpiresAt: _inviteTokenExpiresAt,
    role,
    entryDate,
    exitDate,
    ...rest
  } = member
  const intermediate = {
    ...rest,
    entryDate: entryDate ? formatDate(entryDate) : null,
    exitDate: exitDate ? formatDate(exitDate) : null,
    role: role?.name ?? null,
  }
  return keysToSnakeCase(intermediate)
}

export default memberController
