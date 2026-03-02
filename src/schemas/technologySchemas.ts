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

export const CreateTechnologySchema = z.object({
  name: z.string().min(1, 'Nome é obrigatório.'),
  icon_url: z.string().optional(),
  type: technologyTypeSchema,
})

export const UpdateTechnologySchema = z.object({
  name: z.string().min(1).optional(),
  icon_url: z.string().optional(),
  type: technologyTypeSchema.optional(),
})

export type CreateTechnologyInput = z.infer<typeof CreateTechnologySchema>
export type UpdateTechnologyInput = z.infer<typeof UpdateTechnologySchema>
