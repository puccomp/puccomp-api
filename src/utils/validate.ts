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
    const issue = result.error.issues[0]
    const field = issue.path.join('.')

    let message: string
    if (!field) {
      message = issue.message
    } else if (issue.code === 'invalid_type') {
      const isUndefined =
        issue.message.includes('undefined') ||
        issue.message.includes('indefinido')
      message = isUndefined
        ? `O campo '${field}' é obrigatório.`
        : `O campo '${field}' recebeu um valor inválido.`
    } else if (!issue.message.toLowerCase().includes(field.toLowerCase())) {
      message = `'${field}': ${issue.message}`
    } else {
      message = issue.message
    }

    res.status(400).json({ message })
    return null
  }
  return result.data
}
