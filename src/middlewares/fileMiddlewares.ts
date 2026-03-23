import path from 'path'
import { NextFunction, Request, Response } from 'express'
import { fileTypeFromBuffer } from 'file-type'

export const ALLOWED_MIMES: Record<string, string[]> = {
  IMAGE: ['image/jpeg', 'image/png', 'image/webp', 'image/gif'],
  DOCUMENT: ['application/pdf'],
  VIDEO: ['video/mp4', 'video/webm', 'video/quicktime'],
}

export function fileRequiredMiddleware(
  req: Request,
  res: Response,
  next: NextFunction
): void {
  if (!req.file) {
    res.status(400).json({ message: 'Um arquivo é obrigatório.' })
    return
  }
  next()
}

const PDF_MAGIC = Buffer.from([0x25, 0x50, 0x44, 0x46]) // %PDF

export async function validateAssetTypeMiddleware(
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  const file = req.file!
  const type = (req.body.type as string | undefined) ?? 'IMAGE'

  const allowedMimes = ALLOWED_MIMES[type]
  if (!allowedMimes) {
    // Unknown type value — let schema validation handle it
    next()
    return
  }

  const detected = await fileTypeFromBuffer(file.buffer)

  if (!detected) {
    res.status(400).json({ message: 'Não foi possível identificar o tipo do arquivo enviado.' })
    return
  }

  if (!allowedMimes.includes(detected.mime)) {
    res.status(400).json({
      message: `Arquivo incompatível com o tipo ${type}. Recebido: ${detected.mime}. Aceitos: ${allowedMimes.join(', ')}.`,
    })
    return
  }

  next()
}

export function validatePdfFileMiddleware(
  req: Request,
  res: Response,
  next: NextFunction
): void {
  const file = req.file!

  if (file.mimetype !== 'application/pdf') {
    res.status(400).json({ message: 'Apenas arquivos PDF são permitidos.' })
    return
  }

  if (path.extname(file.originalname).toLowerCase() !== '.pdf') {
    res.status(400).json({ message: 'O arquivo deve ter extensão .pdf.' })
    return
  }

  if (
    file.buffer.length < PDF_MAGIC.length ||
    !file.buffer.subarray(0, PDF_MAGIC.length).equals(PDF_MAGIC)
  ) {
    res.status(400).json({ message: 'O conteúdo do arquivo não é um PDF válido.' })
    return
  }

  next()
}
