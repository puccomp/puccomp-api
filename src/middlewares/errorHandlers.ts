import { NextFunction, Request, Response } from 'express'
import multer from 'multer'

const multerErrorMessages: { [key: string]: string } = {
  LIMIT_FILE_SIZE: 'O tamanho do arquivo excede o limite permitido.',
  LIMIT_FILE_COUNT: 'Muitos arquivos enviados.',
  LIMIT_UNEXPECTED_FILE: 'Campo de arquivo inesperado.',
}

const multerErrorHandler = (
  err: Error,
  _req: Request,
  res: Response,
  next: NextFunction
) => {
  if (err instanceof multer.MulterError) {
    const message = multerErrorMessages[err.code]
    const errorMsg = message || 'Erro ao processar o arquivo.'
    res.status(400).json({ message: errorMsg })
    return
  }

  next(err)
}

export { multerErrorHandler }
