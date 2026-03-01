import { RequestHandler } from 'express'
import jwt from 'jsonwebtoken'
import bcrypt from 'bcryptjs'
import { randomBytes } from 'crypto'
import { MemberStatus, Prisma } from '@prisma/client'
import { TokenPayload } from '../middlewares/isAuth.js'
import { BASE_URL } from '../index.js'
import prisma from '../utils/prisma.js'
import { sendEmail } from '../utils/email.js'

interface LoginDTO {
  email: string
  password: string
}

interface InviteDTO {
  email: string
  name: string
  surname: string
  course: string
  role_id: number
  entry_date: string
  bio?: string
  github_url?: string
  instagram_url?: string
  linkedin_url?: string
  is_admin?: boolean
}

interface AcceptInviteDTO {
  token: string
  password: string
}

const INVITE_EXPIRY_DAYS = 7

const authController = {
  login: (async (req, res) => {
    const { email, password } = req.body as LoginDTO
    if (!email || !password) {
      res.status(400).json({ message: 'Email and password are required.' })
      return
    }

    try {
      const member = await prisma.member.findUnique({ where: { email } })

      if (!member || !member.password) {
        res.status(401).json({ message: 'Invalid credentials' })
        return
      }

      if (member.status !== MemberStatus.ACTIVE) {
        res.status(403).json({
          message: 'Account is not active. Please accept your invite first.',
        })
        return
      }

      const isPasswordValid = await bcrypt.compare(password, member.password)
      if (!isPasswordValid) {
        res.status(401).json({ message: 'Invalid credentials' })
        return
      }

      const tokenPayload: TokenPayload = {
        id: member.id,
        is_active: true,
        is_admin: member.isAdmin,
      }
      const token = jwt.sign(tokenPayload, process.env.JWT_SECRET_KEY!, {
        expiresIn: '1h',
      })

      res.json({ token })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Internal server error during login' })
    }
  }) as RequestHandler,

  invite: (async (req, res) => {
    const {
      email,
      name,
      surname,
      course,
      role_id,
      entry_date,
      ...rest
    } = req.body as InviteDTO

    if (!email || !name || !surname || !course || !role_id || !entry_date) {
      res.status(400).json({ message: 'Missing required fields.' })
      return
    }

    if (!email.endsWith('@sga.pucminas.br')) {
      res
        .status(400)
        .json({ message: 'Email must be a @sga.pucminas.br address.' })
      return
    }

    if (!isSingleWord(name) || !isSingleWord(surname)) {
      res
        .status(400)
        .json({ message: 'Name and surname must be single words.' })
      return
    }

    const inviteToken = randomBytes(32).toString('hex')
    const inviteTokenExpiresAt = new Date(
      Date.now() + INVITE_EXPIRY_DAYS * 24 * 60 * 60 * 1000
    )
    const internalUrl =
      process.env.INTERNAL_URL || 'http://localhost:3000'

    try {
      const newMember = await prisma.member.create({
        data: {
          email,
          name,
          surname,
          course,
          entryDate: new Date(entry_date),
          bio: rest.bio,
          githubUrl: rest.github_url,
          instagramUrl: rest.instagram_url,
          linkedinUrl: rest.linkedin_url,
          isAdmin: rest.is_admin ?? false,
          status: MemberStatus.PENDING,
          inviteToken,
          inviteTokenExpiresAt,
          role: { connect: { id: role_id } },
        },
      })

      const inviteLink = `${internalUrl}/invite?token=${inviteToken}`
      await sendEmail(
        email,
        'Convite para o sistema COMP',
        `Olá, ${name}!\n\nVocê foi convidado(a) para fazer parte do sistema da COMP.\n\nClique no link abaixo para definir sua senha e ativar sua conta:\n\n${inviteLink}\n\nEste link expira em ${INVITE_EXPIRY_DAYS} dias.\n\nEquipe COMP`
      )

      res.status(201).json({
        message: `Invite sent to ${email}.`,
        member_url: `${BASE_URL}/api/members/${newMember.id}`,
      })
    } catch (err) {
      if (err instanceof Prisma.PrismaClientKnownRequestError) {
        if (err.code === 'P2002') {
          res
            .status(409)
            .json({ message: `Email '${email}' already exists.` })
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
      res.status(500).json({ error: 'Failed to send invite.' })
    }
  }) as RequestHandler,

  acceptInvite: (async (req, res) => {
    const { token, password } = req.body as AcceptInviteDTO

    if (!token || !password) {
      res.status(400).json({ message: 'Token and password are required.' })
      return
    }

    if (password.length < 8) {
      res
        .status(400)
        .json({ message: 'Password must be at least 8 characters.' })
      return
    }

    try {
      const member = await prisma.member.findUnique({
        where: { inviteToken: token },
      })

      if (!member || !member.inviteTokenExpiresAt) {
        res
          .status(400)
          .json({ message: 'Invalid or expired invite token.' })
        return
      }

      if (member.inviteTokenExpiresAt < new Date()) {
        res.status(400).json({ message: 'Invite token has expired.' })
        return
      }

      const hashedPassword = await bcrypt.hash(password, 10)
      await prisma.member.update({
        where: { id: member.id },
        data: {
          password: hashedPassword,
          status: MemberStatus.ACTIVE,
          inviteToken: null,
          inviteTokenExpiresAt: null,
        },
      })

      res.json({ message: 'Account activated successfully.' })
    } catch (err) {
      console.error(err)
      res.status(500).json({ error: 'Failed to accept invite.' })
    }
  }) as RequestHandler,
}

const isSingleWord = (word: string): boolean => /^[^\s]+$/.test(word)

export default authController
