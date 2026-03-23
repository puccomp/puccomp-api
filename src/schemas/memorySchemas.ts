import { z } from 'zod'

export const MemoryQuerySchema = z.object({
  sort_by: z.enum(['date', 'title', 'id']).default('date'),
  order: z.enum(['asc', 'desc']).default('desc'),
  page: z.coerce.number().int().positive().default(1),
  limit: z.coerce.number().int().positive().max(100).default(20),
})

const dateField = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, 'date deve estar no formato YYYY-MM-DD.')
  .optional()

export const CreateMemorySchema = z.object({
  title: z.string().optional(),
  description: z.string().optional(),
  date: dateField,
})

export const UpdateMemorySchema = z
  .object({
    title: z.string().optional(),
    description: z.string().optional(),
    date: dateField,
  })
  .refine((data) => Object.values(data).some((v) => v !== undefined), {
    message: 'Nenhum campo para atualizar.',
  })

export type MemoryQueryInput = z.infer<typeof MemoryQuerySchema>
export type CreateMemoryInput = z.infer<typeof CreateMemorySchema>
export type UpdateMemoryInput = z.infer<typeof UpdateMemorySchema>
