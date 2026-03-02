import { z } from 'zod'
import { TechnologyUsageLevel } from '@prisma/client'

const projectNameRegex = /^[a-zA-Z0-9](?!.*[-_]{2})[-_a-zA-Z0-9]*[a-zA-Z0-9]$/

export const CreateProjectSchema = z.object({
  name: z
    .string()
    .min(3, 'Project name must be at least 3 characters.')
    .max(50, 'Project name must be at most 50 characters.')
    .regex(
      projectNameRegex,
      'Project name can only contain alphanumeric characters, single hyphens, and single underscores, and cannot start or end with a hyphen or underscore. Consecutive hyphens or underscores are not allowed.'
    ),
  description: z.string().min(1, 'Description is required.'),
  created_at: z.string().optional(),
  updated_at: z.string().optional(),
})

export const UpdateProjectSchema = CreateProjectSchema.partial()

export const AddContributorSchema = z.object({
  member_id: z
    .number({ error: 'member_id is required.' })
    .int()
    .positive('member_id must be a positive integer.'),
})

export const AddTechSchema = z.object({
  technology_name: z.string().min(1, 'technology_name is required.'),
  usage_level: z.nativeEnum(TechnologyUsageLevel, {
    error: `usage_level must be one of: ${Object.values(TechnologyUsageLevel).join(', ')}.`,
  }),
})

export const MemberIdParamSchema = z.object({
  member_id: z.coerce.number().int().positive('Invalid member_id format.'),
})

export const TechIdParamSchema = z.object({
  technology_id: z.coerce
    .number()
    .int()
    .positive('Invalid technology_id format.'),
})

export type CreateProjectInput = z.infer<typeof CreateProjectSchema>
export type UpdateProjectInput = z.infer<typeof UpdateProjectSchema>
export type AddContributorInput = z.infer<typeof AddContributorSchema>
export type AddTechInput = z.infer<typeof AddTechSchema>
export type MemberIdParamInput = z.infer<typeof MemberIdParamSchema>
export type TechIdParamInput = z.infer<typeof TechIdParamSchema>
