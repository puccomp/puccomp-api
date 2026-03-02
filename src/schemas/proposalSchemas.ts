import { z } from 'zod'

const BudgetRangeEnum = z.enum(
  ['UNDER_3K', 'FROM_3K_TO_10K', 'FROM_10K_TO_25K', 'FROM_25K_TO_50K', 'ABOVE_50K'],
  {
    error:
      "budget_range deve ser 'UNDER_3K', 'FROM_3K_TO_10K', 'FROM_10K_TO_25K', 'FROM_25K_TO_50K' ou 'ABOVE_50K'.",
  }
)

const ProposalStatusEnum = z.enum(
  ['PENDING', 'UNDER_ANALYSIS', 'ACCEPTED', 'REJECTED'],
  {
    error:
      "status deve ser 'PENDING', 'UNDER_ANALYSIS', 'ACCEPTED' ou 'REJECTED'.",
  }
)

export const CreateProposalSchema = z.object({
  full_name: z.string().min(1, 'full_name é obrigatório.'),
  phone: z
    .string()
    .min(1, 'phone é obrigatório.')
    .regex(/^\+?[\d\s\-().]{8,20}$/, 'phone deve ser um número de telefone válido.'),
  problem_description: z
    .string()
    .min(1, 'problem_description é obrigatório.'),
  solution_overview: z.string().optional(),
  app_features: z.string().optional(),
  visual_identity: z.string().optional(),
  reference_links: z
    .array(z.string().url('Cada item de reference_links deve ser uma URL válida.'))
    .optional(),
  budget_range: BudgetRangeEnum.optional(),
})

export const UpdateProposalSchema = z
  .object({
    status: ProposalStatusEnum.optional(),
    internal_notes: z.string().optional(),
  })
  .refine((data) => data.status !== undefined || data.internal_notes !== undefined, {
    message: 'Pelo menos um campo deve ser fornecido.',
  })

export const ProposalQuerySchema = z.object({
  search: z.string().min(1).optional(),
  status: ProposalStatusEnum.optional(),
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
    .enum(['created_at', 'name', 'status'], {
      error: "sort_by deve ser 'created_at', 'name' ou 'status'.",
    })
    .default('created_at'),
  order: z
    .enum(['asc', 'desc'], { error: "order deve ser 'asc' ou 'desc'." })
    .default('desc'),
})

export type CreateProposalInput = z.infer<typeof CreateProposalSchema>
export type UpdateProposalInput = z.infer<typeof UpdateProposalSchema>
export type ProposalQueryInput = z.infer<typeof ProposalQuerySchema>
