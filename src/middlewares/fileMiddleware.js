export function fileRequiredMiddleware(req, res, next) {
  if (!req.file) return res.status(400).json({ message: 'A file is required.' })
  next()
}
