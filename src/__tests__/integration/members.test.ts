import { describe, it, expect, beforeEach, afterAll } from 'vitest'
import request from 'supertest'
import type { Request, Response, NextFunction } from 'express'
import { vi } from 'vitest'

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

const createActiveMember = async (email = 'ativo@sga.pucminas.br') =>
  prisma.member.create({
    data: {
      email,
      name: 'Joao',
      surname: 'Silva',
      course: 'CC',
      entryDate: new Date('2025-01-15'),
      isAdmin: false,
      status: 'ACTIVE',
      password: 'hashed',
      roleId: testRoleId,
    },
  })

const createPendingMember = async (email = 'pendente@sga.pucminas.br') =>
  prisma.member.create({
    data: {
      email,
      name: null,
      surname: null,
      course: null,
      entryDate: null,
      isAdmin: false,
      status: 'PENDING',
      inviteToken: 'some-token',
      inviteTokenExpiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
      roleId: testRoleId,
    },
  })

// ─── Setup ───────────────────────────────────────────────────────────────────

beforeEach(async () => {
  await prisma.contributor.deleteMany()
  await prisma.member.deleteMany()
  await prisma.role.deleteMany()
  const role = await prisma.role.create({ data: { name: 'Dev', level: 1 } })
  testRoleId = role.id
})

afterAll(async () => {
  await prisma.$disconnect()
})

// ─── PATCH /api/members/:id — membros PENDING ─────────────────────────────────

describe('PATCH /api/members/:id — membro PENDING', () => {
  it('retorna 422 ao tentar editar qualquer campo', async () => {
    const member = await createPendingMember()

    const res = await request(app)
      .patch(`/api/members/${member.id}`)
      .send({ name: 'Novo Nome' })

    expect(res.status).toBe(422)
    expect(res.body.message).toMatch(/pendente/)
  })

  it('retorna 422 ao tentar forçar status ACTIVE em membro PENDING', async () => {
    const member = await createPendingMember()

    const res = await request(app)
      .patch(`/api/members/${member.id}`)
      .send({ status: 'ACTIVE' })

    expect(res.status).toBe(422)
    expect(res.body.message).toMatch(/pendente/)
  })

  it('retorna 422 ao tentar trocar role de membro PENDING', async () => {
    const otherRole = await prisma.role.create({ data: { name: 'Design', level: 2 } })
    const member = await createPendingMember()

    const res = await request(app)
      .patch(`/api/members/${member.id}`)
      .send({ role_id: otherRole.id })

    expect(res.status).toBe(422)
    expect(res.body.message).toMatch(/pendente/)
  })

  it('não altera dados do membro PENDING no banco', async () => {
    const member = await createPendingMember()

    await request(app)
      .patch(`/api/members/${member.id}`)
      .send({ name: 'Tentativa' })

    const unchanged = await prisma.member.findUnique({ where: { id: member.id } })
    expect(unchanged!.name).toBeNull()
    expect(unchanged!.status).toBe('PENDING')
  })
})

// ─── PATCH /api/members/:id — status PENDING via update ──────────────────────

describe('PATCH /api/members/:id — transição para PENDING bloqueada', () => {
  it('retorna 422 ao tentar setar status PENDING em membro ACTIVE', async () => {
    const member = await createActiveMember()

    const res = await request(app)
      .patch(`/api/members/${member.id}`)
      .send({ status: 'PENDING' })

    expect(res.status).toBe(422)
    expect(res.body.message).toMatch(/PENDING/)
  })

  it('não altera o status do membro ACTIVE para PENDING no banco', async () => {
    const member = await createActiveMember()

    await request(app)
      .patch(`/api/members/${member.id}`)
      .send({ status: 'PENDING' })

    const unchanged = await prisma.member.findUnique({ where: { id: member.id } })
    expect(unchanged!.status).toBe('ACTIVE')
  })
})

// ─── PATCH /api/members/:id — membros ACTIVE (caminho feliz) ─────────────────

describe('PATCH /api/members/:id — membro ACTIVE', () => {
  it('atualiza campos normalmente', async () => {
    const member = await createActiveMember()

    const res = await request(app)
      .patch(`/api/members/${member.id}`)
      .send({ name: 'Pedro' })

    expect(res.status).toBe(200)
    expect(res.body.member.name).toBe('Pedro')
  })

  it('permite transição ACTIVE → INACTIVE com exit_date', async () => {
    const member = await createActiveMember()

    const res = await request(app)
      .patch(`/api/members/${member.id}`)
      .send({ status: 'INACTIVE', exit_date: '2026-03-01' })

    expect(res.status).toBe(200)
    expect(res.body.member.status).toBe('INACTIVE')
  })
})

// ─── PATCH /api/members/:id — reativação de membro INACTIVE ──────────────────

describe('PATCH /api/members/:id — reativar membro INACTIVE', () => {
  const createInactiveMember = async () =>
    prisma.member.create({
      data: {
        email: 'inativo@sga.pucminas.br',
        name: 'Ana',
        surname: 'Lima',
        course: 'CC',
        entryDate: new Date('2024-01-15'),
        exitDate: new Date('2025-12-01'),
        isAdmin: false,
        status: 'INACTIVE',
        password: 'hashed',
        roleId: testRoleId,
      },
    })

  it('reativa membro com apenas { status: "ACTIVE" } e limpa exit_date', async () => {
    const member = await createInactiveMember()

    const res = await request(app)
      .patch(`/api/members/${member.id}`)
      .send({ status: 'ACTIVE' })

    expect(res.status).toBe(200)
    expect(res.body.member.status).toBe('ACTIVE')
    expect(res.body.member.exit_date).toBeNull()
  })

  it('limpa exit_date no banco ao reativar', async () => {
    const member = await createInactiveMember()

    await request(app)
      .patch(`/api/members/${member.id}`)
      .send({ status: 'ACTIVE' })

    const updated = await prisma.member.findUnique({ where: { id: member.id } })
    expect(updated!.exitDate).toBeNull()
    expect(updated!.status).toBe('ACTIVE')
  })
})
