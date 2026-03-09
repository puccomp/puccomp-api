import { z } from 'zod'

const BudgetRangeEnum = z.enum(
  ['UNDER_3K', 'FROM_3K_TO_10K', 'FROM_10K_TO_25K', 'FROM_25K_TO_50K', 'ABOVE_50K'],
  {
    error:
      "budget_range deve ser 'UNDER_3K', 'FROM_3K_TO_10K', 'FROM_10K_TO_25K', 'FROM_25K_TO_50K' ou 'ABOVE_50K'.",
  }
)

const ProposalStatusEnum = z.enum(['PENDING', 'ACCEPTED', 'REJECTED'], {
  error: "status deve ser 'PENDING', 'ACCEPTED' ou 'REJECTED'.",
})

const DecisionStatusEnum = z.enum(['ACCEPTED', 'REJECTED'], {
  error: "status deve ser 'ACCEPTED' ou 'REJECTED'.",
})

export const CreateProposalSchema = z.object({
  name: z
    .string()
    .min(1, 'name é obrigatório.')
    .max(100, 'name não pode exceder 100 caracteres.'),
  phone: z
    .string()
    .min(1, 'phone é obrigatório.')
    .regex(/^\+?[\d\s\-().]{8,20}$/, 'phone deve ser um número de telefone válido.'),
  problem_description: z
    .string()
    .min(10, 'problem_description deve ter pelo menos 10 caracteres.')
    .max(3000, 'problem_description não pode exceder 3000 caracteres.'),
  solution_overview: z
    .string()
    .max(3000, 'solution_overview não pode exceder 3000 caracteres.')
    .optional(),
  features: z
    .array(
      z
        .string()
        .min(1, 'Cada feature não pode ser vazia.')
        .max(150, 'Cada feature não pode exceder 150 caracteres.')
    )
    .max(30, 'features não pode ter mais de 30 itens.')
    .optional(),
  visual_identity: z
    .string()
    .max(2000, 'visual_identity não pode exceder 2000 caracteres.')
    .optional(),
  reference_links: z
    .array(z.string().url('Cada item de reference_links deve ser uma URL válida.'))
    .max(10, 'reference_links não pode ter mais de 10 links.')
    .optional(),
  budget_range: BudgetRangeEnum.optional(),
})

export const UpdateProposalSchema = z
  .object({
    status: DecisionStatusEnum.optional(),
    internal_notes: z
      .string()
      .trim()
      .min(1, 'internal_notes não pode ser uma string vazia.')
      .max(5000, 'internal_notes não pode exceder 5000 caracteres.')
      .nullable()
      .optional(),
  })
  .refine((data) => data.status !== undefined || data.internal_notes !== undefined, {
    message: 'Pelo menos um campo deve ser fornecido.',
  })

export const ProposalQuerySchema = z.object({
  search: z.string().min(1).max(100).optional(),
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
