import { z } from 'zod'

export const CreateProposalSchema = z.object({
  fullName: z.string().min(1, 'fullName is required.'),
  phone: z
    .string()
    .min(1, 'phone is required.')
    .regex(/^\+?[\d\s\-().]{8,20}$/, 'phone must be a valid phone number.'),
  projectDescription: z.string().min(1, 'projectDescription is required.'),
  appFeatures: z.string().optional(),
  visualIdentity: z.string().optional(),
})

export type CreateProposalInput = z.infer<typeof CreateProposalSchema>
