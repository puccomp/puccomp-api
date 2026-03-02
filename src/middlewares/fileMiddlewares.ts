import path from 'path'
import { NextFunction, Request, Response } from 'express'

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
