import multer from 'multer'

const storage = multer.memoryStorage()

const memUpload = multer({ storage })

function sanitizeFileName(filename) {
  return filename
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9._-]/g, '_')
    .replace(/\s+/g, '_')
    .toLowerCase()
}

export { memUpload, sanitizeFileName }
