import multer from 'multer'

const multerErrorHandler = (err, req, res, next) => {
  if (err instanceof multer.MulterError) {
    if (err.code === 'LIMIT_FILE_SIZE')
      return res.status(400).json({ error: 'File size exceeds the limit' })

    if (err.code === 'LIMIT_FILE_COUNT')
      return res.status(400).json({ error: 'Too many files uploaded' })

    if (err.code === 'LIMIT_UNEXPECTED_FILE')
      return res.status(400).json({ error: 'Unexpected file field' })

    return res.status(400).json({ error: `Multer error: ${err.message}` })
  }

  next(err)
}

export { multerErrorHandler }
