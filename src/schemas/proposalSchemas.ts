import { z } from 'zod'

export const CreateProposalSchema = z.object({
  fullName: z.string().min(1, 'fullName é obrigatório.'),
  phone: z
    .string()
    .min(1, 'phone é obrigatório.')
    .regex(/^\+?[\d\s\-().]{8,20}$/, 'phone deve ser um número de telefone válido.'),
  projectDescription: z.string().min(1, 'projectDescription é obrigatório.'),
  appFeatures: z.string().optional(),
  visualIdentity: z.string().optional(),
})

export const ProposalQuerySchema = z.object({
  search: z.string().min(1).optional(),
  date_from: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'date_from deve estar no formato YYYY-MM-DD.')
    .optional(),
  date_to: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'date_to deve estar no formato YYYY-MM-DD.')
    .optional(),
  page: z.coerce
    .number({ error: 'page deve ser um número.' })
    .int()
    .positive()
    .default(1),
  limit: z.coerce
    .number({ error: 'limit deve ser um número.' })
    .int()
    .positive()
    .max(100, 'limit não pode exceder 100.')
    .default(20),
  sort_by: z
    .enum(['date', 'name'], {
      error: "sort_by deve ser 'date' ou 'name'.",
    })
    .default('date'),
  order: z
    .enum(['asc', 'desc'], { error: "order deve ser 'asc' ou 'desc'." })
    .default('desc'),
})

export type CreateProposalInput = z.infer<typeof CreateProposalSchema>
export type ProposalQueryInput = z.infer<typeof ProposalQuerySchema>
