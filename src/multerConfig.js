import multer from 'multer'
import fs from 'fs'
import path from 'path'

const RESUMES_DIR = path.join(process.cwd(), 'uploads/resumes')
const PROJECTS_DIR = path.join(process.cwd(), 'uploads/projects')

if (!fs.existsSync(RESUMES_DIR)) {
  fs.mkdirSync(RESUMES_DIR, { recursive: true })
  console.log(`${RESUMES_DIR} directory has been created`)
}

if (!fs.existsSync(PROJECTS_DIR)) {
  fs.mkdirSync(PROJECTS_DIR, { recursive: true })
  console.log(`${PROJECTS_DIR} directory has been created`)
}

const resumeStorage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, RESUMES_DIR)
  },
  filename: (req, file, cb) => {
    const uniqueName = `${Date.now()}-${file.originalname}`
    cb(null, uniqueName)
  },
})

const uploadResumeMulter = multer({ storage: resumeStorage })

const uploadProjectMulter = multer({ storage: multer.memoryStorage() })

export { uploadResumeMulter, uploadProjectMulter }
