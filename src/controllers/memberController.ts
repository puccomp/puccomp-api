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
        message: 'Member created successfully.',
        member_url: `${BASE_URL}/api/members/${newMember.id}`,
      })
    } catch (err) {
      if (err instanceof Prisma.PrismaClientKnownRequestError) {
        if (err.code === 'P2002') {
          res.status(409).json({ message: `Email '${email}' already exists.` })
          return
        }
        if (err.code === 'P2025') {
          res
            .status(400)
            .json({ message: `Role with id '${role_id}' not found.` })
          return
        }
      }
      console.error(err)
      res.status(500).json({ message: 'Failed to create member.' })
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
        res.status(404).json({ message: 'Member not found.' })
        return
      }

      res.json(sanitizeMemberForResponse(member))
    } catch (error) {
      console.error(error)
      res.status(500).json({ message: 'Failed to retrieve member.' })
    }
  }) as RequestHandler<{ id: string }>,

  all: (async (_req, res) => {
    try {
      const members = await prisma.member.findMany({ include: { role: true } })
      res.json(members.map(sanitizeMemberForResponse))
    } catch (error) {
      console.error(error)
      res.status(500).json({ message: 'Failed to retrieve members.' })
    }
  }) as RequestHandler,

  update: (async (req, res) => {
    const params = validate(IdParamSchema, req.params, res)
    if (!params) return

    const body = validate(UpdateMemberSchema, req.body, res)
    if (!body) return

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
      if (exit_date) dataToUpdate.exitDate = new Date(exit_date)
      if (role_id) dataToUpdate.role = { connect: { id: role_id } }

      const updatedMember = await prisma.member.update({
        where: { id: params.id },
        data: dataToUpdate,
        include: { role: true },
      })

      res.json({
        message: 'Member updated successfully.',
        member: sanitizeMemberForResponse(updatedMember),
      })
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2025'
      ) {
        res.status(404).json({ message: 'Member not found.' })
        return
      }
      console.error(error)
      res.status(500).json({ message: 'Failed to update member.' })
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
        res.status(404).json({ message: 'Member not found.' })
        return
      }

      if (member.isAdmin) {
        const adminCount = await prisma.member.count({
          where: { isAdmin: true },
        })
        if (adminCount === 1) {
          res
            .status(403)
            .json({ message: 'Cannot delete the last admin member.' })
          return
        }
      }

      await prisma.$transaction(async (tx) => {
        await tx.contributor.deleteMany({ where: { memberId: params.id } })
        await tx.member.delete({ where: { id: params.id } })
      })

      res.json({ message: 'Member deleted successfully.' })
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2025'
      ) {
        res.status(404).json({ message: 'Member not found.' })
        return
      }
      console.error(error)
      res.status(500).json({ message: 'Failed to delete member.' })
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
    entryDate: formatDate(entryDate),
    exitDate: exitDate ? formatDate(exitDate) : null,
    role: role?.name,
  }
  return keysToSnakeCase(intermediate)
}

export default memberController
