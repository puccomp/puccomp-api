import { NextFunction, Request, Response } from 'express'
import multer from 'multer'

const multerErrorMessages: { [key: string]: string } = {
  LIMIT_FILE_SIZE: 'File size exceeds the limit',
  LIMIT_FILE_COUNT: 'Too many files uploaded',
  LIMIT_UNEXPECTED_FILE: 'Unexpected file field',
}

const multerErrorHandler = (
  err: Error,
  _req: Request,
  res: Response,
  next: NextFunction
) => {
  if (err instanceof multer.MulterError) {
    const message = multerErrorMessages[err.code]
    const errorMsg = message || 'Multer error occurred'
    res.status(400).json({ message: errorMsg })
    return
  }

  next(err)
}

export { multerErrorHandler }
