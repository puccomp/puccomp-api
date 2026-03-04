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

// ─── Helpers ─────────────────────────────────────────────────────────────────

const createTech = (overrides: object = {}) =>
  prisma.technology.create({
    data: {
      name: 'React',
      slug: 'react',
      type: 'FRAMEWORK',
      ...overrides,
    },
  })

const createProject = (overrides: object = {}) =>
  prisma.project.create({
    data: {
      name: 'Test Project',
      slug: 'test-project',
      description: 'Test description',
      createdAt: new Date(),
      ...overrides,
    },
  })

// ─── Setup ───────────────────────────────────────────────────────────────────

beforeEach(async () => {
  await prisma.projectTechnology.deleteMany()
  await prisma.projectAsset.deleteMany()
  await prisma.project.deleteMany()
  await prisma.technology.deleteMany()
})

afterAll(async () => {
  await prisma.$disconnect()
})

// ─── GET /api/technologies ────────────────────────────────────────────────────

describe('GET /api/technologies', () => {
  it('returns empty array when no technologies exist', async () => {
    const res = await request(app).get('/api/technologies')
    expect(res.status).toBe(200)
    expect(res.body).toEqual([])
  })

  it('returns technologies with formatted fields', async () => {
    await createTech({ name: 'Vue', slug: 'vue', type: 'FRAMEWORK', color: '#42b883', description: 'Progressive framework' })

    const res = await request(app).get('/api/technologies')

    expect(res.status).toBe(200)
    expect(res.body).toHaveLength(1)
    const tech = res.body[0]
    expect(tech).toHaveProperty('id')
    expect(tech).toHaveProperty('name', 'Vue')
    expect(tech).toHaveProperty('slug', 'vue')
    expect(tech).toHaveProperty('type', 'FRAMEWORK')
    expect(tech).toHaveProperty('color', '#42b883')
    expect(tech).toHaveProperty('description', 'Progressive framework')
    expect(tech).toHaveProperty('icon_url')
    expect(tech).toHaveProperty('project_count', 0)
    expect(tech).toHaveProperty('created_at')
    expect(tech).toHaveProperty('updated_at')
    expect(tech).not.toHaveProperty('iconUrl')
    expect(tech).not.toHaveProperty('createdAt')
  })

  it('filters by search (case-insensitive)', async () => {
    await createTech({ name: 'React', slug: 'react', type: 'FRAMEWORK' })
    await createTech({ name: 'TypeScript', slug: 'typescript', type: 'LANGUAGE' })

    const res = await request(app).get('/api/technologies?search=react')

    expect(res.status).toBe(200)
    expect(res.body).toHaveLength(1)
    expect(res.body[0].name).toBe('React')
  })

  it('filters by type', async () => {
    await createTech({ name: 'React', slug: 'react', type: 'FRAMEWORK' })
    await createTech({ name: 'TypeScript', slug: 'typescript', type: 'LANGUAGE' })

    const res = await request(app).get('/api/technologies?type=LANGUAGE')

    expect(res.status).toBe(200)
    expect(res.body).toHaveLength(1)
    expect(res.body[0].name).toBe('TypeScript')
  })

  it('respects limit parameter', async () => {
    await createTech({ name: 'React', slug: 'react', type: 'FRAMEWORK' })
    await createTech({ name: 'Vue', slug: 'vue', type: 'FRAMEWORK' })
    await createTech({ name: 'Angular', slug: 'angular', type: 'FRAMEWORK' })

    const res = await request(app).get('/api/technologies?limit=2')

    expect(res.status).toBe(200)
    expect(res.body).toHaveLength(2)
  })

  it('returns 400 for invalid limit', async () => {
    const res = await request(app).get('/api/technologies?limit=0')
    expect(res.status).toBe(400)
  })

  it('filters out technologies already linked to exclude_project', async () => {
    const react = await createTech({ name: 'React', slug: 'react', type: 'FRAMEWORK' })
    await createTech({ name: 'Vue', slug: 'vue', type: 'FRAMEWORK' })
    const project = await createProject()
    await prisma.projectTechnology.create({
      data: { projectId: project.id, technologyId: react.id, usageLevel: 'PRIMARY' },
    })

    const res = await request(app).get(
      `/api/technologies?exclude_project=${project.slug}`
    )

    expect(res.status).toBe(200)
    expect(res.body).toHaveLength(1)
    expect(res.body[0].name).toBe('Vue')
  })
})

// ─── GET /api/technologies/:id ────────────────────────────────────────────────

describe('GET /api/technologies/:id', () => {
  it('returns a technology by id', async () => {
    const tech = await createTech()

    const res = await request(app).get(`/api/technologies/${tech.id}`)

    expect(res.status).toBe(200)
    expect(res.body.name).toBe('React')
    expect(res.body.id).toBe(tech.id)
  })

  it('returns 404 for non-existent id', async () => {
    const res = await request(app).get('/api/technologies/99999')
    expect(res.status).toBe(404)
  })

  it('returns 400 for invalid id format', async () => {
    const res = await request(app).get('/api/technologies/abc')
    expect(res.status).toBe(400)
  })
})

// ─── POST /api/technologies ───────────────────────────────────────────────────

describe('POST /api/technologies', () => {
  it('creates a technology and returns 201 with technology_url', async () => {
    const res = await request(app).post('/api/technologies').send({
      name: 'React',
      type: 'FRAMEWORK',
    })

    expect(res.status).toBe(201)
    expect(res.body).toHaveProperty('technology_url')
    expect(res.body.message).toMatch(/sucesso/i)
  })

  it('persists the technology in the database', async () => {
    await request(app).post('/api/technologies').send({
      name: 'Vue',
      type: 'FRAMEWORK',
    })

    const tech = await prisma.technology.findFirst({ where: { name: 'Vue' } })
    expect(tech).not.toBeNull()
    expect(tech?.slug).toBe('vue')
  })

  it('auto-generates slug from name (including accents)', async () => {
    await request(app).post('/api/technologies').send({
      name: 'Gestão de Dados',
      type: 'TOOL',
    })

    const tech = await prisma.technology.findFirst()
    expect(tech?.slug).toBe('gestao-de-dados')
  })

  it('accepts optional fields: icon_url, color, description', async () => {
    await request(app).post('/api/technologies').send({
      name: 'React',
      type: 'FRAMEWORK',
      icon_url: 'https://example.com/react.svg',
      color: '#61DAFB',
      description: 'A JavaScript library for building user interfaces.',
    })

    const tech = await prisma.technology.findFirst()
    expect(tech?.iconUrl).toBe('https://example.com/react.svg')
    expect(tech?.color).toBe('#61DAFB')
    expect(tech?.description).toMatch(/JavaScript/)
  })

  it('returns 409 on duplicate name', async () => {
    await request(app).post('/api/technologies').send({ name: 'React', type: 'FRAMEWORK' })

    const res = await request(app).post('/api/technologies').send({ name: 'React', type: 'LIBRARY' })

    expect(res.status).toBe(409)
  })

  it('returns 400 when name is missing', async () => {
    const res = await request(app).post('/api/technologies').send({ type: 'FRAMEWORK' })
    expect(res.status).toBe(400)
  })

  it('returns 400 when type is missing', async () => {
    const res = await request(app).post('/api/technologies').send({ name: 'React' })
    expect(res.status).toBe(400)
  })

  it('returns 400 for invalid type', async () => {
    const res = await request(app)
      .post('/api/technologies')
      .send({ name: 'React', type: 'INVALID' })
    expect(res.status).toBe(400)
  })

  it('returns 400 for invalid color format', async () => {
    const res = await request(app).post('/api/technologies').send({
      name: 'React',
      type: 'FRAMEWORK',
      color: 'not-a-color',
    })
    expect(res.status).toBe(400)
  })

  it('returns 400 for invalid icon_url', async () => {
    const res = await request(app).post('/api/technologies').send({
      name: 'React',
      type: 'FRAMEWORK',
      icon_url: 'not-a-url',
    })
    expect(res.status).toBe(400)
  })
})

// ─── PATCH /api/technologies/:id ─────────────────────────────────────────────

describe('PATCH /api/technologies/:id', () => {
  it('updates the name and regenerates the slug', async () => {
    const tech = await createTech()

    const res = await request(app)
      .patch(`/api/technologies/${tech.id}`)
      .send({ name: 'React Native' })

    expect(res.status).toBe(200)
    expect(res.body).toHaveProperty('technology_url')

    const updated = await prisma.technology.findUnique({ where: { id: tech.id } })
    expect(updated?.name).toBe('React Native')
    expect(updated?.slug).toBe('react-native')
  })

  it('updates optional fields (color, description)', async () => {
    const tech = await createTech()

    await request(app)
      .patch(`/api/technologies/${tech.id}`)
      .send({ color: '#000000', description: 'Updated description' })

    const updated = await prisma.technology.findUnique({ where: { id: tech.id } })
    expect(updated?.color).toBe('#000000')
    expect(updated?.description).toBe('Updated description')
  })

  it('clears optional fields when sent as null', async () => {
    const tech = await prisma.technology.create({
      data: { name: 'React', slug: 'react', type: 'FRAMEWORK', color: '#61DAFB', description: 'Library' },
    })

    await request(app)
      .patch(`/api/technologies/${tech.id}`)
      .send({ color: null, description: null })

    const updated = await prisma.technology.findUnique({ where: { id: tech.id } })
    expect(updated?.color).toBeNull()
    expect(updated?.description).toBeNull()
  })

  it('returns 404 for non-existent id', async () => {
    const res = await request(app)
      .patch('/api/technologies/99999')
      .send({ name: 'Nada' })
    expect(res.status).toBe(404)
  })

  it('returns 409 on duplicate name', async () => {
    await createTech()
    const other = await prisma.technology.create({
      data: { name: 'Vue', slug: 'vue', type: 'FRAMEWORK' },
    })

    const res = await request(app)
      .patch(`/api/technologies/${other.id}`)
      .send({ name: 'React' })

    expect(res.status).toBe(409)
  })

  it('returns 400 with empty body', async () => {
    const tech = await createTech()

    const res = await request(app)
      .patch(`/api/technologies/${tech.id}`)
      .send({})

    expect(res.status).toBe(400)
  })
})

// ─── DELETE /api/technologies/:id ─────────────────────────────────────────────

describe('DELETE /api/technologies/:id', () => {
  it('deletes a technology and returns 200', async () => {
    const tech = await createTech()

    const res = await request(app).delete(`/api/technologies/${tech.id}`)

    expect(res.status).toBe(200)
    expect(res.body.message).toMatch(/sucesso/i)

    const deleted = await prisma.technology.findUnique({ where: { id: tech.id } })
    expect(deleted).toBeNull()
  })

  it('returns 404 for non-existent id', async () => {
    const res = await request(app).delete('/api/technologies/99999')
    expect(res.status).toBe(404)
  })

  it('returns 400 when technology is used in a project', async () => {
    const tech = await createTech()
    const project = await createProject()
    await prisma.projectTechnology.create({
      data: { projectId: project.id, technologyId: tech.id, usageLevel: 'PRIMARY' },
    })

    const res = await request(app).delete(`/api/technologies/${tech.id}`)

    expect(res.status).toBe(400)
    expect(res.body.message).toMatch(/projeto/i)

    const still = await prisma.technology.findUnique({ where: { id: tech.id } })
    expect(still).not.toBeNull()
  })
})

// ─── GET /api/projects/:slug/technologies ─────────────────────────────────────

describe('GET /api/projects/:slug/technologies', () => {
  it('returns empty array when project has no technologies', async () => {
    await createProject()

    const res = await request(app).get('/api/projects/test-project/technologies')

    expect(res.status).toBe(200)
    expect(res.body).toEqual([])
  })

  it('returns technologies with formatted fields', async () => {
    const project = await createProject()
    const tech = await createTech({ name: 'TypeScript', slug: 'typescript', type: 'LANGUAGE', color: '#3178C6' })
    await prisma.projectTechnology.create({
      data: { projectId: project.id, technologyId: tech.id, usageLevel: 'PRIMARY' },
    })

    const res = await request(app).get('/api/projects/test-project/technologies')

    expect(res.status).toBe(200)
    expect(res.body).toHaveLength(1)
    const item = res.body[0]
    expect(item).toHaveProperty('id', tech.id)
    expect(item).toHaveProperty('name', 'TypeScript')
    expect(item).toHaveProperty('slug', 'typescript')
    expect(item).toHaveProperty('type', 'LANGUAGE')
    expect(item).toHaveProperty('color', '#3178C6')
    expect(item).toHaveProperty('usage_level', 'PRIMARY')
    expect(item).toHaveProperty('icon_url')
    expect(item).not.toHaveProperty('iconUrl')
    expect(item).not.toHaveProperty('usageLevel')
  })

  it('returns technologies ordered by name ascending', async () => {
    const project = await createProject()
    const vue = await createTech({ name: 'Vue', slug: 'vue', type: 'FRAMEWORK' })
    const react = await createTech({ name: 'React', slug: 'react', type: 'FRAMEWORK' })
    await prisma.projectTechnology.createMany({
      data: [
        { projectId: project.id, technologyId: vue.id, usageLevel: 'SECONDARY' },
        { projectId: project.id, technologyId: react.id, usageLevel: 'PRIMARY' },
      ],
    })

    const res = await request(app).get('/api/projects/test-project/technologies')

    expect(res.status).toBe(200)
    expect(res.body.map((t: { name: string }) => t.name)).toEqual(['React', 'Vue'])
  })

  it('returns 404 for non-existent project', async () => {
    const res = await request(app).get('/api/projects/fantasma/technologies')
    expect(res.status).toBe(404)
  })
})

// ─── POST /api/projects/:slug/technologies ────────────────────────────────────

describe('POST /api/projects/:slug/technologies', () => {
  it('links a technology to a project and returns 201', async () => {
    await createProject()
    await createTech()

    const res = await request(app)
      .post('/api/projects/test-project/technologies')
      .send({ technology_name: 'React', usage_level: 'PRIMARY' })

    expect(res.status).toBe(201)
    expect(res.body.message).toMatch(/sucesso/i)
  })

  it('persists the association in the database', async () => {
    const project = await createProject()
    const tech = await createTech()

    await request(app)
      .post('/api/projects/test-project/technologies')
      .send({ technology_name: 'React', usage_level: 'SECONDARY' })

    const link = await prisma.projectTechnology.findUnique({
      where: {
        projectId_technologyId: { projectId: project.id, technologyId: tech.id },
      },
    })
    expect(link).not.toBeNull()
    expect(link?.usageLevel).toBe('SECONDARY')
  })

  it('returns 404 when technology name does not exist', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/technologies')
      .send({ technology_name: 'NonExistent', usage_level: 'PRIMARY' })

    expect(res.status).toBe(404)
  })

  it('returns 409 when technology is already linked to the project', async () => {
    const project = await createProject()
    const tech = await createTech()
    await prisma.projectTechnology.create({
      data: { projectId: project.id, technologyId: tech.id, usageLevel: 'PRIMARY' },
    })

    const res = await request(app)
      .post('/api/projects/test-project/technologies')
      .send({ technology_name: 'React', usage_level: 'SECONDARY' })

    expect(res.status).toBe(409)
  })

  it('returns 400 when technology_name is missing', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/technologies')
      .send({ usage_level: 'PRIMARY' })

    expect(res.status).toBe(400)
  })

  it('returns 400 when usage_level is invalid', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/technologies')
      .send({ technology_name: 'React', usage_level: 'INVALID' })

    expect(res.status).toBe(400)
  })

  it('returns 404 for non-existent project', async () => {
    const res = await request(app)
      .post('/api/projects/fantasma/technologies')
      .send({ technology_name: 'React', usage_level: 'PRIMARY' })

    expect(res.status).toBe(404)
  })
})

// ─── PATCH /api/projects/:slug/technologies/:technology_id ────────────────────

describe('PATCH /api/projects/:slug/technologies/:technology_id', () => {
  it('updates usage_level and returns 200', async () => {
    const project = await createProject()
    const tech = await createTech()
    await prisma.projectTechnology.create({
      data: { projectId: project.id, technologyId: tech.id, usageLevel: 'PRIMARY' },
    })

    const res = await request(app)
      .patch(`/api/projects/test-project/technologies/${tech.id}`)
      .send({ usage_level: 'SECONDARY' })

    expect(res.status).toBe(200)
    expect(res.body.message).toMatch(/sucesso/i)

    const updated = await prisma.projectTechnology.findUnique({
      where: {
        projectId_technologyId: { projectId: project.id, technologyId: tech.id },
      },
    })
    expect(updated?.usageLevel).toBe('SECONDARY')
  })

  it('returns 404 when technology is not linked to the project', async () => {
    await createProject()
    const tech = await createTech()

    const res = await request(app)
      .patch(`/api/projects/test-project/technologies/${tech.id}`)
      .send({ usage_level: 'SECONDARY' })

    expect(res.status).toBe(404)
  })

  it('returns 400 with invalid usage_level', async () => {
    const project = await createProject()
    const tech = await createTech()
    await prisma.projectTechnology.create({
      data: { projectId: project.id, technologyId: tech.id, usageLevel: 'PRIMARY' },
    })

    const res = await request(app)
      .patch(`/api/projects/test-project/technologies/${tech.id}`)
      .send({ usage_level: 'INVALID' })

    expect(res.status).toBe(400)
  })

  it('returns 404 for non-existent project', async () => {
    const tech = await createTech()

    const res = await request(app)
      .patch(`/api/projects/fantasma/technologies/${tech.id}`)
      .send({ usage_level: 'SECONDARY' })

    expect(res.status).toBe(404)
  })
})

// ─── DELETE /api/projects/:slug/technologies/:technology_id ───────────────────

describe('DELETE /api/projects/:slug/technologies/:technology_id', () => {
  it('removes the technology from the project and returns 200', async () => {
    const project = await createProject()
    const tech = await createTech()
    await prisma.projectTechnology.create({
      data: { projectId: project.id, technologyId: tech.id, usageLevel: 'PRIMARY' },
    })

    const res = await request(app).delete(
      `/api/projects/test-project/technologies/${tech.id}`
    )

    expect(res.status).toBe(200)

    const link = await prisma.projectTechnology.findUnique({
      where: {
        projectId_technologyId: { projectId: project.id, technologyId: tech.id },
      },
    })
    expect(link).toBeNull()
  })

  it('does not delete the technology itself from the database', async () => {
    const project = await createProject()
    const tech = await createTech()
    await prisma.projectTechnology.create({
      data: { projectId: project.id, technologyId: tech.id, usageLevel: 'PRIMARY' },
    })

    await request(app).delete(
      `/api/projects/test-project/technologies/${tech.id}`
    )

    const still = await prisma.technology.findUnique({ where: { id: tech.id } })
    expect(still).not.toBeNull()
  })

  it('returns 404 when technology is not linked to the project', async () => {
    await createProject()
    const tech = await createTech()

    const res = await request(app).delete(
      `/api/projects/test-project/technologies/${tech.id}`
    )

    expect(res.status).toBe(404)
  })

  it('returns 404 for non-existent project', async () => {
    const tech = await createTech()

    const res = await request(app).delete(
      `/api/projects/fantasma/technologies/${tech.id}`
    )

    expect(res.status).toBe(404)
  })
})
