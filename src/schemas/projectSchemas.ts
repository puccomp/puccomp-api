import { z } from 'zod'
import { TechnologyUsageLevel, ProjectStatus, AssetType } from '@prisma/client'

const slugRegex = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const dateRegex = /^\d{4}-\d{2}-\d{2}$/

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
  description: z.string().min(1, 'Descrição é obrigatória.'),
  status: z.nativeEnum(ProjectStatus).optional(),
  is_featured: z.boolean().optional(),
  priority: z
    .number()
    .int()
    .min(0, 'priority deve ser um inteiro não-negativo.')
    .optional(),
  start_date: z
    .string()
    .regex(dateRegex, 'start_date deve estar no formato YYYY-MM-DD.')
    .optional(),
  end_date: z
    .string()
    .regex(dateRegex, 'end_date deve estar no formato YYYY-MM-DD.')
    .optional(),
  is_internal: z.boolean().optional(),
  created_at: z.string().optional(),
  updated_at: z.string().optional(),
}

const validateDateRange = (
  data: { start_date?: string; end_date?: string },
  ctx: z.RefinementCtx
) => {
  if (data.start_date && data.end_date && data.end_date < data.start_date) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'end_date não pode ser anterior a start_date.',
      path: ['end_date'],
    })
  }
}

export const CreateProjectSchema = z
  .object({
    name: z
      .string()
      .min(1, 'Nome é obrigatório.')
      .max(100, 'O nome do projeto deve ter no máximo 100 caracteres.'),
    ...projectBaseShape,
  })
  .superRefine(validateDateRange)

export const UpdateProjectSchema = z
  .object({
    name: z
      .string()
      .min(1, 'Nome é obrigatório.')
      .max(100, 'O nome do projeto deve ter no máximo 100 caracteres.')
      .optional(),
    ...projectBaseShape,
  })
  .partial()
  .refine((data) => Object.values(data).some((v) => v !== undefined), {
    message: 'Nenhum campo para atualizar.',
  })
  .superRefine(validateDateRange)

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
  type: z.nativeEnum(AssetType).optional(),
  caption: z.string().optional(),
  order: z.coerce.number().int().min(0).optional(),
})

export const UpdateAssetSchema = z
  .object({
    caption: z.string().optional(),
    order: z.coerce.number().int().min(0).optional(),
    type: z.nativeEnum(AssetType).optional(),
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

export type CreateProjectInput = z.infer<typeof CreateProjectSchema>
export type UpdateProjectInput = z.infer<typeof UpdateProjectSchema>
export type AddContributorInput = z.infer<typeof AddContributorSchema>
export type AddTechInput = z.infer<typeof AddTechSchema>
export type CreateAssetInput = z.infer<typeof CreateAssetSchema>
export type UpdateAssetInput = z.infer<typeof UpdateAssetSchema>
export type MemberIdParamInput = z.infer<typeof MemberIdParamSchema>
export type TechIdParamInput = z.infer<typeof TechIdParamSchema>
export type AssetIdParamInput = z.infer<typeof AssetIdParamSchema>
