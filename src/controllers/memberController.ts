import { RequestHandler } from 'express'
import jwt from 'jsonwebtoken'
import bcrypt from 'bcryptjs'
import { TokenPayload } from '../middlewares/isAuth.js'
import { Member, Prisma, Role } from '@prisma/client'
import { BASE_URL, } from '../index.js'
import { formatDate, keysToSnakeCase } from '../utils/formats.js'
import prisma from '../utils/prisma.js'

interface LoginDTO {
  email: string
  password: string
}

interface CreateMemberDTO {
  email: string
  password: string
  name: string
  surname: string
  bio?: string
  course: string
  avatar_url?: string
  entry_date: string // YYYY-MM-DD
  exit_date?: string // YYYY-MM-DD
  is_active?: boolean
  github_url?: string
  instagram_url?: string
  linkedin_url?: string
  is_admin?: boolean
  role_id: number
}

type UpdateMemberDTO = Partial<CreateMemberDTO>

const memberController = {
  login: (async (req, res) => {
    const { email, password } = req.body as LoginDTO
    if (!email || !password) {
      res.status(400).json({ message: 'Email and password are required.' })
      return
    }

    try {
      const member = await prisma.member.findUnique({
        where: { email },
      })

      if (!member) {
        res.status(404).json({ message: 'Member not found' })
        return
      }

      const isPasswordValid = await bcrypt.compare(password, member.password)
      if (!isPasswordValid) {
        res.status(401).json({ message: 'Invalid credentials' })
        return
      }

      const tokenPayload: TokenPayload = {
        id: member.id,
        is_active: member.isActive,
        is_admin: member.isAdmin,
      }
      const token: string = jwt.sign(
        tokenPayload,
        process.env.JWT_SECRET_KEY!,
        {
          expiresIn: '1h',
        }
      )

      res.json({ token })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Internal server error during login' })
    }
  }) as RequestHandler,

  insert: (async (req, res) => {
    const {
      email,
      password,
      name,
      surname,
      course,
      role_id,
      entry_date,
      ...rest
    } = req.body as CreateMemberDTO

    if (
      !email ||
      !password ||
      !name ||
      !surname ||
      !course ||
      !role_id ||
      !entry_date
    ) {
      res.status(400).json({ message: 'Missing required fields.' })
      return
    }

    if (!isSingleWord(name) || !isSingleWord(surname)) {
      res
        .status(400)
        .json({ message: 'Name and surname must be single words.' })
      return
    }

    try {
      const hashedPassword = await bcrypt.hash(password, 10)

      const newMember = await prisma.member.create({
        data: {
          email,
          password: hashedPassword,
          name,
          surname,
          course,
          entryDate: new Date(entry_date),
          bio: rest.bio,
          avatarUrl: rest.avatar_url,
          exitDate: rest.exit_date ? new Date(rest.exit_date) : null,
          isActive: rest.is_active ?? true,
          githubUrl: rest.github_url,
          instagramUrl: rest.instagram_url,
          linkedinUrl: rest.linkedin_url,
          isAdmin: rest.is_admin ?? false,
          role: {
            connect: { id: role_id },
          },
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
      res.status(500).json({ error: 'Failed to create member.' })
    }
  }) as RequestHandler<{}, {}, CreateMemberDTO>,

  get: (async (req, res) => {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ error: 'Invalid id format.' })
      return
    }

    try {
      const member = await prisma.member.findUnique({
        where: { id },
        include: { role: true },
      })

      if (!member) {
        res.status(404).json({ error: 'Member not found.' })
        return
      }

      res.json(sanitizeMemberForResponse(member))
    } catch (error) {
      console.error(error)
      res.status(500).json({ error: 'Failed to retrieve member.' })
    }
  }) as RequestHandler<{ id: string }>,

  all: (async (_req, res) => {
    try {
      const members = await prisma.member.findMany({
        include: {
          role: true,
        },
      })
      res.json(members.map(sanitizeMemberForResponse))
    } catch (error) {
      console.error(error)
      res.status(500).json({ error: 'Failed to retrieve members.' })
    }
  }) as RequestHandler,

  update: (async (req, res) => {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ error: 'Invalid id format.' })
      return
    }
    const { password, entry_date, exit_date, role_id, ...rest } =
      req.body as UpdateMemberDTO

    if (
      Object.keys(rest).length === 0 &&
      !password &&
      !entry_date &&
      !exit_date &&
      !role_id
    ) {
      res.status(400).json({ error: 'No fields to update.' })
      return
    }

    if (rest.name && !isSingleWord(rest.name)) {
      res.status(400).json({ message: 'Name must be a single word.' })
      return
    }
    if (rest.surname && !isSingleWord(rest.surname)) {
      res.status(400).json({ message: 'Surname must be a single word.' })
      return
    }

    try {
      const dataToUpdate: Prisma.MemberUpdateInput = {
        name: rest.name,
        surname: rest.surname,
        course: rest.course,
        bio: rest.bio,
        avatarUrl: rest.avatar_url,
        isActive: rest.is_active,
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
        where: { id },
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
      res.status(500).json({ error: 'Failed to update member.' })
    }
  }) as RequestHandler<{ id: string }, {}, Partial<CreateMemberDTO>>,

  delete: (async (req, res) => {
    const id = parseInt(req.params.id, 10)
    if (isNaN(id)) {
      res.status(400).json({ error: 'Invalid id format.' })
      return
    }

    try {
      await prisma.$transaction(async (tx) => {
        await tx.contributor.deleteMany({
          where: { memberId: id },
        })
        await tx.member.delete({
          where: { id },
        })
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
      res.status(500).json({ error: 'Failed to delete member.' })
    }
  }) as RequestHandler<{ id: string }>,
}

const isSingleWord = (word: string): boolean => {
  const singleWordRegex = /^[^\s]+$/
  return singleWordRegex.test(word)
}

const sanitizeMemberForResponse = (member: Member & { role?: Role | null }) => {
  const { password, role, entryDate, exitDate, ...camelCaseMember } = member
  const intermediateObject = {
    ...camelCaseMember,
    entryDate: formatDate(entryDate),
    exitDate: exitDate ? formatDate(exitDate) : null,
    role: role?.name,
  }
  return keysToSnakeCase(intermediateObject)
}

export default memberController
