import { describe, it, expect, vi } from 'vitest'
import type { Request, Response, NextFunction } from 'express'
import {
  validateAssetTypeMiddleware,
  ALLOWED_MIMES,
} from '../../middlewares/fileMiddlewares.js'

// ─── Magic byte buffers ───────────────────────────────────────────────────────

const JPEG = Buffer.from([
  0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46,
  0x49, 0x46, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
  0x00, 0x01, 0x00, 0x00,
])

const PNG = Buffer.from([
  0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
  0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
  0x00, 0x00, 0x00, 0x01,
])

const WEBP = Buffer.concat([
  Buffer.from('RIFF'),
  Buffer.from([0x24, 0x00, 0x00, 0x00]),
  Buffer.from('WEBP'),
  Buffer.from('VP8 '),
  Buffer.from([0x10, 0x00, 0x00, 0x00]),
  Buffer.alloc(16),
])

const PDF = Buffer.concat([Buffer.from('%PDF-1.4\n'), Buffer.alloc(20)])

const MP4 = Buffer.concat([
  Buffer.from([0x00, 0x00, 0x00, 0x20]),
  Buffer.from('ftyp'),
  Buffer.from('mp42'),
  Buffer.from([0x00, 0x00, 0x00, 0x00]),
  Buffer.from('mp42mp41isomiso2'),
])

const UNKNOWN = Buffer.alloc(32, 0)

// ─── Helpers ─────────────────────────────────────────────────────────────────

const makeReq = (buffer: Buffer, type?: string) =>
  ({
    file: { buffer, originalname: 'test' },
    body: type !== undefined ? { type } : {},
  }) as unknown as Request

const makeRes = () => {
  const json = vi.fn()
  const status = vi.fn().mockReturnValue({ json })
  return { status, json } as unknown as Response
}

const run = (buffer: Buffer, type?: string) => {
  const req = makeReq(buffer, type)
  const res = makeRes()
  const next = vi.fn()
  return { promise: validateAssetTypeMiddleware(req, res, next), res, next }
}

// ─── ALLOWED_MIMES shape ─────────────────────────────────────────────────────

describe('ALLOWED_MIMES', () => {
  it('covers all three asset types', () => {
    expect(ALLOWED_MIMES).toHaveProperty('IMAGE')
    expect(ALLOWED_MIMES).toHaveProperty('DOCUMENT')
    expect(ALLOWED_MIMES).toHaveProperty('VIDEO')
  })

  it('IMAGE whitelist contains jpeg, png, webp, gif', () => {
    expect(ALLOWED_MIMES.IMAGE).toEqual(
      expect.arrayContaining(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
    )
  })
})

// ─── IMAGE type ──────────────────────────────────────────────────────────────

describe('validateAssetTypeMiddleware — IMAGE', () => {
  it('accepts a JPEG file', async () => {
    const { promise, next } = run(JPEG, 'IMAGE')
    await promise
    expect(next).toHaveBeenCalledOnce()
  })

  it('accepts a PNG file', async () => {
    const { promise, next } = run(PNG, 'IMAGE')
    await promise
    expect(next).toHaveBeenCalledOnce()
  })

  it('accepts a WebP file', async () => {
    const { promise, next } = run(WEBP, 'IMAGE')
    await promise
    expect(next).toHaveBeenCalledOnce()
  })

  it('rejects a PDF sent as IMAGE', async () => {
    const { promise, res, next } = run(PDF, 'IMAGE')
    await promise
    expect(next).not.toHaveBeenCalled()
    expect(res.status).toHaveBeenCalledWith(400)
  })

  it('rejects an MP4 sent as IMAGE', async () => {
    const { promise, res, next } = run(MP4, 'IMAGE')
    await promise
    expect(next).not.toHaveBeenCalled()
    expect(res.status).toHaveBeenCalledWith(400)
  })

  it('defaults to IMAGE when type is not provided and accepts JPEG', async () => {
    const { promise, next } = run(JPEG) // no type
    await promise
    expect(next).toHaveBeenCalledOnce()
  })

  it('defaults to IMAGE when type is not provided and rejects PDF', async () => {
    const { promise, res, next } = run(PDF) // no type
    await promise
    expect(next).not.toHaveBeenCalled()
    expect(res.status).toHaveBeenCalledWith(400)
  })
})

// ─── DOCUMENT type ───────────────────────────────────────────────────────────

describe('validateAssetTypeMiddleware — DOCUMENT', () => {
  it('accepts a PDF file', async () => {
    const { promise, next } = run(PDF, 'DOCUMENT')
    await promise
    expect(next).toHaveBeenCalledOnce()
  })

  it('rejects a JPEG sent as DOCUMENT', async () => {
    const { promise, res, next } = run(JPEG, 'DOCUMENT')
    await promise
    expect(next).not.toHaveBeenCalled()
    expect(res.status).toHaveBeenCalledWith(400)
  })

  it('rejects an MP4 sent as DOCUMENT', async () => {
    const { promise, res, next } = run(MP4, 'DOCUMENT')
    await promise
    expect(next).not.toHaveBeenCalled()
    expect(res.status).toHaveBeenCalledWith(400)
  })
})

// ─── VIDEO type ──────────────────────────────────────────────────────────────

describe('validateAssetTypeMiddleware — VIDEO', () => {
  it('accepts an MP4 file', async () => {
    const { promise, next } = run(MP4, 'VIDEO')
    await promise
    expect(next).toHaveBeenCalledOnce()
  })

  it('rejects a JPEG sent as VIDEO', async () => {
    const { promise, res, next } = run(JPEG, 'VIDEO')
    await promise
    expect(next).not.toHaveBeenCalled()
    expect(res.status).toHaveBeenCalledWith(400)
  })

  it('rejects a PDF sent as VIDEO', async () => {
    const { promise, res, next } = run(PDF, 'VIDEO')
    await promise
    expect(next).not.toHaveBeenCalled()
    expect(res.status).toHaveBeenCalledWith(400)
  })
})

// ─── Undetectable file ───────────────────────────────────────────────────────

describe('validateAssetTypeMiddleware — undetectable file', () => {
  it('rejects a file with no recognizable magic bytes', async () => {
    const { promise, res, next } = run(UNKNOWN, 'IMAGE')
    await promise
    expect(next).not.toHaveBeenCalled()
    expect(res.status).toHaveBeenCalledWith(400)
  })
})

// ─── Unknown type value ──────────────────────────────────────────────────────

describe('validateAssetTypeMiddleware — unknown type value', () => {
  it('calls next() when type is not in ALLOWED_MIMES (schema will reject it)', async () => {
    const { promise, next } = run(JPEG, 'INVALID_TYPE')
    await promise
    expect(next).toHaveBeenCalledOnce()
  })
})
