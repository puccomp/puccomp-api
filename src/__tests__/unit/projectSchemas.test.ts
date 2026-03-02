import { describe, it, expect } from 'vitest'
import {
  CreateProjectSchema,
  UpdateProjectSchema,
} from '../../schemas/projectSchemas.js'

const validBase = {
  name: 'Meu Projeto',
  description: 'Descrição do projeto',
}

describe('CreateProjectSchema', () => {
  it('accepts valid minimal data', () => {
    expect(CreateProjectSchema.safeParse(validBase).success).toBe(true)
  })

  it('accepts valid data with all optional fields', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase,
      slug: 'meu-projeto',
      status: 'PLANNING',
      is_featured: true,
      priority: 5,
      start_date: '2024-01-01',
      end_date: '2024-12-31',
      is_internal: false,
    })
    expect(result.success).toBe(true)
  })

  it('rejects missing description', () => {
    const result = CreateProjectSchema.safeParse({ name: 'Projeto' })
    expect(result.success).toBe(false)
  })

  it('rejects empty name', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase,
      name: '',
    })
    expect(result.success).toBe(false)
  })

  it('rejects end_date before start_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase,
      start_date: '2024-06-01',
      end_date: '2024-01-01',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path.join('.'))
      expect(paths).toContain('end_date')
    }
  })

  it('accepts equal start_date and end_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase,
      start_date: '2024-06-01',
      end_date: '2024-06-01',
    })
    expect(result.success).toBe(true)
  })

  it('accepts end_date after start_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase,
      start_date: '2024-01-01',
      end_date: '2024-12-31',
    })
    expect(result.success).toBe(true)
  })

  it('rejects invalid slug format (uppercase, spaces, special chars)', () => {
    const cases = ['Invalid Slug', 'UPPER', 'with!special', '-starts-with-hyphen']
    for (const slug of cases) {
      const result = CreateProjectSchema.safeParse({ ...validBase, slug })
      expect(result.success, `slug "${slug}" should be rejected`).toBe(false)
    }
  })

  it('accepts valid slug format', () => {
    const cases = ['my-slug', 'slug123', 'abc', 'multi-word-slug']
    for (const slug of cases) {
      const result = CreateProjectSchema.safeParse({ ...validBase, slug })
      expect(result.success, `slug "${slug}" should be accepted`).toBe(true)
    }
  })

  it('rejects invalid status value', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase,
      status: 'INVALID_STATUS',
    })
    expect(result.success).toBe(false)
  })
})

describe('UpdateProjectSchema', () => {
  it('rejects completely empty body', () => {
    expect(UpdateProjectSchema.safeParse({}).success).toBe(false)
  })

  it('accepts body with a single field', () => {
    expect(
      UpdateProjectSchema.safeParse({ description: 'Nova descrição' }).success
    ).toBe(true)
  })

  it('accepts name update', () => {
    expect(
      UpdateProjectSchema.safeParse({ name: 'Novo Nome Livre' }).success
    ).toBe(true)
  })

  it('validates date range even in partial update', () => {
    const result = UpdateProjectSchema.safeParse({
      start_date: '2024-12-01',
      end_date: '2024-01-01',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path.join('.'))
      expect(paths).toContain('end_date')
    }
  })
})
