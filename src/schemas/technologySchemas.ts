import { z } from 'zod'
import { TechnologyType } from '@prisma/client'

const validTypes = Object.values(TechnologyType)

const technologyTypeSchema = z
  .string()
  .transform((val) => val.toUpperCase().trim())
  .pipe(
    z.nativeEnum(TechnologyType, {
      error: `type deve ser um dos valores: ${validTypes.join(', ')}.`,
    })
  )

const hexColorRegex = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/

export const TechnologyQuerySchema = z.object({
  search: z.string().optional(),
  type: technologyTypeSchema.optional(),
  exclude_project: z.string().min(1).optional(),
  limit: z.coerce
    .number()
    .int()
    .positive('limit deve ser um número inteiro positivo.')
    .max(100, 'limit não pode ser maior que 100.')
    .optional()
    .default(50),
})

export const CreateTechnologySchema = z.object({
  name: z
    .string()
    .min(1, 'Nome é obrigatório.')
    .max(100, 'O nome deve ter no máximo 100 caracteres.'),
  icon_url: z.string().url('icon_url deve ser uma URL válida.').optional(),
  color: z
    .string()
    .regex(
      hexColorRegex,
      'color deve ser uma cor hex válida (ex: #fff ou #ffffff).'
    )
    .optional(),
  description: z
    .string()
    .max(500, 'A descrição deve ter no máximo 500 caracteres.')
    .optional(),
  type: technologyTypeSchema,
})

export const UpdateTechnologySchema = z
  .object({
    name: z.string().min(1).max(100).optional(),
    icon_url: z
      .string()
      .url('icon_url deve ser uma URL válida.')
      .nullable()
      .optional(),
    color: z
      .string()
      .regex(
        hexColorRegex,
        'color deve ser uma cor hex válida (ex: #fff ou #ffffff).'
      )
      .nullable()
      .optional(),
    description: z
      .string()
      .max(500, 'A descrição deve ter no máximo 500 caracteres.')
      .nullable()
      .optional(),
    type: technologyTypeSchema.optional(),
  })
  .refine((data) => Object.values(data).some((v) => v !== undefined), {
    message: 'Nenhum campo para atualizar.',
  })

export type TechnologyQueryInput = z.infer<typeof TechnologyQuerySchema>
export type CreateTechnologyInput = z.infer<typeof CreateTechnologySchema>
export type UpdateTechnologyInput = z.infer<typeof UpdateTechnologySchema>
