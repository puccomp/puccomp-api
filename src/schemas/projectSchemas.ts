import { z } from 'zod'
import { TechnologyUsageLevel, ProjectStatus, AssetType } from '@prisma/client'

export const ProjectQuerySchema = z.object({
  page: z.coerce
    .number()
    .int()
    .positive('page deve ser um número inteiro positivo.')
    .optional()
    .default(1),
  limit: z.coerce
    .number()
    .int()
    .positive('limit deve ser um número inteiro positivo.')
    .max(100, 'limit não pode ser maior que 100.')
    .optional()
    .default(20),
  sort_by: z
    .enum(['priority', 'created_at', 'name', 'start_date'], {
      error: "sort_by deve ser 'priority', 'created_at', 'name' ou 'start_date'.",
    })
    .optional()
    .default('priority'),
  order: z
    .enum(['asc', 'desc'], { error: "order deve ser 'asc' ou 'desc'." })
    .optional()
    .default('desc'),
  status: z
    .nativeEnum(ProjectStatus, {
      error: `status deve ser um dos valores: ${Object.values(ProjectStatus).join(', ')}.`,
    })
    .optional(),
  is_featured: z
    .enum(['true', 'false'], { error: "is_featured deve ser 'true' ou 'false'." })
    .transform((v) => v === 'true')
    .optional(),
  is_internal: z
    .enum(['true', 'false'], { error: "is_internal deve ser 'true' ou 'false'." })
    .transform((v) => v === 'true')
    .optional(),
})

const slugRegex = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const dateRegex = /^\d{4}-\d{2}-\d{2}$/

// Item 1: validates format AND actual calendar validity (rejects e.g. 2024-02-30)
const isValidCalendarDate = (v: string): boolean => {
  const [year, month, day] = v.split('-').map(Number)
  const d = new Date(year, month - 1, day)
  return (
    d.getFullYear() === year &&
    d.getMonth() === month - 1 &&
    d.getDate() === day
  )
}

const dateField = (fieldName: string) =>
  z
    .string()
    .regex(dateRegex, `${fieldName} deve estar no formato YYYY-MM-DD.`)
    .refine(isValidCalendarDate, {
      message: `${fieldName} contém uma data de calendário inválida.`,
    })
    .nullable()
    .optional()

// Item 2: shared description validator with max length
const descriptionField = z
  .string()
  .min(1, 'Descrição é obrigatória.')
  .max(2000, 'A descrição deve ter no máximo 2000 caracteres.')

// Item 7: description removed from base shape — declared explicitly in each schema
// to make its required/optional status unambiguous at the call site
const projectBaseShape = {
  slug: z
    .string()
    .min(3, 'O slug deve ter ao menos 3 caracteres.')
    .max(60, 'O slug deve ter no máximo 60 caracteres.')
    .regex(
      slugRegex,
      'O slug deve conter apenas letras minúsculas, números e hífens, sem hífens no início ou fim.'
    )
    .optional(),
  // Item 5: custom error message (consistent with other enum fields)
  status: z
    .nativeEnum(ProjectStatus, {
      error: `status deve ser um dos valores: ${Object.values(ProjectStatus).join(', ')}.`,
    })
    .optional(),
  is_featured: z.boolean().optional(),
  priority: z
    .number()
    .int()
    .min(0, 'priority deve ser um inteiro não-negativo.')
    .optional(),
  // Item 1: date fields now validate calendar validity, not just format
  start_date: dateField('start_date'),
  end_date: dateField('end_date'),
  deadline: dateField('deadline'),
  completed_at: dateField('completed_at'),
  is_internal: z.boolean().optional(),
}

// Item 3: resolvedStatus allows callers to pass the effective status (e.g. the
// CREATE default 'PLANNING') so the schema can catch completed_at without status.
// UPDATE passes no resolvedStatus — the runtime controller guard handles that case
// since the schema cannot know the project's current status in the database.
const validateProjectDates = (
  data: {
    status?: string
    start_date?: string | null
    end_date?: string | null
    deadline?: string | null
    completed_at?: string | null
  },
  ctx: z.RefinementCtx,
  resolvedStatus?: string,
) => {
  if (data.start_date && data.end_date && data.end_date < data.start_date) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'end_date não pode ser anterior a start_date.',
      path: ['end_date'],
    })
  }

  if (data.start_date && data.deadline && data.deadline < data.start_date) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'deadline não pode ser anterior a start_date.',
      path: ['deadline'],
    })
  }

  const statusToCheck = resolvedStatus ?? data.status
  if (data.completed_at && statusToCheck && statusToCheck !== 'DONE') {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'completed_at só pode ser definido quando o status é DONE.',
      path: ['completed_at'],
    })
  }
}

export const CreateProjectSchema = z
  .object({
    name: z
      .string()
      .min(1, 'Nome é obrigatório.')
      .max(100, 'O nome do projeto deve ter no máximo 100 caracteres.'),
    description: descriptionField, // Item 7: required in CREATE
    ...projectBaseShape,
  })
  // Item 3: status ?? 'PLANNING' so the schema rejects completed_at when
  // status is omitted (it would default to PLANNING in the controller)
  .superRefine((data, ctx) =>
    validateProjectDates(data, ctx, data.status ?? 'PLANNING'),
  )

export const UpdateProjectSchema = z
  .object({
    name: z
      .string()
      .min(1, 'Nome é obrigatório.')
      .max(100, 'O nome do projeto deve ter no máximo 100 caracteres.')
      .optional(),
    description: descriptionField, // Item 7: same rules; .partial() below makes it optional
    ...projectBaseShape,
  })
  .partial()
  .refine((data) => Object.values(data).some((v) => v !== undefined), {
    message: 'Nenhum campo para atualizar.',
  })
  // Item 3: no resolvedStatus — cannot know DB state; runtime guard handles it
  .superRefine((data, ctx) => validateProjectDates(data, ctx))

export const AddContributorSchema = z.object({
  member_id: z
    .number({ error: 'member_id é obrigatório.' })
    .int()
    .positive('member_id deve ser um número inteiro positivo.'),
})

export const AddTechSchema = z.object({
  technology_name: z.string().min(1, 'technology_name é obrigatório.'),
  usage_level: z.nativeEnum(TechnologyUsageLevel, {
    error: `usage_level deve ser um dos valores: ${Object.values(TechnologyUsageLevel).join(', ')}.`,
  }),
})

export const CreateAssetSchema = z.object({
  type: z
    .nativeEnum(AssetType, {
      error: `type deve ser um dos valores: ${Object.values(AssetType).join(', ')}.`,
    })
    .optional(),
  caption: z.string().max(300, 'A legenda deve ter no máximo 300 caracteres.').optional(), // Item 2
  order: z.coerce
    .number()
    .int()
    .min(0, 'order deve ser um número inteiro não-negativo.')
    .optional(),
})

export const UpdateAssetSchema = z
  .object({
    caption: z.string().max(300, 'A legenda deve ter no máximo 300 caracteres.').optional(), // Item 2
    order: z.coerce
      .number()
      .int()
      .min(0, 'order deve ser um número inteiro não-negativo.')
      .optional(),
  })
  .refine((data) => Object.values(data).some((v) => v !== undefined), {
    message: 'Nenhum campo para atualizar.',
  })

export const MemberIdParamSchema = z.object({
  member_id: z.coerce.number().int().positive('Formato de member_id inválido.'),
})

export const TechIdParamSchema = z.object({
  technology_id: z.coerce
    .number()
    .int()
    .positive('Formato de technology_id inválido.'),
})

export const AssetIdParamSchema = z.object({
  asset_id: z.coerce.number().int().positive('Formato de asset_id inválido.'),
})

export type ProjectQueryInput = z.infer<typeof ProjectQuerySchema>
export type CreateProjectInput = z.infer<typeof CreateProjectSchema>
export type UpdateProjectInput = z.infer<typeof UpdateProjectSchema>
export type AddContributorInput = z.infer<typeof AddContributorSchema>
export type AddTechInput = z.infer<typeof AddTechSchema>
export type CreateAssetInput = z.infer<typeof CreateAssetSchema>
export type UpdateAssetInput = z.infer<typeof UpdateAssetSchema>
export type MemberIdParamInput = z.infer<typeof MemberIdParamSchema>
export type TechIdParamInput = z.infer<typeof TechIdParamSchema>
export type AssetIdParamInput = z.infer<typeof AssetIdParamSchema>
