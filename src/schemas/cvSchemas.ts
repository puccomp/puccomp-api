import { z } from 'zod'

export const CVSubmissionSchema = z.object({
  fullName: z.string().trim().min(1, 'fullName é obrigatório.'),
  phone: z
    .string()
    .trim()
    .min(1, 'phone é obrigatório.')
    .regex(/^\+?[\d\s\-().]{8,20}$/, 'phone deve ser um número de telefone válido.'),
  course: z.string().trim().min(1, 'course é obrigatório.'),
  period: z.string().trim().min(1, 'period é obrigatório.'),
  linkedIn: z.string().optional(),
  gitHub: z.string().optional(),
})

export const CVQuerySchema = z.object({
  page: z.coerce.number().int().positive().default(1),
  limit: z.coerce.number().int().positive().max(100).default(20),
  sort_by: z.enum(['submittedAt', 'fullname', 'course']).default('submittedAt'),
  order: z.enum(['asc', 'desc']).default('desc'),
  search: z.string().optional(),
  course: z.string().optional(),
  period: z.string().optional(),
  submitted_from: z
    .string()
    .regex(
      /^\d{4}-\d{2}-\d{2}$/,
      'submitted_from deve estar no formato YYYY-MM-DD.'
    )
    .optional(),
  submitted_to: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'submitted_to deve estar no formato YYYY-MM-DD.')
    .optional(),
})

export type CVSubmissionInput = z.infer<typeof CVSubmissionSchema>
export type CVQueryInput = z.infer<typeof CVQuerySchema>
