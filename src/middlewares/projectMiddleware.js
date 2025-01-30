import db from '../db/db.js'

function findProjectByName(name) {
  const query = db.prepare('SELECT * FROM project WHERE name = ?')
  return query.get(name)
}

export function projectExistsMiddleware(req, res, next) {
  const { project_name } = req.params
  const project = findProjectByName(project_name)

  if (!project) return res.status(404).json({ message: 'Project not found.' })

  req.project = project
  next()
}
