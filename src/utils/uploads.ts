import multer from 'multer'

const storage = multer.memoryStorage()

const memUpload = multer({ storage })

function createUpload(maxSizeMB?: number) {
  const limits = maxSizeMB ? { fileSize: maxSizeMB * 1024 * 1024 } : undefined
  return multer({ storage, limits })
}

function sanitizeFileName(filename: string): string {
  return filename
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9._-]/g, '_')
    .replace(/\s+/g, '_')
    .toLowerCase()
}

export { memUpload, createUpload, sanitizeFileName }
