import { z } from 'zod'

export const CreateRoleSchema = z.object({
  name: z.string().min(1, 'Name is required.'),
  description: z.string().optional(),
  level: z
    .number({ error: 'Level is required.' })
    .int()
    .min(0, 'Level must be 0 or greater.'),
})

export const UpdateRoleSchema = CreateRoleSchema.partial()

export type CreateRoleInput = z.infer<typeof CreateRoleSchema>
export type UpdateRoleInput = z.infer<typeof UpdateRoleSchema>
