import { z } from 'zod'
import { TechnologyUsageLevel } from '@prisma/client'

const projectNameRegex = /^[a-zA-Z0-9](?!.*[-_]{2})[-_a-zA-Z0-9]*[a-zA-Z0-9]$/

export const CreateProjectSchema = z.object({
  name: z
    .string()
    .min(3, 'O nome do projeto deve ter ao menos 3 caracteres.')
    .max(50, 'O nome do projeto deve ter no máximo 50 caracteres.')
    .regex(
      projectNameRegex,
      'O nome do projeto pode conter apenas caracteres alfanuméricos, hífens e underscores simples, e não pode começar ou terminar com hífens ou underscores. Hífens ou underscores consecutivos não são permitidos.'
    ),
  description: z.string().min(1, 'Descrição é obrigatória.'),
  created_at: z.string().optional(),
  updated_at: z.string().optional(),
})

export const UpdateProjectSchema = CreateProjectSchema.partial()

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

export const MemberIdParamSchema = z.object({
  member_id: z.coerce.number().int().positive('Formato de member_id inválido.'),
})

export const TechIdParamSchema = z.object({
  technology_id: z.coerce
    .number()
    .int()
    .positive('Formato de technology_id inválido.'),
})

export type CreateProjectInput = z.infer<typeof CreateProjectSchema>
export type UpdateProjectInput = z.infer<typeof UpdateProjectSchema>
export type AddContributorInput = z.infer<typeof AddContributorSchema>
export type AddTechInput = z.infer<typeof AddTechSchema>
export type MemberIdParamInput = z.infer<typeof MemberIdParamSchema>
export type TechIdParamInput = z.infer<typeof TechIdParamSchema>
