import { z } from 'zod'

export const CreateRoleSchema = z.object({
  name: z.string().min(1, 'Nome é obrigatório.'),
  description: z.string().optional(),
  level: z
    .number({ error: 'Nível é obrigatório.' })
    .int()
    .min(0, 'O nível deve ser 0 ou maior.'),
})

export const UpdateRoleSchema = CreateRoleSchema.partial()

export type CreateRoleInput = z.infer<typeof CreateRoleSchema>
export type UpdateRoleInput = z.infer<typeof UpdateRoleSchema>
