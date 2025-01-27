import express from 'express'
import db from '../db.js'

// MIDDLEWARES
import authMiddleware from '../middlewares/authMiddleware.js'

const router = express.Router()

function validateProjectName(name) {
  const projectNameRegex = /^[a-zA-Z0-9]([-_a-zA-Z0-9]*[a-zA-Z0-9])?$/

  if (!name || name.length < 1 || name.length > 100)
    return {
      valid: false,
      message: 'Project name must be between 1 and 100 characters.',
    }

  if (!projectNameRegex.test(name))
    return {
      valid: false,
      message:
        'Project name can only contain alphanumeric characters, hyphens, and underscores, and cannot start or end with a hyphen or underscore.',
    }

  return { valid: true }
}

function findProjectByName(name) {
  const query = db.prepare(`
    SELECT * FROM project 
    WHERE name = ?
  `)
  return query.get(name)
}

// SAVE
router.post('/', authMiddleware, (req, res) => {
  const { name, description, image_url } = req.body

  if (!name || !description)
    return res
      .status(400)
      .json({ message: 'Name and description are required.' })

  const nameValidation = validateProjectName(name)
  if (!nameValidation.valid)
    return res.status(400).json({ message: nameValidation.message })

  try {
    const insertProjectQuery = db.prepare(`
        INSERT INTO project (name, description, image_url, created_at, updated_at)
        VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE)
      `)

    const result = insertProjectQuery.run(name, description, image_url || null)

    res.status(201).json({
      message: 'Project created successfully.',
      project_id: result.lastInsertRowid,
    })
  } catch (err) {
    if (err.code === 'SQLITE_CONSTRAINT_UNIQUE')
      return res
        .status(409)
        .json({ message: 'A project with this name already exists.' })
    console.error(err.message)
    res.status(500).json({ message: 'Failed to create project.' })
  }
})

// FIND ALL
router.get('/', (req, res) => {
  try {
    const getProjectsQuery = db.prepare('SELECT * FROM project')
    const projects = getProjectsQuery.all()

    const baseUrl = `${req.protocol}://${req.get('host')}`
    res.json(
      projects.map((project) => ({
        ...project,
        contributors_url: `${baseUrl}/projects/${project.name}/contributors`,
        technologies_url: `${baseUrl}/projects/${project.name}/technologies`,
      }))
    )
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to fetch projects.' })
  }
})

// FIND BY NAME
router.get('/:name', (req, res) => {
  const { name } = req.params

  try {
    const project = findProjectByName(name)

    if (!project) return res.status(404).json({ message: 'Project not found.' })

    const baseUrl = `${req.protocol}://${req.get('host')}`
    res.json({
      ...project,
      contributors_url: `${baseUrl}/projects/${project.name}/contributors`,
      technologies_url: `${baseUrl}/projects/${project.name}/technologies`,
    })
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to fetch the project.' })
  }
})

// UPDATE BY NAME
router.put('/:name', authMiddleware, (req, res) => {
  const { name } = req.params
  const { newName, description, image_url } = req.body

  if (newName) {
    const nameValidation = validateProjectName(newName)
    if (!nameValidation.valid)
      return res.status(400).json({ message: nameValidation.message })
  }

  try {
    const project = findProjectByName(name)

    if (!project) return res.status(404).json({ message: 'Project not found.' })

    const updateProjectQuery = db.prepare(`
        UPDATE project 
        SET 
          name = COALESCE(?, name),
          description = COALESCE(?, description),
          image_url = COALESCE(?, image_url),
          updated_at = CURRENT_DATE
        WHERE name = ?
      `)

    updateProjectQuery.run(newName, description, image_url, name)

    res.json({ message: 'Project updated successfully.' })
  } catch (err) {
    if (err.code === 'SQLITE_CONSTRAINT_UNIQUE')
      return res
        .status(409)
        .json({ message: 'A project with this name already exists.' })

    console.error(err.message)
    res.status(500).json({ message: 'Failed to update the project.' })
  }
})

// DELETE BY NAME
router.delete('/:name', authMiddleware, (req, res) => {
  const { name } = req.params

  try {
    const project = findProjectByName(name)

    if (!project) return res.status(404).json({ message: 'Project not found.' })

    const deleteProjectQuery = db.prepare('DELETE FROM project WHERE name = ?')
    deleteProjectQuery.run(name)

    res.json({ message: 'Project deleted successfully.' })
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to delete the project.' })
  }
})

// -------- CONTRIBUTORS --------

// ASSOCIATE MEMBER <--> PROJECT
router.post('/:name/contributors', authMiddleware, (req, res) => {
  const { name } = req.params
  const { member_id } = req.body

  if (!member_id)
    return res.status(400).json({ message: 'Member ID is required.' })

  try {
    const project = findProjectByName(name)

    if (!project) return res.status(404).json({ message: 'Project not found.' })

    const findMemberQuery = db.prepare('SELECT id FROM member WHERE id = ?')
    const member = findMemberQuery.get(member_id)

    if (!member) return res.status(404).json({ message: 'Member not found.' })

    const addContributorQuery = db.prepare(`
      INSERT INTO contributor (member_id, project_id)
      VALUES (?, ?)
    `)
    addContributorQuery.run(member_id, project.id)

    res.status(201).json({
      message: 'Contributor added successfully to the project.',
      project_id: project.id,
      member_id: member.id,
    })
  } catch (err) {
    if (err.code === 'SQLITE_CONSTRAINT')
      return res
        .status(409)
        .json({ message: 'Member is already a contributor to this project.' })

    console.error(err.message)
    res.status(500).json({ message: 'Failed to add contributor.' })
  }
})

// FIND ALL CONTRIBUTORS
router.get('/:name/contributors', (req, res) => {
  const { name } = req.params

  try {
    const project = findProjectByName(name)

    if (!project) return res.status(404).json({ message: 'Project not found.' })

    const getContributorsQuery = db.prepare(`
      SELECT 
        m.id AS member_id, 
        m.name, 
        m.surname, 
        m.github_url, 
        m.linkedin_url, 
        m.instagram_url, 
        m.avatar_url,
        m.is_active
      FROM contributor c
      JOIN member m ON c.member_id = m.id
      WHERE c.project_id = ?
    `)
    const contributors = getContributorsQuery.all(project.id)

    if (contributors.length === 0)
      return res
        .status(404)
        .json({ message: 'No contributors found for this project.' })

    const baseUrl = `${req.protocol}://${req.get('host')}`
    res.json(
      contributors.map((contributor) => ({
        ...contributor,
        is_active: Boolean(contributor.is_active),
        member_url: `${baseUrl}/api/members/${contributor.member_id}`,
      }))
    )
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to fetch contributors.' })
  }
})

// DELETE A CONTRIBUTOR
router.delete('/:name/contributors/:member_id', authMiddleware, (req, res) => {
  const { name, member_id } = req.params

  try {
    const project = findProjectByName(name)

    if (!project) return res.status(404).json({ message: 'Project not found.' })

    const findContributorQuery = db.prepare(`
      SELECT * 
      FROM contributor
      WHERE member_id = ? AND project_id = ?
    `)
    const contributor = findContributorQuery.get(member_id, project.id)

    if (!contributor)
      return res
        .status(404)
        .json({ message: 'Contributor not found in this project.' })

    const deleteContributorQuery = db.prepare(`
      DELETE FROM contributor
      WHERE member_id = ? AND project_id = ?
    `)
    deleteContributorQuery.run(member_id, project.id)

    res.json({
      message: 'Contributor removed successfully from the project.',
      project_id: project.id,
      member_id: parseInt(member_id),
    })
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to remove contributor.' })
  }
})

// -------- TECHNOLOGIES --------

// ASSOCIATE TECHNOLOGY <--> PROJECT
router.post('/:name/technologies', authMiddleware, (req, res) => {
  const { name } = req.params
  const { technology_name, usage_level } = req.body

  if (!technology_name || !usage_level)
    return res
      .status(400)
      .json({ message: 'Technology name and usage level are required.' })

  const validUsageLevels = ['primary', 'secondary', 'experimental']
  if (!validUsageLevels.includes(usage_level))
    return res.status(400).json({
      message: `Invalid usage level. Must be one of: ${validUsageLevels.join(
        ', '
      )}`,
    })

  try {
    const project = findProjectByName(name)

    if (!project) return res.status(404).json({ message: 'Project not found.' })

    const findTechnologyQuery = db.prepare(
      'SELECT id FROM technology WHERE name = ?'
    )
    const technology = findTechnologyQuery.get(technology_name)

    if (!technology)
      return res.status(404).json({ message: 'Technology not found.' })

    const checkAssociationQuery = db.prepare(`
      SELECT * FROM project_technology
      WHERE project_id = ? AND technology_id = ?
    `)
    const existingAssociation = checkAssociationQuery.get(
      project.id,
      technology.id
    )

    if (existingAssociation) {
      return res.status(409).json({
        message: 'Technology is already associated with this project.',
        project_id: project.id,
        technology_name,
        existing_usage_level: existingAssociation.usage_level,
      })
    }

    const addProjectTechnologyQuery = db.prepare(`
      INSERT INTO project_technology (project_id, technology_id, usage_level)
      VALUES (?, ?, ?)
    `)
    addProjectTechnologyQuery.run(project.id, technology.id, usage_level)

    res.status(201).json({
      message: 'Technology added successfully to the project.',
      project_id: project.id,
      technology_name,
      usage_level,
    })
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to add technology to project.' })
  }
})

// FIND ALL PROJECT TECHNOLOGIES
router.get('/:name/technologies', (req, res) => {
  const { name } = req.params

  try {
    const project = findProjectByName(name)

    if (!project) return res.status(404).json({ message: 'Project not found.' })

    const getTechnologiesQuery = db.prepare(`
      SELECT t.id, t.name, t.icon_url, pt.usage_level
      FROM project_technology pt
      JOIN technology t ON pt.technology_id = t.id
      WHERE pt.project_id = ?
    `)
    const technologies = getTechnologiesQuery.all(project.id)

    res.json(technologies)
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to fetch technologies.' })
  }
})

export default router
