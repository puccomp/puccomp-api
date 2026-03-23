import { RequestHandler } from 'express'
import jwt from 'jsonwebtoken'
import bcrypt from 'bcryptjs'
import { randomBytes } from 'crypto'
import { MemberStatus, Prisma } from '@prisma/client'
import { TokenPayload } from '../middlewares/isAuth.js'
import { BASE_URL } from '../index.js'
import prisma from '../utils/prisma.js'
import { sendEmail } from '../utils/email.js'
import { validate } from '../utils/validate.js'
import { sanitizeMemberForResponse } from './memberController.js'
import {
  LoginSchema,
  InviteSchema,
  AcceptInviteSchema,
  ForgotPasswordSchema,
  ResetPasswordSchema,
} from '../schemas/authSchemas.js'

const INVITE_EXPIRY_DAYS = 7

const authController = {
  login: (async (req, res) => {
    const body = validate(LoginSchema, req.body, res)
    if (!body) return
    const { email, password } = body

    try {
      const member = await prisma.member.findUnique({ where: { email } })

      if (!member || !member.password) {
        res.status(401).json({ message: 'Credenciais inválidas.' })
        return
      }

      if (member.status !== MemberStatus.ACTIVE) {
        res.status(403).json({
          message: 'Conta inativa. Aceite o convite antes de fazer login.',
        })
        return
      }

      const isPasswordValid = await bcrypt.compare(password, member.password)
      if (!isPasswordValid) {
        res.status(401).json({ message: 'Credenciais inválidas.' })
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
      res.status(500).json({ message: 'Erro interno do servidor ao fazer login.' })
    }
  }) as RequestHandler,

  invite: (async (req, res) => {
    const body = validate(InviteSchema, req.body, res)
    if (!body) return
    const { email, name, surname, course, role_id, entry_date, ...rest } = body

    const inviteToken = randomBytes(32).toString('hex')
    const inviteTokenExpiresAt = new Date(
      Date.now() + INVITE_EXPIRY_DAYS * 24 * 60 * 60 * 1000
    )
    const internalUrl = process.env.INTERNAL_URL || 'http://localhost:3000'

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

      const inviteLink = `${internalUrl}/convite?token=${inviteToken}`
      await sendEmail(
        email,
        'Convite para o sistema COMP',
        `Olá, ${name}!\n\nVocê foi convidado(a) para fazer parte do sistema da COMP.\n\nClique no link abaixo para definir sua senha e ativar sua conta:\n\n${inviteLink}\n\nEste link expira em ${INVITE_EXPIRY_DAYS} dias.\n\nEquipe COMP`
      )

      res.status(201).json({
        message: `Convite enviado para ${email}.`,
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
      res.status(500).json({ message: 'Falha ao enviar o convite.' })
    }
  }) as RequestHandler,

  acceptInvite: (async (req, res) => {
    const body = validate(AcceptInviteSchema, req.body, res)
    if (!body) return
    const { token, password } = body

    try {
      const member = await prisma.member.findUnique({
        where: { inviteToken: token },
      })

      if (!member || !member.inviteTokenExpiresAt) {
        res.status(400).json({ message: 'Token de convite inválido ou expirado.' })
        return
      }

      if (member.inviteTokenExpiresAt < new Date()) {
        res.status(400).json({ message: 'O token de convite expirou.' })
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

      res.json({ message: 'Conta ativada com sucesso.' })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Falha ao aceitar o convite.' })
    }
  }) as RequestHandler,
  forgotPassword: (async (req, res) => {
    const body = validate(ForgotPasswordSchema, req.body, res)
    if (!body) return
    const { email } = body

    const internalUrl = process.env.INTERNAL_URL || 'http://localhost:3000'

    try {
      const member = await prisma.member.findUnique({ where: { email } })

      if (member && member.status === MemberStatus.ACTIVE) {
        const token = randomBytes(32).toString('hex')
        const expiresAt = new Date(Date.now() + 60 * 60 * 1000)

        await prisma.member.update({
          where: { id: member.id },
          data: {
            passwordResetToken: token,
            passwordResetExpiresAt: expiresAt,
          },
        })

        const resetLink = `${internalUrl}/redefinir-senha?token=${token}`
        await sendEmail(
          email,
          'Redefinição de senha — COMP',
          `Olá, ${member.name}!\n\nRecebemos uma solicitação para redefinir a senha da sua conta.\n\nClique no link abaixo para criar uma nova senha:\n\n${resetLink}\n\nEste link expira em 1 hora. Se você não solicitou a redefinição, ignore este e-mail.\n\nEquipe COMP`
        )
      }

      res.json({
        message: 'Se o e-mail existir, um link de redefinição foi enviado.',
      })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Falha ao processar a solicitação.' })
    }
  }) as RequestHandler,

  resetPassword: (async (req, res) => {
    const body = validate(ResetPasswordSchema, req.body, res)
    if (!body) return
    const { token, password } = body

    try {
      const member = await prisma.member.findUnique({
        where: { passwordResetToken: token },
      })

      if (!member || !member.passwordResetExpiresAt) {
        res.status(400).json({ message: 'Token inválido ou expirado.' })
        return
      }

      if (member.passwordResetExpiresAt < new Date()) {
        res.status(400).json({ message: 'Token inválido ou expirado.' })
        return
      }

      const hashedPassword = await bcrypt.hash(password, 10)
      await prisma.member.update({
        where: { id: member.id },
        data: {
          password: hashedPassword,
          passwordResetToken: null,
          passwordResetExpiresAt: null,
        },
      })

      res.json({ message: 'Senha redefinida com sucesso.' })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Falha ao redefinir a senha.' })
    }
  }) as RequestHandler,

  me: (async (req, res) => {
    try {
      const member = await prisma.member.findUnique({
        where: { id: req.user!.id },
        include: { role: true },
      })

      if (!member) {
        res.status(404).json({ message: 'Membro não encontrado.' })
        return
      }

      res.json(sanitizeMemberForResponse(member))
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Falha ao buscar o membro.' })
    }
  }) as RequestHandler,
}

export default authController
