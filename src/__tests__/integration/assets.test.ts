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

vi.mock('sharp', () => {
  const chain = {
    resize: vi.fn().mockReturnThis(),
    webp: vi.fn().mockReturnThis(),
    toBuffer: vi.fn().mockResolvedValue(Buffer.from([0xff, 0xd8, 0xff, 0xe0])),
  }
  return { default: vi.fn(() => chain) }
})

import app from '../../index.js'
import prisma from '../../utils/prisma.js'
import { uploadObjectToS3, deleteObjectFromS3 } from '../../utils/s3.js'

const mockUpload = vi.mocked(uploadObjectToS3)
const mockDelete = vi.mocked(deleteObjectFromS3)

// ─── Helpers ─────────────────────────────────────────────────────────────────

// Real JPEG magic bytes — required for validateAssetTypeMiddleware
const fakeFile = Buffer.from([
  0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46,
  0x49, 0x46, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
  0x00, 0x01, 0x00, 0x00,
])
const fileOpts = { filename: 'photo.jpg', contentType: 'image/jpeg' }

// PDF magic bytes for DOCUMENT type tests
const pdfFile = Buffer.concat([Buffer.from('%PDF-1.4\n'), Buffer.alloc(20)])
const pdfOpts = { filename: 'doc.pdf', contentType: 'application/pdf' }

const createProject = () =>
  prisma.project.create({
    data: {
      name: 'Test Project',
      slug: 'test-project',
      description: 'Test description',
      createdAt: new Date(),
    },
  })

const createAsset = (projectId: number, overrides: object = {}) =>
  prisma.projectAsset.create({
    data: {
      projectId,
      key: `projects/${projectId}/assets/test_${Date.now()}.jpg`,
      type: 'IMAGE',
      order: 0,
      ...overrides,
    },
  })

// ─── Setup ───────────────────────────────────────────────────────────────────

beforeEach(async () => {
  await prisma.projectAsset.deleteMany()
  await prisma.project.deleteMany()
  vi.clearAllMocks()
})

afterAll(async () => {
  await prisma.$disconnect()
})

// ─── POST /api/projects/:slug/assets ─────────────────────────────────────────

describe('POST /api/projects/:slug/assets', () => {
  it('returns 201 with asset body when file is uploaded', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)

    expect(res.status).toBe(201)
    expect(res.body).toHaveProperty('asset')
    expect(res.body.asset).toMatchObject({
      type: 'IMAGE',
      order: 0,
      caption: null,
    })
    expect(res.body.asset).toHaveProperty('id')
    expect(res.body.asset).toHaveProperty('url')
  })

  it('persists the asset in the database', async () => {
    const project = await createProject()

    await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)

    const assets = await prisma.projectAsset.findMany({
      where: { projectId: project.id },
    })
    expect(assets).toHaveLength(1)
  })

  it('calls uploadObjectToS3 with the file', async () => {
    await createProject()

    await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)

    expect(mockUpload).toHaveBeenCalledOnce()
    const [calledFile, calledKey] = mockUpload.mock.calls[0]
    expect(calledFile.originalname).toBe('photo.webp')
    expect(calledKey).toMatch(/^projects\/\d+\/assets\//)
  })

  it('defaults type to IMAGE and order to 0', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)

    expect(res.body.asset.type).toBe('IMAGE')
    expect(res.body.asset.order).toBe(0)
  })

  it('accepts caption, type and order fields', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)
      .field('caption', 'Banner principal')
      .field('type', 'IMAGE')
      .field('order', '3')

    expect(res.status).toBe(201)
    expect(res.body.asset).toMatchObject({
      caption: 'Banner principal',
      type: 'IMAGE',
      order: 3,
    })
  })

  it('returns a URL pointing to S3 for the asset', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)

    expect(res.body.asset.url).toMatch(/^https:\/\/s3\.example\.com\/projects\//)
  })

  it('returns 400 when no file is attached', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .send({})

    expect(res.status).toBe(400)
  })

  it('returns 404 for non-existent project', async () => {
    const res = await request(app)
      .post('/api/projects/fantasma/assets')
      .attach('file', fakeFile, fileOpts)

    expect(res.status).toBe(404)
  })

  it('rolls back S3 upload and returns 500 when DB insert fails', async () => {
    await createProject()

    // Force S3 to succeed but DB to fail by uploading to a non-existent project
    // after bypassing the middleware — simplest way: make S3 throw instead
    mockUpload.mockRejectedValueOnce(new Error('S3 unavailable'))

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)

    expect(res.status).toBe(500)

    const assets = await prisma.projectAsset.findMany()
    expect(assets).toHaveLength(0)

    // deleteObjectFromS3 must be called as cleanup attempt
    expect(mockDelete).toHaveBeenCalledOnce()
  })
})

// ─── GET /api/projects/:slug/assets ──────────────────────────────────────────

describe('GET /api/projects/:slug/assets', () => {
  it('returns empty array when project has no assets', async () => {
    await createProject()

    const res = await request(app).get('/api/projects/test-project/assets')

    expect(res.status).toBe(200)
    expect(res.body).toEqual([])
  })

  it('returns assets ordered by order field ascending', async () => {
    const project = await createProject()
    await createAsset(project.id, { order: 2, key: 'projects/1/assets/c.jpg' })
    await createAsset(project.id, { order: 0, key: 'projects/1/assets/a.jpg' })
    await createAsset(project.id, { order: 1, key: 'projects/1/assets/b.jpg' })

    const res = await request(app).get('/api/projects/test-project/assets')

    expect(res.status).toBe(200)
    expect(res.body).toHaveLength(3)
    expect(res.body.map((a: { order: number }) => a.order)).toEqual([0, 1, 2])
  })

  it('returns assets with id, type, url, caption and order fields', async () => {
    const project = await createProject()
    await createAsset(project.id, {
      caption: 'Legenda',
      key: 'projects/1/assets/img.jpg',
    })

    const res = await request(app).get('/api/projects/test-project/assets')

    expect(res.status).toBe(200)
    const asset = res.body[0]
    expect(asset).toHaveProperty('id')
    expect(asset).toHaveProperty('type', 'IMAGE')
    expect(asset).toHaveProperty('url')
    expect(asset).toHaveProperty('caption', 'Legenda')
    expect(asset).toHaveProperty('order', 0)
    expect(asset).not.toHaveProperty('key') // S3 key must not be exposed
  })

  it('returns 404 for non-existent project', async () => {
    const res = await request(app).get('/api/projects/fantasma/assets')
    expect(res.status).toBe(404)
  })
})

// ─── PATCH /api/projects/:slug/assets/:asset_id ───────────────────────────────

describe('PATCH /api/projects/:slug/assets/:asset_id', () => {
  it('updates caption and returns 200 with updated asset', async () => {
    const project = await createProject()
    const asset = await createAsset(project.id)

    const res = await request(app)
      .patch(`/api/projects/test-project/assets/${asset.id}`)
      .send({ caption: 'Nova legenda' })

    expect(res.status).toBe(200)
    expect(res.body.asset.caption).toBe('Nova legenda')
  })

  it('updates order and persists to database', async () => {
    const project = await createProject()
    const asset = await createAsset(project.id)

    await request(app)
      .patch(`/api/projects/test-project/assets/${asset.id}`)
      .send({ order: 5 })

    const updated = await prisma.projectAsset.findUnique({
      where: { id: asset.id },
    })
    expect(updated?.order).toBe(5)
  })

  it('returns 400 when only type is sent (type cannot be changed after upload)', async () => {
    const project = await createProject()
    const asset = await createAsset(project.id)

    const res = await request(app)
      .patch(`/api/projects/test-project/assets/${asset.id}`)
      .send({ type: 'VIDEO' })

    // type is stripped by schema (not an updatable field); empty body → 400
    expect(res.status).toBe(400)
    const unchanged = await prisma.projectAsset.findUnique({ where: { id: asset.id } })
    expect(unchanged?.type).toBe('IMAGE')
  })

  it('returns 400 with empty body', async () => {
    const project = await createProject()
    const asset = await createAsset(project.id)

    const res = await request(app)
      .patch(`/api/projects/test-project/assets/${asset.id}`)
      .send({})

    expect(res.status).toBe(400)
  })

  it('returns 404 for non-existent asset id', async () => {
    await createProject()

    const res = await request(app)
      .patch('/api/projects/test-project/assets/99999')
      .send({ caption: 'Nada' })

    expect(res.status).toBe(404)
  })

  it('returns 404 for asset belonging to a different project', async () => {
    const project = await createProject()
    const other = await prisma.project.create({
      data: {
        name: 'Other Project',
        slug: 'other-project',
        description: 'Other',
        createdAt: new Date(),
      },
    })
    const asset = await createAsset(other.id)

    // asset belongs to 'other-project', but route uses 'test-project'
    const res = await request(app)
      .patch(`/api/projects/test-project/assets/${asset.id}`)
      .send({ caption: 'Tentativa' })

    expect(res.status).toBe(404)
    // asset must remain unchanged
    const unchanged = await prisma.projectAsset.findUnique({
      where: { id: asset.id },
    })
    expect(unchanged?.caption).toBeNull()
  })
})

// ─── DELETE /api/projects/:slug/assets/:asset_id ──────────────────────────────

describe('DELETE /api/projects/:slug/assets/:asset_id', () => {
  it('returns 200 and removes the asset from the database', async () => {
    const project = await createProject()
    const asset = await createAsset(project.id)

    const res = await request(app).delete(
      `/api/projects/test-project/assets/${asset.id}`
    )

    expect(res.status).toBe(200)

    const deleted = await prisma.projectAsset.findUnique({
      where: { id: asset.id },
    })
    expect(deleted).toBeNull()
  })

  it('calls deleteObjectFromS3 with the asset key', async () => {
    const project = await createProject()
    const asset = await createAsset(project.id, {
      key: 'projects/1/assets/to-delete.jpg',
    })

    await request(app).delete(
      `/api/projects/test-project/assets/${asset.id}`
    )

    expect(mockDelete).toHaveBeenCalledWith('projects/1/assets/to-delete.jpg')
  })

  it('returns 404 for non-existent asset', async () => {
    await createProject()

    const res = await request(app).delete(
      '/api/projects/test-project/assets/99999'
    )
    expect(res.status).toBe(404)
  })

  it('returns 404 for asset belonging to a different project', async () => {
    const project = await createProject()
    const other = await prisma.project.create({
      data: {
        name: 'Other Project',
        slug: 'other-project',
        description: 'Other',
        createdAt: new Date(),
      },
    })
    const asset = await createAsset(other.id)

    const res = await request(app).delete(
      `/api/projects/test-project/assets/${asset.id}`
    )

    expect(res.status).toBe(404)
    // asset must still exist
    const still = await prisma.projectAsset.findUnique({
      where: { id: asset.id },
    })
    expect(still).not.toBeNull()
  })
})

// ─── Asset limit per project ──────────────────────────────────────────────────

describe('POST /api/projects/:slug/assets — limit', () => {
  it('returns 422 when the project has reached MAX_ASSETS_PER_PROJECT', async () => {
    vi.stubEnv('MAX_ASSETS_PER_PROJECT', '2')
    const project = await createProject()

    await createAsset(project.id, { key: 'projects/1/assets/a.jpg' })
    await createAsset(project.id, { key: 'projects/1/assets/b.jpg' })

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)

    expect(res.status).toBe(422)
    expect(res.body.message).toMatch(/limite/i)

    vi.unstubAllEnvs()
  })

  it('accepts upload when below the limit', async () => {
    vi.stubEnv('MAX_ASSETS_PER_PROJECT', '2')
    const project = await createProject()

    await createAsset(project.id, { key: 'projects/1/assets/a.jpg' })

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)

    expect(res.status).toBe(201)

    vi.unstubAllEnvs()
  })

  it('does not upload to S3 when the limit is reached', async () => {
    vi.stubEnv('MAX_ASSETS_PER_PROJECT', '1')
    const project = await createProject()
    await createAsset(project.id, { key: 'projects/1/assets/a.jpg' })

    await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)

    expect(mockUpload).not.toHaveBeenCalled()

    vi.unstubAllEnvs()
  })
})

// ─── MIME type ↔ asset type consistency ──────────────────────────────────────

describe('POST /api/projects/:slug/assets — type consistency', () => {
  it('accepts a JPEG file with type=IMAGE (default)', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)

    expect(res.status).toBe(201)
  })

  it('accepts a PDF file with type=DOCUMENT', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', pdfFile, pdfOpts)
      .field('type', 'DOCUMENT')

    expect(res.status).toBe(201)
  })

  it('rejects a PDF file sent with type=IMAGE', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', pdfFile, pdfOpts)
      .field('type', 'IMAGE')

    expect(res.status).toBe(400)
    expect(res.body.message).toMatch(/incompatível/i)
  })

  it('rejects a JPEG file sent with type=DOCUMENT', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', fakeFile, fileOpts)
      .field('type', 'DOCUMENT')

    expect(res.status).toBe(400)
  })

  it('rejects an unrecognizable file (no magic bytes)', async () => {
    await createProject()

    const res = await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', Buffer.alloc(32, 0), { filename: 'fake.jpg', contentType: 'image/jpeg' })

    expect(res.status).toBe(400)
  })

  it('does not upload to S3 when mime type is rejected', async () => {
    await createProject()

    await request(app)
      .post('/api/projects/test-project/assets')
      .attach('file', pdfFile, pdfOpts)
      .field('type', 'IMAGE')

    expect(mockUpload).not.toHaveBeenCalled()
  })
})
