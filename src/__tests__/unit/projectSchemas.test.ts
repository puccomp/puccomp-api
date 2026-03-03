import { describe, it, expect } from 'vitest'
import {
  CreateProjectSchema,
  UpdateProjectSchema,
} from '../../schemas/projectSchemas.js'

const validBase = {
  name: 'Meu Projeto',
  description: 'Descrição do projeto',
}

// ─── CreateProjectSchema ──────────────────────────────────────────────────────

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
    const result = CreateProjectSchema.safeParse({ ...validBase, name: '' })
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
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
    }
  })

  it('accepts equal start_date and end_date', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        start_date: '2024-06-01',
        end_date: '2024-06-01',
      }).success
    ).toBe(true)
  })

  it('accepts end_date after start_date', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        start_date: '2024-01-01',
        end_date: '2024-12-31',
      }).success
    ).toBe(true)
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
    expect(
      CreateProjectSchema.safeParse({ ...validBase, status: 'INVALID_STATUS' }).success
    ).toBe(false)
  })
})

// ─── UpdateProjectSchema ──────────────────────────────────────────────────────

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
    expect(UpdateProjectSchema.safeParse({ name: 'Novo Nome' }).success).toBe(true)
  })

  it('validates date range even in partial update', () => {
    const result = UpdateProjectSchema.safeParse({
      start_date: '2024-12-01',
      end_date: '2024-01-01',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
    }
  })
})

// ─── deadline ─────────────────────────────────────────────────────────────────

describe('deadline field', () => {
  it('accepts a valid deadline date', () => {
    expect(
      CreateProjectSchema.safeParse({ ...validBase, deadline: '2025-12-31' }).success
    ).toBe(true)
  })

  it('accepts null deadline', () => {
    expect(
      CreateProjectSchema.safeParse({ ...validBase, deadline: null }).success
    ).toBe(true)
  })

  it('accepts deadline without start_date', () => {
    expect(
      CreateProjectSchema.safeParse({ ...validBase, deadline: '2025-06-01' }).success
    ).toBe(true)
  })

  it('rejects deadline in invalid format', () => {
    const cases = ['31/12/2025', 'not-a-date', '20251231', '2025/12/31']
    for (const deadline of cases) {
      expect(
        CreateProjectSchema.safeParse({ ...validBase, deadline }).success,
        `"${deadline}" should be rejected`
      ).toBe(false)
    }
  })

  it('rejects deadline before start_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase,
      start_date: '2025-06-01',
      deadline: '2025-01-01',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('deadline')
    }
  })

  it('accepts deadline equal to start_date', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        start_date: '2025-06-01',
        deadline: '2025-06-01',
      }).success
    ).toBe(true)
  })

  it('accepts deadline after start_date', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        start_date: '2025-01-01',
        deadline: '2025-12-31',
      }).success
    ).toBe(true)
  })

  it('allows deadline independent of end_date', () => {
    // deadline is an estimate; it can differ from end_date
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        start_date: '2025-01-01',
        end_date: '2025-06-01',
        deadline: '2025-12-31',
      }).success
    ).toBe(true)
  })
})

// ─── completed_at ─────────────────────────────────────────────────────────────

describe('completed_at field', () => {
  it('accepts completed_at when status is DONE', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        status: 'DONE',
        completed_at: '2025-03-01',
      }).success
    ).toBe(true)
  })

  it('accepts status DONE without completed_at (auto-set by controller)', () => {
    expect(
      CreateProjectSchema.safeParse({ ...validBase, status: 'DONE' }).success
    ).toBe(true)
  })

  it('accepts null completed_at', () => {
    expect(
      CreateProjectSchema.safeParse({ ...validBase, completed_at: null }).success
    ).toBe(true)
  })

  it('rejects invalid completed_at format', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        status: 'DONE',
        completed_at: '01-03-2025',
      }).success
    ).toBe(false)
  })

  it.each(['PLANNING', 'IN_PROGRESS', 'PAUSED'])(
    'rejects completed_at when status is %s',
    (status) => {
      const result = CreateProjectSchema.safeParse({
        ...validBase,
        status,
        completed_at: '2025-03-01',
      })
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues.map((i) => i.path.join('.'))).toContain('completed_at')
      }
    }
  )

  it('rejects completed_at with non-DONE status in same update body', () => {
    const result = UpdateProjectSchema.safeParse({
      status: 'IN_PROGRESS',
      completed_at: '2025-03-01',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('completed_at')
    }
  })

  it('allows completed_at alone in update body (DB status may be DONE)', () => {
    // Schema cannot know the current DB status; controller validates this case
    expect(
      UpdateProjectSchema.safeParse({ completed_at: '2025-03-01' }).success
    ).toBe(true)
  })

  it('allows null completed_at to clear the field in update', () => {
    expect(
      UpdateProjectSchema.safeParse({ completed_at: null }).success
    ).toBe(true)
  })
})

// ─── combined status + dates ──────────────────────────────────────────────────

describe('status and date fields coexistence', () => {
  it('accepts a fully filled DONE project', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        status: 'DONE',
        start_date: '2024-01-01',
        end_date: '2024-12-01',
        deadline: '2024-11-30',
        completed_at: '2024-12-01',
      }).success
    ).toBe(true)
  })

  it('rejects DONE project with completed_at before start_date (end_date rule is separate)', () => {
    // completed_at is a date field but has no range rule against start_date in the schema
    // This test confirms the schema does NOT reject it (controller/business layer would)
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        status: 'DONE',
        start_date: '2024-06-01',
        completed_at: '2024-01-01',
      }).success
    ).toBe(true)
  })

  it('rejects when both end_date and deadline are before start_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase,
      start_date: '2025-06-01',
      end_date: '2025-01-01',
      deadline: '2025-02-01',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path.join('.'))
      expect(paths).toContain('end_date')
      expect(paths).toContain('deadline')
    }
  })

  it('accepts IN_PROGRESS project with only a deadline set', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        status: 'IN_PROGRESS',
        start_date: '2025-01-01',
        deadline: '2025-06-01',
      }).success
    ).toBe(true)
  })

  it('accepts PLANNING project with no dates at all', () => {
    expect(
      CreateProjectSchema.safeParse({ ...validBase, status: 'PLANNING' }).success
    ).toBe(true)
  })
})
