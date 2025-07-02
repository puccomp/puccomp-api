import { NextFunction, Request, Response } from 'express'

export function fileRequiredMiddleware(
  req: Request,
  res: Response,
  next: NextFunction
): void {
  if (!req.file) {
    res.status(400).json({ message: 'A file is required.' })
    return
  }
  next()
}
