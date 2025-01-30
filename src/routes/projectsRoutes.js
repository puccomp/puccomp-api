import express from 'express'
import db from '../db/db.js'

// MIDDLEWARES
import authMiddleware from '../middlewares/authMiddleware.js'
import { projectExistsMiddleware } from '../middlewares/projectMiddleware.js'
import { getS3URL } from '../utils/s3Service.js.js'

const router = express.Router()

function validateProjectName(name) {
  const projectNameRegex = /^[a-zA-Z0-9](?!.*[-_]{2})[-_a-zA-Z0-9]*[a-zA-Z0-9]$/

  if (!name || name.length < 1 || name.length > 100) {
    return {
      valid: false,
      message: 'Project name must be between 1 and 100 characters.',
    }
  }

  if (!projectNameRegex.test(name)) {
    return {
      valid: false,
      message:
        'Project name can only contain alphanumeric characters, single hyphens, and single underscores, and cannot start or end with a hyphen or underscore. Consecutive hyphens or underscores are not allowed.',
    }
  }

  return { valid: true }
}

// SAVE
router.post('/', authMiddleware, (req, res) => {
  const { name, description } = req.body

  if (!name || !description)
    return res
      .status(400)
      .json({ message: 'Name and description are required.' })

  const nameValidation = validateProjectName(name)
  if (!nameValidation.valid)
    return res.status(400).json({ message: nameValidation.message })

  try {
    const insertProjectQuery = db.prepare(`
        INSERT INTO project (name, description, created_at, updated_at)
        VALUES (?, ?, CURRENT_DATE, CURRENT_DATE)
      `)

    const result = insertProjectQuery.run(name, description)

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

    return res.json(
      projects.map((project) => {
        const { image_key, ...rest } = project
        return {
          ...rest,
          contributors_url: `${baseUrl}/api/projects/${project.name}/contributors`,
          technologies_url: `${baseUrl}/api/projects/${project.name}/technologies`,
          image_url: image_key ? getS3URL(image_key) : null,
        }
      })
    )
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to fetch projects.' })
  }
})

// FIND BY NAME
router.get('/:project_name', projectExistsMiddleware, (req, res) => {
  try {
    const baseUrl = `${req.protocol}://${req.get('host')}`
    const { image_key, ...rest } = req.project

    return res.json({
      ...rest,
      contributors_url: `${baseUrl}/api/projects/${req.project.name}/contributors`,
      technologies_url: `${baseUrl}/api/projects/${req.project.name}/technologies`,
      image_url: image_key ? getS3URL(image_key) : null,
    })
  } catch (err) {
    console.error(err.message)
    res.status(500).json({ message: 'Failed to fetch project.' })
  }
})

// UPDATE BY NAME
router.put(
  '/:project_name',
  authMiddleware,
  projectExistsMiddleware,
  (req, res) => {
    const { newName, description } = req.body

    if (newName) {
      const nameValidation = validateProjectName(newName)
      if (!nameValidation.valid)
        return res.status(400).json({ message: nameValidation.message })
    }

    try {
      const updateProjectQuery = db.prepare(`
        UPDATE project 
        SET 
          name = COALESCE(?, name),
          description = COALESCE(?, description),
          updated_at = CURRENT_DATE
        WHERE name = ?
      `)

      updateProjectQuery.run(newName, description, req.project.name)

      res.json({ message: 'Project updated successfully.' })
    } catch (err) {
      if (err.code === 'SQLITE_CONSTRAINT_UNIQUE')
        return res
          .status(409)
          .json({ message: 'A project with this name already exists.' })

      console.error(err.message)
      res.status(500).json({ message: 'Failed to update the project.' })
    }
  }
)

// DELETE BY NAME
router.delete(
  '/:project_name',
  authMiddleware,
  projectExistsMiddleware,
  (req, res) => {
    try {
      const deleteProjectQuery = db.prepare(
        'DELETE FROM project WHERE name = ?'
      )
      deleteProjectQuery.run(req.project.name)

      res.json({ message: 'Project deleted successfully.' })
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to delete the project.' })
    }
  }
)

// -------- CONTRIBUTORS --------

// ASSOCIATE MEMBER <--> PROJECT
router.post(
  '/:project_name/contributors',
  authMiddleware,
  projectExistsMiddleware,
  (req, res) => {
    const { member_id } = req.body

    if (!member_id)
      return res.status(400).json({ message: 'Member ID is required.' })

    try {
      const findMemberQuery = db.prepare('SELECT id FROM member WHERE id = ?')
      const member = findMemberQuery.get(member_id)

      if (!member) return res.status(404).json({ message: 'Member not found.' })

      const addContributorQuery = db.prepare(`
      INSERT INTO contributor (member_id, project_id)
      VALUES (?, ?)
    `)
      addContributorQuery.run(member_id, req.project.id)

      res.status(201).json({
        message: 'Contributor added successfully to the project.',
        project_id: req.project.id,
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
  }
)

// FIND ALL CONTRIBUTORS
router.get(
  '/:project_name/contributors',
  projectExistsMiddleware,
  (req, res) => {
    try {
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
      const contributors = getContributorsQuery.all(req.project.id)

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
  }
)

// DISASSOCIATE MEMBER <--> PROJECT
router.delete(
  '/:project_name/contributors/:member_id',
  authMiddleware,
  projectExistsMiddleware,
  (req, res) => {
    const { member_id } = req.params

    try {
      const findContributorQuery = db.prepare(`
      SELECT * 
      FROM contributor
      WHERE member_id = ? AND project_id = ?
    `)
      const contributor = findContributorQuery.get(member_id, req.project.id)

      if (!contributor)
        return res
          .status(404)
          .json({ message: 'Contributor not found in this project.' })

      const deleteContributorQuery = db.prepare(`
      DELETE FROM contributor
      WHERE member_id = ? AND project_id = ?
    `)
      deleteContributorQuery.run(member_id, req.project.id)

      res.json({
        message: 'Contributor removed successfully from the project.',
        project_id: req.project.id,
        member_id: parseInt(member_id),
      })
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to remove contributor.' })
    }
  }
)

// -------- TECHNOLOGIES --------

// ASSOCIATE TECHNOLOGY <--> PROJECT
router.post(
  '/:project_name/technologies',
  authMiddleware,
  projectExistsMiddleware,
  (req, res) => {
    const { technology_name, usage_level } = req.body

    if (!technology_name || !usage_level)
      return res
        .status(400)
        .json({ message: 'Technology name and usage level are required.' })

    const validUsageLevels = ['primary', 'secondary', 'experimental']
    if (!validUsageLevels.includes(usage_level))
      return res.status(400).json({
        message: `Invalid usage level. Must be one of: ${validUsageLevels.join(', ')}`,
      })

    try {
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
        req.project.id,
        technology.id
      )

      if (existingAssociation) {
        return res.status(409).json({
          message: 'Technology is already associated with this project.',
          project_id: req.project.id,
          technology_name,
          existing_usage_level: existingAssociation.usage_level,
        })
      }

      const addProjectTechnologyQuery = db.prepare(`
      INSERT INTO project_technology (project_id, technology_id, usage_level)
      VALUES (?, ?, ?)
    `)
      addProjectTechnologyQuery.run(req.project.id, technology.id, usage_level)

      res.status(201).json({
        message: 'Technology added successfully to the project.',
        project_id: req.project.id,
        technology_name,
        usage_level,
      })
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to add technology to project.' })
    }
  }
)

// FIND ALL PROJECT TECHNOLOGIES
router.get(
  '/:project_name/technologies',
  projectExistsMiddleware,
  (req, res) => {
    try {
      const getTechnologiesQuery = db.prepare(`
      SELECT t.id, t.name, t.icon_url, t.type, pt.usage_level
      FROM project_technology pt
      JOIN technology t ON pt.technology_id = t.id
      WHERE pt.project_id = ?
    `)
      const technologies = getTechnologiesQuery.all(req.project.id)

      res.json(technologies)
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to fetch technologies.' })
    }
  }
)

// DISASSOCIATE TECHNOLOGY <--> PROJECT
router.delete(
  '/:project_name/technologies/:technology_id',
  authMiddleware,
  projectExistsMiddleware,
  (req, res) => {
    const { technology_id } = req.params

    try {
      const findTechnologyQuery = db.prepare(`
      SELECT * 
      FROM project_technology 
      WHERE project_id = ? AND technology_id = ?
    `)
      const technologyAssociation = findTechnologyQuery.get(
        req.project.id,
        technology_id
      )

      if (!technologyAssociation)
        return res.status(404).json({
          message: 'Technology not associated with this project.',
        })

      const deleteTechnologyQuery = db.prepare(`
      DELETE FROM project_technology 
      WHERE project_id = ? AND technology_id = ?
    `)
      deleteTechnologyQuery.run(req.project.id, technology_id)

      return res.json({
        message: 'Technology removed successfully from the project.',
        project_id: req.project.id,
        technology_id: parseInt(technology_id),
      })
    } catch (err) {
      console.error(err.message)
      return res
        .status(500)
        .json({ message: 'Failed to remove technology from project.' })
    }
  }
)

export default router
