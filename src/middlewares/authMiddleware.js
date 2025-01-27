import jwt from 'jsonwebtoken'

function authMiddleware(req, res, next) {
  const authHeader = req.headers['authorization']
  const token = authHeader && authHeader.split(' ')[1]

  if (!token) return res.status(401).json({ message: 'No token provided' })

  jwt.verify(token, process.env.JWT_SECRET, (error, decoded) => {
    if (error) return res.status(401).json({ message: 'Invalid token' })
    if (!decoded.is_active)
      return res.status(403).json({ message: 'Member is inactive' })
    req.user = decoded
    next()
  })
}

export default authMiddleware
