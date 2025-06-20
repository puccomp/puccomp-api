import { RequestHandler } from 'express'
import { BASE_URL } from '../index.js'
import { deleteObjectFromS3, getS3URL, uploadObjectToS3 } from '../utils/s3.js'

// MODELS
import projectModel, { Project, ProjectData } from '../models/projectModel.js'
import memberModel from '../models/memberModel.js'
import technologyModel from '../models/technologyModel.js'

import { TechnologyUsageLevel } from '../utils/enums.js'

type CreateProjectDTO = Pick<
  Project,
  'name' | 'description' | 'created_at' | 'updated_at'
>
type UpdateProjectDTO = Partial<CreateProjectDTO>

const projectsController = {
  insert: (async (req, res) => {
    try {
      const { name, description, created_at, updated_at } = req.body
      const image = req.file

      if (!name || !description) {
        res.status(400).json({ message: 'Name and description are required.' })
        return
      }
      const nameValidation = validateProjectName(name)
      if (!nameValidation.valid) {
        res.status(400).json({ message: nameValidation.message })
        return
      }

      const imageKey = image
        ? `projects/${Date.now()}_${sanitizeFileName(image.originalname)}`
        : null

      const id = projectModel.save({
        name,
        description,
        image_key: imageKey,
        created_at,
        updated_at,
      })

      if (image && imageKey) await uploadObjectToS3(image, imageKey)

      res.status(201).json({
        message: image
          ? 'Project created successfully and image was uploaded.'
          : 'Project created successfully. No image provided.',
        project_id: id,
        project_url: `${BASE_URL}/api/projects/${name}`,
      })
    } catch (err) {
      const error = err as Error & { code?: string }
      if (error.code === 'SQLITE_CONSTRAINT_UNIQUE') {
        res
          .status(409)
          .json({ message: 'A project with this name already exists.' })
        return
      }
      console.error(error.message)
      res.status(500).json({ message: 'Failed to create project.' })
    }
  }) as RequestHandler<{}, {}, CreateProjectDTO>,

  get: ((req, res) => {
    try {
      const { image_key, ...rest } = req.project!

      res.json({
        ...rest,
        contributors_url: `${BASE_URL}/api/projects/${req.project!.name}/contributors`,
        technologies_url: `${BASE_URL}/api/projects/${req.project!.name}/technologies`,
        image_url: image_key ? getS3URL(image_key) : null,
      })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to fetch project.' })
    }
  }) as RequestHandler,

  all: ((_req, res) => {
    try {
      const projects = projectModel.all()

      res.json(
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
      console.error(err)
      res.status(500).json({ message: 'Failed to fetch projects.' })
    }
  }) as RequestHandler,

  update: (async (req, res) => {
    try {
      const { name, description, created_at, updated_at } = req.body
      const image = req.file
      const oldImageKey = req.project!.image_key
      const oldName = req.project!.name

      if (name) {
        const nameValidation = validateProjectName(name)
        if (!nameValidation.valid) {
          res.status(400).json({ message: nameValidation.message })
          return
        }
      }

      if (image && oldImageKey) await deleteObjectFromS3(oldImageKey)

      const imageKey = image
        ? `projects/${Date.now()}_${sanitizeFileName(image.originalname)}`
        : null

      const updateData: ProjectData = {
        name: name,
        description: description,
        image_key: imageKey,
        created_at: created_at,
        updated_at: updated_at,
      }

      projectModel.update(oldName, updateData)

      if (image && imageKey) await uploadObjectToS3(image, imageKey)

      res.json({ message: 'Project updated successfully.' })
    } catch (err) {
      const error = err as Error & { code?: string }
      if (error.code === 'SQLITE_CONSTRAINT_UNIQUE') {
        res
          .status(409)
          .json({ message: 'A project with this name already exists.' })
        return
      }
      console.error(err)
      res.status(500).json({ message: 'Failed to update the project.' })
    }
  }) as RequestHandler<{ project_name: string }, {}, UpdateProjectDTO>,

  delete: (async (req, res) => {
    try {
      const { name, image_key } = req.project!
      if (image_key) await deleteObjectFromS3(image_key)
      projectModel.delete(name)

      res.json({ message: 'Project deleted successfully.' })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to delete projects.' })
    }
  }) as RequestHandler,

  addContributor: ((req, res) => {
    try {
      const { member_id } = req.body
      const { id: project_id } = req.project!

      if (!member_id) {
        res.status(400).json({ message: 'Member id is required.' })
        return
      }

      if (!memberModel.exists(member_id)) {
        res.status(404).json({ message: 'Member not found.' })
        return
      }

      projectModel.saveContributor(member_id, project_id)

      res.status(201).json({
        message: 'Contributor added successfully to the project.',
        project_id: project_id,
        member_id: member_id,
      })
    } catch (err) {
      const error = err as { code?: string; message: string }
      if (error.code === 'SQLITE_CONSTRAINT') {
        res
          .status(409)
          .json({ message: 'Member is already a contributor to this project.' })
        return
      }
      console.error(err)
      res.status(500).json({ message: 'Failed to add contributor.' })
    }
  }) as RequestHandler,

  allContributors: ((req, res) => {
    try {
      const contributors = projectModel.allContributors(req.project!.id)

      res.json(
        contributors.map((contributor) => ({
          ...contributor,
          is_active: Boolean(contributor.is_active),
          member_url: `${BASE_URL}/api/members/${contributor.member_id}`,
        }))
      )
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to fetch contributors.' })
    }
  }) as RequestHandler,

  removeContributor: ((req, res) => {
    try {
      const { member_id } = req.params
      const { id: project_id } = req.project!

      const memberID = parseInt(member_id)

      if (!projectModel.hasContributor(memberID, project_id)) {
        res
          .status(404)
          .json({ message: 'Contributor not found in this project.' })
        return
      }

      projectModel.deleteContributor(memberID, project_id)

      res.json({
        message: 'Contributor removed successfully from the project.',
        project_id: project_id,
        member_id: memberID,
      })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to remove contributor.' })
    }
  }) as RequestHandler<{ project_name: string; member_id: string }>,

  addTech: ((req, res) => {
    try {
      const { technology_name, usage_level } = req.body
      const { id: project_id } = req.project!

      if (!technology_name || !usage_level) {
        res
          .status(400)
          .json({ message: 'Technology name and usage level are required.' })
        return
      }

      const validUsageLevels = Object.values(TechnologyUsageLevel)
      if (!validUsageLevels.includes(usage_level)) {
        res.status(400).json({
          message: `Invalid usage level. Must be one of: ${validUsageLevels.join(', ')}`,
        })
        return
      }

      const technology = technologyModel.find(technology_name)
      if (!technology) {
        res.status(404).json({ message: 'Technology not found.' })
        return
      }

      if (projectModel.hasTech(technology.id, project_id)) {
        res.status(409).json({
          message: 'Technology is already associated with this project.',
          project_id: project_id,
          technology_name,
        })
        return
      }
      projectModel.saveTech(technology.id, project_id, usage_level)

      res.status(201).json({
        message: 'Technology added successfully to the project.',
        project_id: project_id,
        technology_id: technology.id,
      })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to add technology to project.' })
    }
  }) as RequestHandler,

  allTechs: ((req, res) => {
    try {
      const technologies = projectModel.allTechs(req.project!.id)
      res.json(technologies)
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to fetch technologies.' })
    }
  }) as RequestHandler,

  removeTech: ((req, res) => {
    try {
      const technology_id = parseInt(req.params.technology_id, 10)
      if (isNaN(technology_id)) {
        res.status(400).json({ message: 'Invalid Technology ID format.' })
        return
      }

      if (!req.project) {
        res.status(404).json({ message: 'Project not found on request.' })
        return
      }
      const { id: project_id } = req.project

      if (!projectModel.hasTech(technology_id, project_id)) {
        res.status(404).json({
          message: 'Technology not associated with this project.',
        })
        return
      }

      projectModel.deleteTech(technology_id, project_id)

      res.json({
        message: 'Technology removed successfully from the project.',
        project_id,
        technology_id,
      })
    } catch (err) {
      const error = err as Error
      console.error(error.message)
      res
        .status(500)
        .json({ message: 'Failed to remove technology from project.' })
    }
  }) as RequestHandler<{ project_name: string; technology_id: string }>,
}

const validateProjectName = (
  name: string
): { valid: boolean; message?: string } => {
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

function sanitizeFileName(filename: string): string {
  return filename
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9._-]/g, '_')
    .replace(/\s+/g, '_')
    .toLowerCase()
}

export default projectsController
