import { z, ZodSchema } from 'zod'
import { Response } from 'express'

export const IdParamSchema = z.object({
  id: z.coerce.number().int().positive('Formato de id inválido.'),
})

export function validate<T>(
  schema: ZodSchema<T>,
  data: unknown,
  res: Response
): T | null {
  const result = schema.safeParse(data)
  if (!result.success) {
    res.status(400).json({ message: result.error.issues[0].message })
    return null
  }
  return result.data
}
