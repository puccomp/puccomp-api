import { BASE_URL } from '../index.js'
import { deleteObjectFromS3, getS3URL, uploadObjectToS3 } from '../utils/s3.js'

// MODELS
import projectModel from '../models/projectModel.js'
import memberModel from '../models/memberModel.js'
import technologyModel from '../models/technologyModel.js'
import { TechnologyUsageLevel } from '../utils/enums.js'

const projectsController = {
  insert: async (req, res) => {
    try {
      const { name, description, created_at, updated_at } = req.body
      const image = req.file

      if (!name || !description)
        return res
          .status(400)
          .json({ message: 'Name and description are required.' })

      const nameValidation = validateProjectName(name)
      if (!nameValidation.valid)
        return res.status(400).json({ message: nameValidation.message })

      const imageKey = image
        ? `projects/${Date.now()}_${sanitizeFileName(image.originalname)}`
        : null

      const id = projectModel.save(
        name,
        description,
        imageKey,
        created_at,
        updated_at
      )

      if (image) await uploadObjectToS3(image, imageKey)

      res.status(201).json({
        message: image
          ? 'Project created successfully and image was uploaded.'
          : 'Project created successfully. No image provided.',
        project_id: id,
        project_url: `${BASE_URL}/api/projects/${name}`,
      })
    } catch (err) {
      if (err.code === 'SQLITE_CONSTRAINT_UNIQUE')
        return res
          .status(409)
          .json({ message: 'A project with this name already exists.' })
      console.error(err.message)
      res.status(500).json({ message: 'Failed to create project.' })
    }
  },

  get: (req, res) => {
    try {
      const { image_key, ...rest } = req.project

      return res.json({
        ...rest,
        contributors_url: `${BASE_URL}/api/projects/${req.project.name}/contributors`,
        technologies_url: `${BASE_URL}/api/projects/${req.project.name}/technologies`,
        image_url: image_key ? getS3URL(image_key) : null,
      })
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to fetch project.' })
    }
  },

  all: (req, res) => {
    try {
      const projects = projectModel.all()

      return res.json(
        projects.map((project) => {
          const { image_key, ...rest } = project
          return {
            ...rest,
            contributors_url: `${BASE_URL}/api/projects/${project.name}/contributors`,
            technologies_url: `${BASE_URL}/api/projects/${project.name}/technologies`,
            image_url: image_key ? getS3URL(image_key) : null,
          }
        })
      )
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to fetch projects.' })
    }
  },

  update: async (req, res) => {
    try {
      const { name, description, created_at, updated_at } = req.body
      const image = req.file
      const oldImageKey = req.project.image_key
      const oldName = req.project.name

      if (name !== undefined) {
        const nameValidation = validateProjectName(name)
        if (!nameValidation.valid)
          return res.status(400).json({ message: nameValidation.message })
      }

      if (image && oldImageKey) await deleteObjectFromS3(oldImageKey)

      const imageKey = image
        ? `projects/${Date.now()}_${sanitizeFileName(image.originalname)}`
        : null

      projectModel.update(
        name,
        description,
        imageKey,
        created_at,
        updated_at,
        oldName
      )

      if (image) await uploadObjectToS3(image, imageKey)

      res.json({ message: 'Project updated successfully.' })
    } catch (err) {
      if (err.code === 'SQLITE_CONSTRAINT_UNIQUE')
        return res
          .status(409)
          .json({ message: 'A project with this name already exists.' })

      console.error(err.message)
      res.status(500).json({ message: 'Failed to update the project.' })
    }
  },

  delete: async (req, res) => {
    try {
      const { name, image_key } = req.project
      if (image_key) await deleteObjectFromS3(image_key)
      projectModel.delete(name)

      res.json({ message: 'Project deleted successfully.' })
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to delete projects.' })
    }
  },

  addContributor: (req, res) => {
    try {
      const { member_id } = req.body
      const { id: project_id } = req.project

      if (!member_id)
        return res.status(400).json({ message: 'Member ID is required.' })

      if (!memberModel.exists(member_id))
        return res.status(404).json({ message: 'Member not found.' })

      projectModel.saveContributor(member_id, project_id)

      res.status(201).json({
        message: 'Contributor added successfully to the project.',
        project_id: project_id,
        member_id: member_id,
      })
    } catch (err) {
      if (err.code === 'SQLITE_CONSTRAINT')
        return res
          .status(409)
          .json({ message: 'Member is already a contributor to this project.' })

      console.error(err.message)
      res.status(500).json({ message: 'Failed to add contributor.' })
    }
  },

  allContributors: (req, res) => {
    try {
      const contributors = projectModel.allContributors(req.project.id)

      res.json(
        contributors.map((contributor) => ({
          ...contributor,
          is_active: Boolean(contributor.is_active),
          member_url: `${BASE_URL}/api/members/${contributor.member_id}`,
        }))
      )
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to fetch contributors.' })
    }
  },

  removeContributor: (req, res) => {
    try {
      const { member_id } = req.params
      const { id: project_id } = req.project

      if (!projectModel.contributors.exists(member_id, project_id))
        return res
          .status(404)
          .json({ message: 'Contributor not found in this project.' })

      projectModel.deleteContributor(member_id, project_id)

      res.json({
        message: 'Contributor removed successfully from the project.',
        project_id: req.project.id,
        member_id: parseInt(member_id),
      })
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to remove contributor.' })
    }
  },

  addTech: (req, res) => {
    try {
      const { technology_name, usage_level } = req.body
      const { id: project_id } = req.project

      if (!technology_name || !usage_level)
        return res
          .status(400)
          .json({ message: 'Technology name and usage level are required.' })

      const validUsageLevels = Object.values(TechnologyUsageLevel)
      if (!validUsageLevels.includes(usage_level))
        return res.status(400).json({
          message: `Invalid usage level. Must be one of: ${validUsageLevels.join(', ')}`,
        })

      const technology = technologyModel.find(technology_name)
      if (!technology)
        return res.status(404).json({ message: 'Technology not found.' })

      if (projectModel.hasTech(technology.id, project_id)) {
        return res.status(409).json({
          message: 'Technology is already associated with this project.',
          project_id: project_id,
          technology_name,
        })
      }
      projectModel.saveTech(technology.id, project_id, usage_level)

      res.status(201).json({
        message: 'Technology added successfully to the project.',
        project_id: project_id,
        technology_id: technology.id,
      })
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to add technology to project.' })
    }
  },

  allTechs: (req, res) => {
    try {
      const technologies = projectModel.allTechs(req.project.id)
      res.json(technologies)
    } catch (err) {
      console.error(err.message)
      res.status(500).json({ message: 'Failed to fetch technologies.' })
    }
  },

  removeTech: (req, res) => {
    try {
      const { technology_id } = req.params
      const { id: project_id } = req.project

      if (!projectModel.hasTech(technology_id, project_id))
        return res.status(404).json({
          message: 'Technology not associated with this project.',
        })

      projectModel.deleteTech(technology_id, project_id)

      return res.json({
        message: 'Technology removed successfully from the project.',
        project_id: project_id,
        technology_id: parseInt(technology_id),
      })
    } catch (err) {
      console.error(err.message)
      return res
        .status(500)
        .json({ message: 'Failed to remove technology from project.' })
    }
  },
}

const validateProjectName = (name) => {
  if (
    !name ||
    typeof name !== 'string' ||
    name.length < 3 ||
    name.length > 50
  ) {
    return {
      valid: false,
      message: 'Project name must be between 3 and 50 characters.',
    }
  }
  const regex = /^[a-zA-Z0-9](?!.*[-_]{2})[-_a-zA-Z0-9]*[a-zA-Z0-9]$/
  if (!regex.test(name)) {
    return {
      valid: false,
      message:
        'Project name can only contain alphanumeric characters, single hyphens, and single underscores, and cannot start or end with a hyphen or underscore. Consecutive hyphens or underscores are not allowed.',
    }
  }
  return { valid: true }
}

function sanitizeFileName(filename) {
  return filename
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9._-]/g, '_')
    .replace(/\s+/g, '_')
    .toLowerCase()
}

export default projectsController
