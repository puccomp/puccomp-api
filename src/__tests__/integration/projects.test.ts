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

  it('sets completedAt to now when status changes to DONE without completed_at', async () => {
    await createProject()

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
    expect(updated?.completedAt).not.toBeNull()
    // completedAt is stored as DATE (no time), so just check it falls within today
    const completedAt = updated!.completedAt!
    expect(completedAt.getTime()).toBeGreaterThanOrEqual(
      new Date(before.toDateString()).getTime(),
    )
    expect(completedAt.getTime()).toBeLessThanOrEqual(
      new Date(after.toDateString()).getTime() + 86_400_000 - 1,
    )
  })

  it('uses the provided completed_at when status changes to DONE', async () => {
    await createProject()

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'DONE', completed_at: '2026-03-02' })

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({
      where: { slug: 'test-project' },
    })
    expect(updated?.status).toBe('DONE')
    // DB stores as Date; compare only the date portion
    expect(updated?.completedAt?.toISOString().slice(0, 10)).toBe('2026-03-02')
  })

  it('clears completedAt when status leaves DONE', async () => {
    await createProject({ status: 'DONE', completedAt: new Date('2026-03-01') })

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ status: 'IN_PROGRESS' })

    expect(res.status).toBe(200)

    const updated = await prisma.project.findUnique({
      where: { slug: 'test-project' },
    })
    expect(updated?.status).toBe('IN_PROGRESS')
    expect(updated?.completedAt).toBeNull()
  })

  it('returns 422 when setting completed_at on a non-DONE project', async () => {
    await createProject()

    const res = await request(app)
      .patch('/api/projects/test-project')
      .send({ completed_at: '2026-03-02' })

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
