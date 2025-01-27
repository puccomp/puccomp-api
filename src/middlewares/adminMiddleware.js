function adminMiddleware(req, res, next) {
  if (!req.user?.is_admin)
    return res.status(403).send({ message: 'Access denied. Admins only.' })
  next()
}

export default adminMiddleware
