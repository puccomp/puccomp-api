import express, { json } from 'express'
import path, { dirname } from 'path'
import { fileURLToPath } from 'url'

import userRoutes from './routes/userRoutes.js'
import membersRoutes from './routes/membersRoutes.js'
import cvApplicationsRoutes from './routes/cvApplicationsRoutes.js'
import projectProposalRoutes from './routes/projectProposalRoutes.js'

const app = express()
const PORT = process.env.PORT || 8080

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

app.use(json())
app.use(express.static(path.join(__dirname, '../public')))

// Routes
app.get('/', (req, res) =>
  res.sendFile(path.join(__dirname, 'public', 'index.html'))
)
app.use('/uploads', express.static(path.join(__dirname, '../uploads')))

app.use('/api/users', userRoutes)
app.use('/api/members', membersRoutes)
app.use('/api/cv-applications', cvApplicationsRoutes)
app.use('/api/project-proposals', projectProposalRoutes)

app.listen(PORT, () => console.log(`API running on http://localhost:${PORT}`))
