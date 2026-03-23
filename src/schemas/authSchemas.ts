import { z } from 'zod'

export const LoginSchema = z.object({
  email: z.string().min(1, 'E-mail é obrigatório.'),
  password: z.string().min(1, 'Senha é obrigatória.'),
})

export const InviteSchema = z.object({
  email: z
    .string()
    .min(1, 'E-mail é obrigatório.')
    .endsWith('@sga.pucminas.br', 'O e-mail deve ser um endereço @sga.pucminas.br.'),
  role_id: z.number({ error: 'role_id deve ser um número.' }).int().positive().optional(),
  is_admin: z.boolean().optional(),
})

export const AcceptInviteSchema = z.object({
  token: z.string().min(1, 'Token é obrigatório.'),
  name: z
    .string()
    .min(1, 'Nome é obrigatório.')
    .regex(/^[^\s]+$/, 'O nome deve ser uma única palavra.'),
  surname: z
    .string()
    .min(1, 'Sobrenome é obrigatório.')
    .regex(/^[^\s]+$/, 'O sobrenome deve ser uma única palavra.'),
  course: z.string().min(1, 'Curso é obrigatório.'),
  entry_date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'entry_date deve estar no formato YYYY-MM-DD.'),
  password: z.string().min(8, 'A senha deve ter ao menos 8 caracteres.'),
  bio: z.string().optional(),
  github_url: z.string().url('github_url deve ser uma URL válida.').optional(),
  instagram_url: z
    .string()
    .url('instagram_url deve ser uma URL válida.')
    .optional(),
  linkedin_url: z.string().url('linkedin_url deve ser uma URL válida.').optional(),
  // TODO: adicionar avatar_url quando upload de imagem estiver implementado (PATCH /members/:id/avatar)
})

export const ForgotPasswordSchema = z.object({
  email: z.string().email('E-mail inválido.'),
})

export const ResetPasswordSchema = z.object({
  token: z.string().min(1, 'Token é obrigatório.'),
  password: z.string().min(8, 'A senha deve ter ao menos 8 caracteres.'),
})

export type LoginInput = z.infer<typeof LoginSchema>
export type InviteInput = z.infer<typeof InviteSchema>
export type AcceptInviteInput = z.infer<typeof AcceptInviteSchema>
export type ForgotPasswordInput = z.infer<typeof ForgotPasswordSchema>
export type ResetPasswordInput = z.infer<typeof ResetPasswordSchema>
