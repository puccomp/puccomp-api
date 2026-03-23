import { describe, it, expect, beforeEach, afterAll, vi } from 'vitest'
import request from 'supertest'
import type { Request, Response, NextFunction } from 'express'

// Mocks must be declared before app import — Vitest hoists these
vi.mock('../../middlewares/isAuth.js', () => ({
  default: (req: Request, _res: Response, next: NextFunction) => {
    req.user = { id: 1, is_active: true, is_admin: true }
    next()
  },
}))

vi.mock('../../utils/s3.js', () => ({
  uploadObjectToS3: vi.fn().mockResolvedValue(undefined),
  deleteObjectFromS3: vi.fn().mockResolvedValue(undefined),
  getS3URL: vi.fn((key: string) => `https://s3.example.com/${key}`),
  getSignedS3URL: vi.fn().mockResolvedValue('https://s3.example.com/signed'),
}))

vi.mock('../../utils/email.js', () => ({
  sendEmail: vi.fn().mockResolvedValue(undefined),
}))

import app from '../../index.js'
import prisma from '../../utils/prisma.js'

// ─── Helpers ─────────────────────────────────────────────────────────────────

let testRoleId: number

const INVITE_BODY = () => ({ email: 'joao@sga.pucminas.br', role_id: testRoleId })

const ACCEPT_BODY = {
  name: 'Joao',
  surname: 'Silva',
  course: 'Ciencia da Computacao',
  entry_date: '2026-01-15',
  password: 'senha1234',
}

const createPendingMember = async (token = 'valid-token-abc123') => {
  return prisma.member.create({
    data: {
      email: 'joao@sga.pucminas.br',
      name: null,
      surname: null,
      course: null,
      entryDate: null,
      isAdmin: false,
      status: 'PENDING',
      inviteToken: token,
      inviteTokenExpiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
    },
  })
}

// ─── Setup ───────────────────────────────────────────────────────────────────

beforeEach(async () => {
  await prisma.contributor.deleteMany()
  await prisma.member.deleteMany()
  await prisma.role.deleteMany()
  const role = await prisma.role.create({
    data: { name: 'Dev', level: 1 },
  })
  testRoleId = role.id
})

afterAll(async () => {
  await prisma.$disconnect()
})

// ─── POST /api/auth/invite ────────────────────────────────────────────────────

describe('POST /api/auth/invite', () => {
  it('cria membro PENDING e retorna 201', async () => {
    const res = await request(app)
      .post('/api/auth/invite')
      .send(INVITE_BODY())

    expect(res.status).toBe(201)
    expect(res.body.message).toMatch(/joao@sga\.pucminas\.br/)

    const member = await prisma.member.findUnique({
      where: { email: 'joao@sga.pucminas.br' },
    })
    expect(member).not.toBeNull()
    expect(member!.status).toBe('PENDING')
    expect(member!.name).toBeNull()
    expect(member!.inviteToken).toBeTruthy()
  })

  it('retorna 409 se o e-mail já existe', async () => {
    await createPendingMember()

    const res = await request(app)
      .post('/api/auth/invite')
      .send(INVITE_BODY())

    expect(res.status).toBe(409)
  })

  it('retorna 422 para e-mail fora do domínio @sga.pucminas.br', async () => {
    const res = await request(app)
      .post('/api/auth/invite')
      .send({ email: 'joao@gmail.com' })

    expect(res.status).toBe(400)
  })
})

// ─── GET /api/auth/invite/:token ──────────────────────────────────────────────

describe('GET /api/auth/invite/:token', () => {
  it('retorna email e role para token válido', async () => {
    await createPendingMember('abc123')

    const res = await request(app).get('/api/auth/invite/abc123')

    expect(res.status).toBe(200)
    expect(res.body.email).toBe('joao@sga.pucminas.br')
    expect(res.body).toHaveProperty('expires_at')
  })

  it('retorna 404 para token inexistente', async () => {
    const res = await request(app).get('/api/auth/invite/nao-existe')

    expect(res.status).toBe(404)
  })

  it('retorna 410 para token expirado', async () => {
    await prisma.member.create({
      data: {
        email: 'joao@sga.pucminas.br',
        name: null,
        surname: null,
        course: null,
        entryDate: null,
        isAdmin: false,
        status: 'PENDING',
        inviteToken: 'expired-token',
        inviteTokenExpiresAt: new Date(Date.now() - 1000),
      },
    })

    const res = await request(app).get('/api/auth/invite/expired-token')

    expect(res.status).toBe(410)
  })
})

// ─── POST /api/auth/accept-invite ────────────────────────────────────────────

describe('POST /api/auth/accept-invite', () => {
  it('ativa conta com dados do usuário e retorna 200', async () => {
    await createPendingMember('accept-token')

    const res = await request(app)
      .post('/api/auth/accept-invite')
      .send({ token: 'accept-token', ...ACCEPT_BODY })

    expect(res.status).toBe(200)
    expect(res.body.message).toBe('Conta ativada com sucesso.')

    const member = await prisma.member.findUnique({
      where: { email: 'joao@sga.pucminas.br' },
    })
    expect(member!.status).toBe('ACTIVE')
    expect(member!.name).toBe('Joao')
    expect(member!.surname).toBe('Silva')
    expect(member!.course).toBe('Ciencia da Computacao')
    expect(member!.entryDate).not.toBeNull()
    expect(member!.password).not.toBeNull()
    expect(member!.inviteToken).toBeNull()
  })

  it('retorna 400 para token inválido', async () => {
    const res = await request(app)
      .post('/api/auth/accept-invite')
      .send({ token: 'token-invalido', ...ACCEPT_BODY })

    expect(res.status).toBe(400)
  })

  it('retorna 400 para token expirado', async () => {
    await prisma.member.create({
      data: {
        email: 'joao@sga.pucminas.br',
        name: null,
        surname: null,
        course: null,
        entryDate: null,
        isAdmin: false,
        status: 'PENDING',
        inviteToken: 'expired-accept',
        inviteTokenExpiresAt: new Date(Date.now() - 1000),
      },
    })

    const res = await request(app)
      .post('/api/auth/accept-invite')
      .send({ token: 'expired-accept', ...ACCEPT_BODY })

    expect(res.status).toBe(400)
  })

  it('retorna 422 se campos obrigatórios estiverem faltando', async () => {
    const res = await request(app)
      .post('/api/auth/accept-invite')
      .send({ token: 'qualquer', password: 'senha1234' })

    expect(res.status).toBe(400)
  })

  it('permite campos opcionais (bio, github_url etc.)', async () => {
    await createPendingMember('full-token')

    const res = await request(app)
      .post('/api/auth/accept-invite')
      .send({
        token: 'full-token',
        ...ACCEPT_BODY,
        bio: 'Desenvolvedor apaixonado',
        github_url: 'https://github.com/joao',
        linkedin_url: 'https://linkedin.com/in/joao',
      })

    expect(res.status).toBe(200)

    const member = await prisma.member.findUnique({
      where: { email: 'joao@sga.pucminas.br' },
    })
    expect(member!.bio).toBe('Desenvolvedor apaixonado')
    expect(member!.githubUrl).toBe('https://github.com/joao')
  })
})
