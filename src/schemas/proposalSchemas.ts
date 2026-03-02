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

export type CreateProposalInput = z.infer<typeof CreateProposalSchema>
