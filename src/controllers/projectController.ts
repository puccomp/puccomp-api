import { RequestHandler } from 'express'
import { BASE_URL, } from '../index.js'
import { deleteObjectFromS3, getS3URL, uploadObjectToS3 } from '../utils/s3.js'
import { Prisma, Project, TechnologyUsageLevel } from '@prisma/client'
import prisma from '../utils/prisma.js'

type CreateProjectDTO = {
  name: string
  description: string
  created_at: string // yyyy-mm-dd
  updated_at: string // yyyy-mm-dd
}
type UpdateProjectDTO = Partial<CreateProjectDTO>

const projectsController = {
  insert: (async (req, res) => {
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
    let imageKey: string | null = null
    try {
      if (image) {
        imageKey = `projects/${Date.now()}_${sanitizeFileName(image.originalname)}`
        await uploadObjectToS3(image, imageKey)
      }

      const project = await prisma.project.create({
        data: {
          name,
          description,
          imageKey,
          createdAt: created_at ? new Date(created_at) : new Date(),
          updatedAt: updated_at ? new Date(updated_at) : new Date(),
        },
      })

      res.status(201).json({
        message: image
          ? 'Project created successfully and image was uploaded.'
          : 'Project created successfully. No image provided.',
        project_id: project.id,
        project_url: `${BASE_URL}/api/projects/${name}`,
      })
    } catch (err) {
      if (imageKey) {
        await deleteObjectFromS3(imageKey).catch((cleanupErr) => {
          console.error(`S3 CLEANUP FAILED for key ${imageKey}:`, cleanupErr)
        })
      }
      if (err instanceof Prisma.PrismaClientKnownRequestError) {
        if (err.code === 'P2002') {
          res
            .status(409)
            .json({ message: 'A project with this name already exists.' })
          return
        }
      }
      console.error(err)
      res.status(500).json({ message: 'Failed to create project.' })
    }
  }) as RequestHandler<{}, {}, CreateProjectDTO>,

  get: (async (req, res) => {
    try {
      const { imageKey, ...rest } = req.project!
      res.json({
        ...rest,
        contributors_url: `${BASE_URL}/api/projects/${req.project!.name}/contributors`,
        technologies_url: `${BASE_URL}/api/projects/${req.project!.name}/technologies`,
        image_url: imageKey ? getS3URL(imageKey) : null,
      })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to fetch project.' })
    }
  }) as RequestHandler,

  all: (async (_req, res) => {
    try {
      const projects = await prisma.project.findMany()
      res.json(
        projects.map((project: Project) => {
          const { imageKey, ...rest } = project
          return {
            ...rest,
            contributors_url: `${BASE_URL}/api/projects/${project.name}/contributors`,
            technologies_url: `${BASE_URL}/api/projects/${project.name}/technologies`,
            image_url: imageKey ? getS3URL(imageKey) : null,
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
      const { name, description, created_at, updated_at } =
        req.body as UpdateProjectDTO
      const image = req.file
      const oldProject = req.project!

      if (name) {
        const nameValidation = validateProjectName(name)
        if (!nameValidation.valid) {
          res.status(400).json({ message: nameValidation.message })
          return
        }
      }

      if (image && oldProject.imageKey)
        await deleteObjectFromS3(oldProject.imageKey)

      const newImageKey = image
        ? `projects/${Date.now()}_${sanitizeFileName(image.originalname)}`
        : null

      const updatedProject = await prisma.project.update({
        where: { id: oldProject.id },
        data: {
          name,
          description,
          imageKey: newImageKey,
          createdAt: created_at ? new Date(created_at) : undefined,
          updatedAt: updated_at ? new Date(updated_at) : undefined,
        },
      })

      if (image && newImageKey) await uploadObjectToS3(image, newImageKey)

      res.json({
        message: 'Project updated successfully.',
        project_url: `${BASE_URL}/api/projects/${updatedProject.name}`,
      })
    } catch (err) {
      if (
        err instanceof Prisma.PrismaClientKnownRequestError &&
        err.code === 'P2002'
      ) {
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
      const { name, imageKey } = req.project!
      if (imageKey) await deleteObjectFromS3(imageKey)
      await prisma.project.delete({ where: { name } })

      res.json({ message: 'Project deleted successfully.' })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to delete projects.' })
    }
  }) as RequestHandler,

  addContributor: (async (req, res) => {
    const { member_id } = req.body
    const projectId = req.project!.id

    if (!member_id) {
      res.status(400).json({ message: 'Member id is required.' })
      return
    }

    try {
      const contributor = await prisma.contributor.create({
        data: {
          projectId,
          memberId: Number(member_id),
        },
      })
      res
        .status(201)
        .json({ message: 'Contributor added successfully.', data: contributor })
    } catch (err) {
      if (err instanceof Prisma.PrismaClientKnownRequestError) {
        if (err.code === 'P2002') {
          res.status(409).json({
            message: 'Member is already a contributor to this project.',
          })
          return
        }
        if (err.code === 'P2003' || err.code === 'P2025') {
          res.status(404).json({ message: 'Member or Project not found.' })
          return
        }
      }
      console.error(err)
      res.status(500).json({ message: 'Failed to add contributor.' })
    }
  }) as RequestHandler,

  allContributors: (async (req, res) => {
    try {
      const projectId = req.project!.id
      const contributors = await prisma.contributor.findMany({
        where: { projectId },
        include: {
          member: {
            select: {
              id: true,
              name: true,
              surname: true,
              avatarUrl: true,
              isActive: true,
              githubUrl: true,
            },
          },
        },
      })

      res.json(
        contributors.map((contributor) => ({
          name: contributor.member.name,
          surname: contributor.member.surname,
          avatar_url: contributor.member.avatarUrl,
          github_url: contributor.member.githubUrl,
          member_url: `${BASE_URL}/api/members/${contributor.member.id}`,
          is_active: Boolean(contributor.member.isActive),
        }))
      )
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to fetch contributors.' })
    }
  }) as RequestHandler,

  removeContributor: (async (req, res) => {
    const memberId = parseInt(req.params.member_id, 10)
    const projectId = req.project!.id

    try {
      await prisma.contributor.delete({
        where: { memberId_projectId: { memberId, projectId } },
      })
      res.json({ message: 'Contributor removed successfully.' })
      return
    } catch (err) {
      if (
        err instanceof Prisma.PrismaClientKnownRequestError &&
        err.code === 'P2025'
      ) {
        res
          .status(404)
          .json({ message: 'Contributor not found in this project.' })
        return
      }
      console.error(err)
      res.status(500).json({ message: 'Failed to remove contributor.' })
      return
    }
  }) as RequestHandler<{ project_name: string; member_id: string }>,

  addTech: (async (req, res) => {
    const { technology_name, usage_level } = req.body
    const projectId = req.project!.id

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
    try {
      const technology = await prisma.technology.findUnique({
        where: { name: technology_name },
      })
      if (!technology) {
        res.status(404).json({ message: 'Technology not found.' })
        return
      }

      const projectTech = await prisma.projectTechnology.create({
        data: {
          projectId,
          technologyId: technology.id,
          usageLevel: usage_level as TechnologyUsageLevel,
        },
      })

      res.status(201).json({
        message: 'Technology added successfully to the project.',
        project_id: projectTech.projectId,
        technology_id: technology.id,
      })
    } catch (err) {
      if (err instanceof Prisma.PrismaClientKnownRequestError) {
        if (err.code === 'P2002') {
          res.status(409).json({
            message: 'Technology is already associated with this project.',
          })
          return
        }
        if (err.code === 'P2003' || err.code === 'P2025') {
          res.status(404).json({ message: 'Technology or Project not found.' })
          return
        }
      }
      console.error(err)
      res.status(500).json({ message: 'Failed to add technology.' })
    }
  }) as RequestHandler,

  allTechs: (async (req, res) => {
    try {
      const projectId = req.project!.id
      const technologies = await prisma.projectTechnology.findMany({
        where: { projectId },
        include: {
          technology: true,
        },
      })
      res.json(technologies)
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to fetch technologies.' })
    }
  }) as RequestHandler,

  removeTech: (async (req, res) => {
    const technologyId = parseInt(req.params.technology_id, 10)
    const projectId = req.project!.id

    try {
      await prisma.projectTechnology.delete({
        where: { projectId_technologyId: { projectId, technologyId } },
      })
      res.json({ message: 'Technology removed successfully.' })
      return
    } catch (err) {
      if (
        err instanceof Prisma.PrismaClientKnownRequestError &&
        err.code === 'P2025'
      ) {
        res
          .status(404)
          .json({ message: 'Technology not associated with this project.' })
        return
      }
      console.error(err)
      res.status(500).json({ message: 'Failed to remove technology.' })
      return
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
