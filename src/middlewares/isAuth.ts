import { NextFunction, Request, Response } from 'express'
import jwt from 'jsonwebtoken'

interface TokenPayload {
  id: number
  is_active: boolean
  is_admin: boolean
}

declare global {
  namespace Express {
    export interface Request {
      user?: TokenPayload
    }
  }
}

function isAuth(req: Request, res: Response, next: NextFunction): void {
  const authHeader = req.headers['authorization']
  const token = authHeader && authHeader.split(' ')[1]

  if (!token) {
    res.status(401).json({ message: 'Nenhum token fornecido.' })
    return
  }

  try {
    const decoded = jwt.verify(
      token,
      process.env.JWT_SECRET_KEY!
    ) as TokenPayload
    if (!decoded.is_active) {
      res.status(403).json({ message: 'Membro inativo.' })
      return
    }
    req.user = decoded
    next()
  } catch (error) {
    if (error instanceof jwt.TokenExpiredError)
      res.status(401).json({ message: 'Token expirado.' })
    else if (error instanceof jwt.JsonWebTokenError)
      res.status(401).json({ message: 'Token inválido.' })
    else
      res
        .status(500)
        .json({ message: 'Falha na autenticação devido a um erro no servidor.' })
  }
}

export { TokenPayload }
export default isAuth
