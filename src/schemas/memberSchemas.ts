import { z } from 'zod'
import { MemberStatus } from '@prisma/client'

const memberBaseSchema = z.object({
  email: z
    .string()
    .min(1, 'E-mail é obrigatório.')
    .endsWith('@sga.pucminas.br', 'O e-mail deve ser um endereço @sga.pucminas.br.'),
  name: z
    .string()
    .min(1, 'Nome é obrigatório.')
    .regex(/^[^\s]+$/, 'O nome deve ser uma única palavra.'),
  surname: z
    .string()
    .min(1, 'Sobrenome é obrigatório.')
    .regex(/^[^\s]+$/, 'O sobrenome deve ser uma única palavra.'),
  bio: z.string().optional(),
  course: z.string().min(1, 'Curso é obrigatório.'),
  avatar_url: z.string().optional(),
  entry_date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'entry_date deve estar no formato YYYY-MM-DD.'),
  exit_date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'exit_date deve estar no formato YYYY-MM-DD.')
    .optional(),
  status: z.nativeEnum(MemberStatus).optional(),
  github_url: z.string().url('github_url deve ser uma URL válida.').optional(),
  instagram_url: z
    .string()
    .url('instagram_url deve ser uma URL válida.')
    .optional(),
  linkedin_url: z.string().url('linkedin_url deve ser uma URL válida.').optional(),
  is_admin: z.boolean().optional(),
  role_id: z.number({ error: 'role_id é obrigatório.' }).int().positive(),
})

export const CreateMemberSchema = memberBaseSchema.superRefine((data, ctx) => {
  const effectiveStatus = data.status ?? 'PENDING'
  if (effectiveStatus === 'INACTIVE' && !data.exit_date) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Membros inativos devem ter uma data de saída.',
      path: ['exit_date'],
    })
  }
  if (effectiveStatus !== 'INACTIVE' && data.exit_date) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Membros ativos ou pendentes não podem ter data de saída.',
      path: ['exit_date'],
    })
  }
})

export const UpdateMemberSchema = memberBaseSchema
  .omit({ email: true })
  .partial()
  .extend({
    password: z
      .string()
      .min(8, 'A senha deve ter ao menos 8 caracteres.')
      .optional(),
    exit_date: z
      .string()
      .regex(/^\d{4}-\d{2}-\d{2}$/, 'exit_date deve estar no formato YYYY-MM-DD.')
      .nullable()
      .optional(),
  })
  .refine((data) => Object.values(data).some((v) => v !== undefined), {
    message: 'Nenhum campo para atualizar.',
  })

export const MemberQuerySchema = z.object({
  status: z.nativeEnum(MemberStatus).optional(),
  role_id: z.coerce.number().int().positive().optional(),
  is_admin: z
    .enum(['true', 'false'], { error: 'is_admin deve ser true ou false.' })
    .transform((v) => v === 'true')
    .optional(),
  search: z.string().min(1).optional(),
  course: z.string().min(1).optional(),
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
    .enum(['name', 'entry_date', 'exit_date'], {
      error: "sort_by deve ser 'name', 'entry_date' ou 'exit_date'.",
    })
    .default('name'),
  order: z
    .enum(['asc', 'desc'], { error: "order deve ser 'asc' ou 'desc'." })
    .default('asc'),
})

export type CreateMemberInput = z.infer<typeof CreateMemberSchema>
export type UpdateMemberInput = z.infer<typeof UpdateMemberSchema>
export type MemberQueryInput = z.infer<typeof MemberQuerySchema>
