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
      status: 'DONE',
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
        status: 'DONE',
        start_date: '2024-06-01',
        end_date: '2024-06-01',
      }).success
    ).toBe(true)
  })

  it('accepts end_date after start_date', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        status: 'DONE',
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

// ─── Status date contract (CREATE) ───────────────────────────────────────────
// Contract:
//   PLANNING  : startDate = null,  endDate = null
//   IN_PROGRESS: startDate != null (auto-set ok), endDate = null
//   PAUSED    : startDate required, endDate = null
//   DONE      : startDate required, endDate != null (auto-set ok)

describe('Status date contract — PLANNING (create)', () => {
  it('accepts PLANNING with no dates', () => {
    expect(CreateProjectSchema.safeParse({ ...validBase, status: 'PLANNING' }).success).toBe(true)
  })

  it('accepts PLANNING with null dates', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase, status: 'PLANNING', start_date: null, end_date: null,
      }).success
    ).toBe(true)
  })

  it('rejects PLANNING with non-null start_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase, status: 'PLANNING', start_date: '2024-01-01',
    })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('start_date')
  })

  it('rejects PLANNING with non-null end_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase, status: 'PLANNING', end_date: '2024-12-31',
    })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
  })

  it('accepts PLANNING with a deadline (independent of status)', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase, status: 'PLANNING', deadline: '2024-12-31',
      }).success
    ).toBe(true)
  })
})

describe('Status date contract — IN_PROGRESS (create)', () => {
  it('accepts IN_PROGRESS with start_date', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase, status: 'IN_PROGRESS', start_date: '2024-01-01',
      }).success
    ).toBe(true)
  })

  it('accepts IN_PROGRESS without start_date (controller auto-sets today)', () => {
    expect(
      CreateProjectSchema.safeParse({ ...validBase, status: 'IN_PROGRESS' }).success
    ).toBe(true)
  })

  it('rejects IN_PROGRESS with explicit null start_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase, status: 'IN_PROGRESS', start_date: null,
    })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('start_date')
  })

  it('rejects IN_PROGRESS with non-null end_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase, status: 'IN_PROGRESS', start_date: '2024-01-01', end_date: '2024-12-31',
    })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
  })
})

describe('Status date contract — PAUSED (create)', () => {
  it('accepts PAUSED with start_date', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase, status: 'PAUSED', start_date: '2024-01-01',
      }).success
    ).toBe(true)
  })

  it('rejects PAUSED without start_date', () => {
    const result = CreateProjectSchema.safeParse({ ...validBase, status: 'PAUSED' })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('start_date')
  })

  it('rejects PAUSED with null start_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase, status: 'PAUSED', start_date: null,
    })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('start_date')
  })

  it('rejects PAUSED with non-null end_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase, status: 'PAUSED', start_date: '2024-01-01', end_date: '2024-12-31',
    })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
  })
})

describe('Status date contract — DONE (create)', () => {
  it('accepts DONE with start_date and end_date', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase, status: 'DONE', start_date: '2024-01-01', end_date: '2024-12-01',
      }).success
    ).toBe(true)
  })

  it('accepts DONE with start_date but no end_date (controller auto-sets today)', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase, status: 'DONE', start_date: '2024-01-01',
      }).success
    ).toBe(true)
  })

  it('rejects DONE without start_date', () => {
    const result = CreateProjectSchema.safeParse({ ...validBase, status: 'DONE' })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('start_date')
  })

  it('rejects DONE with null start_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase, status: 'DONE', start_date: null, end_date: '2024-12-01',
    })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('start_date')
  })

  it('rejects DONE with explicit null end_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase, status: 'DONE', start_date: '2024-01-01', end_date: null,
    })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
  })

  it('rejects DONE when end_date is before start_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase, status: 'DONE', start_date: '2024-06-01', end_date: '2024-01-01',
    })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
  })

  it('accepts DONE with deadline (historical record)', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase, status: 'DONE',
        start_date: '2024-01-01', end_date: '2024-12-01', deadline: '2024-11-30',
      }).success
    ).toBe(true)
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

  it('rejects status: IN_PROGRESS with explicit start_date: null', () => {
    const result = UpdateProjectSchema.safeParse({
      status: 'IN_PROGRESS',
      start_date: null,
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('start_date')
    }
  })

  it('accepts status: IN_PROGRESS without start_date in body (controller checks DB)', () => {
    expect(
      UpdateProjectSchema.safeParse({ status: 'IN_PROGRESS' }).success
    ).toBe(true)
  })

  it('rejects status: PLANNING with a non-null end_date in same body', () => {
    const result = UpdateProjectSchema.safeParse({
      status: 'PLANNING',
      end_date: '2025-01-01',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
    }
  })
})

// ─── Status date contract (UPDATE body-only) ──────────────────────────────────
// Schema validates only what is present in the body.
// Controller validates DB-dependent invariants (e.g. startDate already in DB).

describe('Status date contract — PLANNING (update body)', () => {
  it('rejects PLANNING + non-null start_date in body', () => {
    const result = UpdateProjectSchema.safeParse({ status: 'PLANNING', start_date: '2024-01-01' })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('start_date')
  })

  it('rejects PLANNING + non-null end_date in body', () => {
    const result = UpdateProjectSchema.safeParse({ status: 'PLANNING', end_date: '2024-12-01' })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
  })

  it('accepts PLANNING alone (clears dates in controller)', () => {
    expect(UpdateProjectSchema.safeParse({ status: 'PLANNING' }).success).toBe(true)
  })

  it('accepts PLANNING with null start_date and null end_date', () => {
    expect(
      UpdateProjectSchema.safeParse({ status: 'PLANNING', start_date: null, end_date: null }).success
    ).toBe(true)
  })
})

describe('Status date contract — IN_PROGRESS (update body)', () => {
  it('rejects IN_PROGRESS + explicit null start_date in body', () => {
    const result = UpdateProjectSchema.safeParse({ status: 'IN_PROGRESS', start_date: null })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('start_date')
  })

  it('rejects IN_PROGRESS + non-null end_date in body', () => {
    const result = UpdateProjectSchema.safeParse({
      status: 'IN_PROGRESS', start_date: '2024-01-01', end_date: '2024-12-01',
    })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
  })

  it('accepts IN_PROGRESS alone (startDate may be in DB or auto-set)', () => {
    expect(UpdateProjectSchema.safeParse({ status: 'IN_PROGRESS' }).success).toBe(true)
  })

  it('accepts IN_PROGRESS with a start_date', () => {
    expect(
      UpdateProjectSchema.safeParse({ status: 'IN_PROGRESS', start_date: '2024-01-01' }).success
    ).toBe(true)
  })
})

describe('Status date contract — PAUSED (update body)', () => {
  it('rejects PAUSED + explicit null start_date in body', () => {
    const result = UpdateProjectSchema.safeParse({ status: 'PAUSED', start_date: null })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('start_date')
  })

  it('rejects PAUSED + non-null end_date in body', () => {
    const result = UpdateProjectSchema.safeParse({
      status: 'PAUSED', start_date: '2024-01-01', end_date: '2024-12-01',
    })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
  })

  it('accepts PAUSED alone (startDate may be in DB)', () => {
    expect(UpdateProjectSchema.safeParse({ status: 'PAUSED' }).success).toBe(true)
  })
})

describe('Status date contract — DONE (update body)', () => {
  it('rejects DONE + explicit null start_date in body', () => {
    const result = UpdateProjectSchema.safeParse({ status: 'DONE', start_date: null })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('start_date')
  })

  it('rejects DONE + explicit null end_date in body', () => {
    const result = UpdateProjectSchema.safeParse({ status: 'DONE', end_date: null })
    expect(result.success).toBe(false)
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join('.'))).toContain('end_date')
  })

  it('accepts DONE alone (startDate/endDate may be in DB or auto-set)', () => {
    expect(UpdateProjectSchema.safeParse({ status: 'DONE' }).success).toBe(true)
  })

  it('accepts DONE with both start_date and end_date', () => {
    expect(
      UpdateProjectSchema.safeParse({
        status: 'DONE', start_date: '2024-01-01', end_date: '2024-12-01',
      }).success
    ).toBe(true)
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
        status: 'IN_PROGRESS',
        start_date: '2025-06-01',
        deadline: '2025-06-01',
      }).success
    ).toBe(true)
  })

  it('accepts deadline after start_date', () => {
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        status: 'IN_PROGRESS',
        start_date: '2025-01-01',
        deadline: '2025-12-31',
      }).success
    ).toBe(true)
  })

  it('allows deadline independent of end_date (DONE)', () => {
    // deadline is an estimate; it can differ from end_date
    expect(
      CreateProjectSchema.safeParse({
        ...validBase,
        status: 'DONE',
        start_date: '2025-01-01',
        end_date: '2025-06-01',
        deadline: '2025-12-31',
      }).success
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
      }).success
    ).toBe(true)
  })

  it('rejects when both end_date and deadline are before start_date', () => {
    const result = CreateProjectSchema.safeParse({
      ...validBase,
      status: 'DONE',
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
