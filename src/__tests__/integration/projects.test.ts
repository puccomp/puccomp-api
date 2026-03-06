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

import app from '../../index.js'
import prisma from '../../utils/prisma.js'

// ─── Helpers ────────────────────────────────────────────────────────────────

const createProject = (overrides = {}) =>
  prisma.project.create({
    data: {
      name: 'Test Project',
      slug: 'test-project',
      description: 'Test description',
      createdAt: new Date(),
      ...overrides,
    },
  })

// ─── Setup ──────────────────────────────────────────────────────────────────

beforeEach(async () => {
  await prisma.projectAsset.deleteMany()
  await prisma.project.deleteMany()
})

afterAll(async () => {
  await prisma.$disconnect()
})

// ─── Tests ──────────────────────────────────────────────────────────────────

describe('POST /api/projects', () => {
  it('creates a project and returns 201', async () => {
    const res = await request(app).post('/api/projects').send({
      name: 'Meu Projeto',
      description: 'Descrição',
    })

    expect(res.status).toBe(201)
    expect(res.body).toHaveProperty('project_url')
  })

  it('auto-generates slug from name (including accents and spaces)', async () => {
    await request(app).post('/api/projects').send({
      name: 'Gestão de TI',
      description: 'Descrição',
    })

    const project = await prisma.project.findFirst()
    expect(project?.slug).toBe('gestao-de-ti')
  })

  it('uses explicitly provided slug instead of auto-generating', async () => {
    await request(app).post('/api/projects').send({
      name: 'Meu Projeto',
      description: 'Descrição',
      slug: 'slug-personalizado',
    })

    const project = await prisma.project.findFirst()
    expect(project?.slug).toBe('slug-personalizado')
  })

  it('returns 409 on duplicate name', async () => {
    await request(app)
      .post('/api/projects')
      .send({ name: 'Duplicado', description: 'First' })

    const res = await request(app)
      .post('/api/projects')
      .send({ name: 'Duplicado', description: 'Second' })

    expect(res.status).toBe(409)
  })

  it('returns 409 on duplicate slug', async () => {
    await request(app).post('/api/projects').send({
      name: 'Projeto A',
      description: 'First',
      slug: 'mesmo-slug',
    })

    const res = await request(app).post('/api/projects').send({
      name: 'Projeto B',
      description: 'Second',
      slug: 'mesmo-slug',
    })

    expect(res.status).toBe(409)
  })

  it('returns 400 when description is missing', async () => {
    const res = await request(app)
      .post('/api/projects')
      .send({ name: 'Sem Descrição' })

    expect(res.status).toBe(400)
  })

  it('returns 400 when end_date is before start_date', async () => {
    const res = await request(app).post('/api/projects').send({
      name: 'Data Inválida',
      description: 'Descrição',
      start_date: '2024-12-01',
      end_date: '2024-01-01',
    })

    expect(res.status).toBe(400)
  })

  it('defaults status to PLANNING when not provided', async () => {
    await request(app).post('/api/projects').send({
      name: 'Status Padrão',
      description: 'Descrição',
    })

    const project = await prisma.project.findFirst()
    expect(project?.status).toBe('PLANNING')
  })

  it('auto-fills end_date when creating with status DONE and no end_date provided', async () => {
    const res = await request(app).post('/api/projects').send({
      name: 'Done Project',
      description: 'Descrição',
      status: 'DONE',
      start_date: '2026-01-01',
    })

    expect(res.status).toBe(201)

    const project = await prisma.project.findFirst({ where: { name: 'Done Project' } })
    expect(project?.endDate).not.toBeNull()
  })
})

describe('GET /api/projects', () => {
  it('returns empty array when no projects exist', async () => {
    const res = await request(app).get('/api/projects')
    expect(res.status).toBe(200)
    expect(res.body.data).toEqual([])
  })

  it('returns all projects with assets included', async () => {
    await createProject({ name: 'A', slug: 'projeto-a' })
    await createProject({ name: 'B', slug: 'projeto-b' })

    const res = await request(app).get('/api/projects')
    expect(res.status).toBe(200)
    expect(res.body.data).toHaveLength(2)
    expect(res.body.data[0]).toHaveProperty('assets')
  })
})

describe('GET /api/projects/:slug', () => {
  it('returns a project by slug with assets array', async () => {
    await createProject()

    const res = await request(app).get('/api/projects/test-project')
    expect(res.status).toBe(200)
    expect(res.body.slug).toBe('test-project')
    expect(res.body.assets).toEqual([])
  })

  it('returns 404 for non-existent slug', async () => {
    const res = await request(app).get('/api/projects/does-not-exist')
    expect(res.status).toBe(404)
  })

  it('returns contributors_url and technologies_url using the slug', async () => {
    await createProject()

    const res = await request(app).get('/api/projects/test-project')
    expect(res.body.contributors_url).toContain('/test-project/contributors')
    expect(res.body.technologies_url).toContain('/test-project/technologies')
  })
})

describe('PATCH /api/projects/:slug', () => {
  it('updates a project field and returns 200', async () => {
    await createProject()

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ description: 'Descrição atualizada' })

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({
      where: { slug: 'test-project' },
    })
    expect(updated?.description).toBe('Descrição atualizada')
  })

  it('updates the slug and responds with the new project_url', async () => {
    await createProject()

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ slug: 'novo-slug' })

    expect(res.status).toBe(200)
    expect(res.body.project_url).toContain('/novo-slug')
  })

  it('returns 404 when slug does not exist', async () => {
    const res = await request(app)
      .patch('/api/projects/fantasma')
      .send({ description: 'Update' })

    expect(res.status).toBe(404)
  })

  it('returns 400 with empty body', async () => {
    await createProject()

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({})

    expect(res.status).toBe(400)
  })

  it('returns 400 when end_date is before start_date', async () => {
    await createProject()

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ start_date: '2024-12-01', end_date: '2024-01-01' })

    expect(res.status).toBe(400)
  })

  it('sets endDate to now when status changes to DONE without end_date', async () => {
    await createProject({ startDate: new Date('2026-01-01') })

    const before = new Date()
    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'DONE' })
    const after = new Date()

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({
      where: { slug: 'test-project' },
    })
    expect(updated?.status).toBe('DONE')
    expect(updated?.endDate).not.toBeNull()
    // endDate is stored as DATE (no time), so just check it falls within today
    const endDate = updated!.endDate!
    expect(endDate.getTime()).toBeGreaterThanOrEqual(
      new Date(before.toDateString()).getTime(),
    )
    expect(endDate.getTime()).toBeLessThanOrEqual(
      new Date(after.toDateString()).getTime() + 86_400_000 - 1,
    )
  })

  it('uses the provided end_date when status changes to DONE', async () => {
    await createProject({ startDate: new Date('2026-01-01') })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'DONE', end_date: '2026-03-02' })

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({
      where: { slug: 'test-project' },
    })
    expect(updated?.status).toBe('DONE')
    // DB stores as Date; compare only the date portion
    expect(updated?.endDate?.toISOString().slice(0, 10)).toBe('2026-03-02')
  })

  it('clears endDate and startDate when IN_PROGRESS transitions to PLANNING', async () => {
    await createProject({ status: 'IN_PROGRESS', startDate: new Date('2026-01-01') })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'PLANNING' })

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({
      where: { slug: 'test-project' },
    })
    expect(updated?.status).toBe('PLANNING')
    expect(updated?.startDate).toBeNull()
    expect(updated?.endDate).toBeNull()
  })

  it('auto-sets startDate when transitioning to IN_PROGRESS with no startDate in DB or body', async () => {
    await createProject() // no startDate

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'IN_PROGRESS' })

    expect(res.status).toBe(200)
    const updated = await prisma.project.findUnique({ where: { slug: 'test-project' } })
    expect(updated?.startDate).not.toBeNull()
  })

  it('allows transition to IN_PROGRESS when startDate already in DB', async () => {
    await createProject({ startDate: new Date('2026-01-01') })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'IN_PROGRESS' })

    expect(res.status).toBe(200)
  })

  it('allows transition to IN_PROGRESS when start_date provided in body', async () => {
    await createProject()

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'IN_PROGRESS', start_date: '2026-01-01' })

    expect(res.status).toBe(200)
  })

  it('auto-fills end_date when transitioning to DONE without end_date in DB', async () => {
    await createProject({ startDate: new Date('2026-01-01') }) // no endDate

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'DONE' })

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({ where: { slug: 'test-project' } })
    expect(updated?.endDate).not.toBeNull()
  })

  it('keeps existing end_date when staying DONE without end_date in body', async () => {
    await createProject({
      status: 'DONE',
      startDate: new Date('2026-01-01'),
      endDate: new Date('2026-02-01'),
    })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ priority: 5 }) // update something else, endDate not in body

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({ where: { slug: 'test-project' } })
    expect(updated?.endDate?.toISOString().slice(0, 10)).toBe('2026-02-01')
  })

  it('clears end_date when transitioning to PLANNING', async () => {
    await createProject({ endDate: new Date('2026-02-01') })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'PLANNING' })

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({ where: { slug: 'test-project' } })
    expect(updated?.endDate).toBeNull()
  })

  // ── Workflow / transition rules ────────────────────────────────────────────

  it('allows DONE → IN_PROGRESS (clears endDate, keeps startDate)', async () => {
    await createProject({
      status: 'DONE',
      startDate: new Date('2026-01-01'),
      endDate: new Date('2026-03-01'),
    })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'IN_PROGRESS' })

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({ where: { slug: 'test-project' } })
    expect(updated?.status).toBe('IN_PROGRESS')
    expect(updated?.startDate?.toISOString().slice(0, 10)).toBe('2026-01-01')
    expect(updated?.endDate).toBeNull()
  })

  it('allows DONE → PAUSED (clears endDate, keeps startDate)', async () => {
    await createProject({
      status: 'DONE',
      startDate: new Date('2026-01-01'),
      endDate: new Date('2026-03-01'),
    })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'PAUSED' })

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({ where: { slug: 'test-project' } })
    expect(updated?.status).toBe('PAUSED')
    expect(updated?.startDate?.toISOString().slice(0, 10)).toBe('2026-01-01')
    expect(updated?.endDate).toBeNull()
  })

  it('allows DONE → PLANNING (clears both endDate and startDate)', async () => {
    await createProject({
      status: 'DONE',
      startDate: new Date('2026-01-01'),
      endDate: new Date('2026-03-01'),
    })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'PLANNING' })

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({ where: { slug: 'test-project' } })
    expect(updated?.status).toBe('PLANNING')
    expect(updated?.startDate).toBeNull()
    expect(updated?.endDate).toBeNull()
  })

  it('allows DONE → DONE (updating fields while staying DONE)', async () => {
    await createProject({
      status: 'DONE',
      startDate: new Date('2026-01-01'),
      endDate: new Date('2026-03-01'),
    })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'DONE', end_date: '2026-03-15' })

    expect(res.status).toBe(200)
    const updated = await prisma.project.findUnique({ where: { slug: 'test-project' } })
    expect(updated?.endDate?.toISOString().slice(0, 10)).toBe('2026-03-15')
  })

  it('auto-sets startDate to today when transitioning PLANNING → IN_PROGRESS without startDate', async () => {
    await createProject() // PLANNING, no startDate

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'IN_PROGRESS' })

    expect(res.status).toBe(200)
    const updated = await prisma.project.findUnique({ where: { slug: 'test-project' } })
    expect(updated?.startDate).not.toBeNull()
    expect(updated?.endDate).toBeNull()
  })

  it('clears startDate and endDate when IN_PROGRESS transitions to PLANNING', async () => {
    await createProject({
      status: 'IN_PROGRESS',
      startDate: new Date('2026-01-01'),
    })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'PLANNING' })

    expect(res.status).toBe(200)
    const updated = await prisma.project.findUnique({ where: { slug: 'test-project' } })
    expect(updated?.startDate).toBeNull()
    expect(updated?.endDate).toBeNull()
  })

  it('returns 422 when transitioning PLANNING → PAUSED without startDate', async () => {
    await createProject() // PLANNING, no startDate

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'PAUSED' })

    expect(res.status).toBe(422)
  })

  it('clears endDate when transitioning IN_PROGRESS → PAUSED', async () => {
    // endDate should never exist in IN_PROGRESS, but if somehow it does, clear it
    await createProject({
      status: 'IN_PROGRESS',
      startDate: new Date('2026-01-01'),
    })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'PAUSED' })

    expect(res.status).toBe(200)
    const updated = await prisma.project.findUnique({ where: { slug: 'test-project' } })
    expect(updated?.status).toBe('PAUSED')
    expect(updated?.startDate?.toISOString().slice(0, 10)).toBe('2026-01-01')
    expect(updated?.endDate).toBeNull()
  })

  it('returns 422 when nullifying startDate on an IN_PROGRESS project', async () => {
    await createProject({ status: 'IN_PROGRESS', startDate: new Date('2026-01-01') })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ start_date: null })

    expect(res.status).toBe(422)
  })

  it('returns 422 when nullifying endDate on a DONE project', async () => {
    await createProject({
      status: 'DONE',
      startDate: new Date('2026-01-01'),
      endDate: new Date('2026-03-01'),
    })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ end_date: null })

    expect(res.status).toBe(422)
  })
})

describe('DELETE /api/projects/:slug', () => {
  it('deletes the project and returns 200', async () => {
    await createProject()

    const res = await request(app).delete('/api/projects/test-project')
    expect(res.status).toBe(200)

    const project = await prisma.project.findUnique({
      where: { slug: 'test-project' },
    })
    expect(project).toBeNull()
  })

  it('returns 404 when slug does not exist', async () => {
    const res = await request(app).delete('/api/projects/fantasma')
    expect(res.status).toBe(404)
  })
})

describe('GET /api/projects/:slug/assets', () => {
  it('returns empty array for project with no assets', async () => {
    await createProject()

    const res = await request(app).get('/api/projects/test-project/assets')
    expect(res.status).toBe(200)
    expect(res.body).toEqual([])
  })

  it('returns 404 for non-existent project', async () => {
    const res = await request(app).get('/api/projects/fantasma/assets')
    expect(res.status).toBe(404)
  })
})

describe('POST /api/projects/:slug/assets', () => {
  it('returns 400 when no file is attached', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .send({})

    expect(res.status).toBe(400)
  })
})

describe('DELETE /api/projects/:slug/assets/:asset_id', () => {
  it('returns 404 for non-existent asset', async () => {
    await createProject()

    const res = await request(app).delete(
      '/api/projects/test-project/assets/99999'
    )
    expect(res.status).toBe(404)
  })
})
