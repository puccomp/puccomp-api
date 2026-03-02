import { z } from 'zod'

export const CVSubmissionSchema = z.object({
  fullName: z.string().trim().min(1, 'fullName is required.'),
  phone: z
    .string()
    .trim()
    .min(1, 'phone is required.')
    .regex(/^\+?[\d\s\-().]{8,20}$/, 'phone must be a valid phone number.'),
  course: z.string().trim().min(1, 'course is required.'),
  period: z.string().trim().min(1, 'period is required.'),
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
      'submitted_from must be in YYYY-MM-DD format.'
    )
    .optional(),
  submitted_to: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'submitted_to must be in YYYY-MM-DD format.')
    .optional(),
})

export type CVSubmissionInput = z.infer<typeof CVSubmissionSchema>
export type CVQueryInput = z.infer<typeof CVQuerySchema>
