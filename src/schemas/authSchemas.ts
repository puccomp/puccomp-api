import { z } from 'zod'

export const LoginSchema = z.object({
  email: z.string().min(1, 'Email is required.'),
  password: z.string().min(1, 'Password is required.'),
})

export const InviteSchema = z.object({
  email: z
    .string()
    .min(1, 'Email is required.')
    .endsWith('@sga.pucminas.br', 'Email must be a @sga.pucminas.br address.'),
  name: z
    .string()
    .min(1, 'Name is required.')
    .regex(/^[^\s]+$/, 'Name must be a single word.'),
  surname: z
    .string()
    .min(1, 'Surname is required.')
    .regex(/^[^\s]+$/, 'Surname must be a single word.'),
  course: z.string().min(1, 'Course is required.'),
  role_id: z.number({ error: 'role_id is required.' }).int().positive(),
  entry_date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'entry_date must be in YYYY-MM-DD format.'),
  bio: z.string().optional(),
  github_url: z.string().url('github_url must be a valid URL.').optional(),
  instagram_url: z
    .string()
    .url('instagram_url must be a valid URL.')
    .optional(),
  linkedin_url: z.string().url('linkedin_url must be a valid URL.').optional(),
  is_admin: z.boolean().optional(),
})

export const AcceptInviteSchema = z.object({
  token: z.string().min(1, 'Token is required.'),
  password: z.string().min(8, 'Password must be at least 8 characters.'),
})

export type LoginInput = z.infer<typeof LoginSchema>
export type InviteInput = z.infer<typeof InviteSchema>
export type AcceptInviteInput = z.infer<typeof AcceptInviteSchema>
