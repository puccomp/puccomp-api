import { NextFunction, Request, Response } from 'express'

function isAdmin(req: Request, res: Response, next: NextFunction): void {
  if (!req.user?.is_admin) {
    res.status(403).send({ message: 'Acesso negado. Apenas administradores.' })
    return
  }
  next()
}

export default isAdmin
