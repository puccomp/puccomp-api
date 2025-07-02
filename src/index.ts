import express, { json } from 'express'
import cors from 'cors'
import path, { dirname } from 'path'
import { fileURLToPath } from 'url'

// ROUTES
import membersRoutes from './routes/membersRoutes.js'
import projectProposalRoutes from './routes/projectProposalRoutes.js'
import projectsRoutes from './routes/projectsRoutes.js'
import technologiesRoutes from './routes/technologiesRoutes.js'
import rolesRoutes from './routes/rolesRoutes.js'
import cvApplications from './routes/cvApplications.js'
import memoriesRoutes from './routes/memoriesRoutes.js'

const app = express()
const PORT = process.env.PORT || 8080

export const BASE_URL = process.env.BASE_URL || `http://localhost:${PORT}`
const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

app.use(json())
app.use(
  cors({
    origin: FRONTEND_URL,
    methods: ['GET', 'POST'],
    credentials: false,
  })
)
app.use(express.static(path.join(__dirname, '../public')))

// ROUTES
app.get('/', (_req, res) =>
  res.sendFile(path.join(__dirname, 'public', 'index.html'))
)
app.use('/api/members', membersRoutes)
app.use('/api/project-proposals', projectProposalRoutes)
app.use('/api/projects', projectsRoutes)
app.use('/api/technologies', technologiesRoutes)
app.use('/api/roles', rolesRoutes)
app.use('/api/cv-applications', cvApplications)
app.use('/api/memories', memoriesRoutes)

app.listen(PORT, () => console.log(`API running on port ${PORT}`))