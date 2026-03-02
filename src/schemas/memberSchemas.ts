import { z } from 'zod'
import { MemberStatus } from '@prisma/client'

const memberBaseSchema = z.object({
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
  bio: z.string().optional(),
  course: z.string().min(1, 'Course is required.'),
  avatar_url: z.string().optional(),
  entry_date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'entry_date must be in YYYY-MM-DD format.'),
  exit_date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'exit_date must be in YYYY-MM-DD format.')
    .optional(),
  status: z.nativeEnum(MemberStatus).optional(),
  github_url: z.string().url('github_url must be a valid URL.').optional(),
  instagram_url: z
    .string()
    .url('instagram_url must be a valid URL.')
    .optional(),
  linkedin_url: z.string().url('linkedin_url must be a valid URL.').optional(),
  is_admin: z.boolean().optional(),
  role_id: z.number({ error: 'role_id is required.' }).int().positive(),
})

export const CreateMemberSchema = memberBaseSchema

export const UpdateMemberSchema = memberBaseSchema
  .omit({ email: true })
  .partial()
  .extend({
    password: z
      .string()
      .min(8, 'Password must be at least 8 characters.')
      .optional(),
  })
  .refine((data) => Object.values(data).some((v) => v !== undefined), {
    message: 'No fields to update.',
  })

export type CreateMemberInput = z.infer<typeof CreateMemberSchema>
export type UpdateMemberInput = z.infer<typeof UpdateMemberSchema>
