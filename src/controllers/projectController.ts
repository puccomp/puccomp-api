import { RequestHandler } from 'express'
import { BASE_URL } from '../index.js'
import { deleteObjectFromS3, getS3URL, uploadObjectToS3 } from '../utils/s3.js'
import { Prisma, Project } from '@prisma/client'
import prisma from '../utils/prisma.js'
import { validate } from '../utils/validate.js'
import { sanitizeFileName } from '../utils/uploads.js'
import {
  CreateProjectSchema,
  UpdateProjectSchema,
  AddContributorSchema,
  AddTechSchema,
  MemberIdParamSchema,
  TechIdParamSchema,
} from '../schemas/projectSchemas.js'

const projectsController = {
  insert: (async (req, res) => {
    const body = validate(CreateProjectSchema, req.body, res)
    if (!body) return
    const { name, description, created_at, updated_at } = body
    const image = req.file

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
  }) as RequestHandler,

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
    const body = validate(UpdateProjectSchema, req.body, res)
    if (!body) return
    const { name, description, created_at, updated_at } = body
    const image = req.file
    const oldProject = req.project!

    try {
      const newImageKey = image
        ? `projects/${Date.now()}_${sanitizeFileName(image.originalname)}`
        : null

      if (image && newImageKey) await uploadObjectToS3(image, newImageKey)

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

      if (image && oldProject.imageKey) {
        await deleteObjectFromS3(oldProject.imageKey).catch((err) => {
          console.error(
            `Failed to delete old S3 object ${oldProject.imageKey}:`,
            err
          )
        })
      }

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
  }) as RequestHandler,

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
    const body = validate(AddContributorSchema, req.body, res)
    if (!body) return
    const projectId = req.project!.id

    try {
      const contributor = await prisma.contributor.create({
        data: { projectId, memberId: body.member_id },
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
              status: true,
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
          is_active: contributor.member.status === 'ACTIVE',
        }))
      )
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to fetch contributors.' })
    }
  }) as RequestHandler,

  removeContributor: (async (req, res) => {
    const params = validate(MemberIdParamSchema, req.params, res)
    if (!params) return
    const projectId = req.project!.id

    try {
      await prisma.contributor.delete({
        where: {
          memberId_projectId: { memberId: params.member_id, projectId },
        },
      })
      res.json({ message: 'Contributor removed successfully.' })
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
    }
  }) as RequestHandler<{ project_name: string; member_id: string }>,

  addTech: (async (req, res) => {
    const body = validate(AddTechSchema, req.body, res)
    if (!body) return
    const { technology_name, usage_level } = body
    const projectId = req.project!.id

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
          usageLevel: usage_level,
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
        include: { technology: true },
      })
      res.json(technologies)
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Failed to fetch technologies.' })
    }
  }) as RequestHandler,

  removeTech: (async (req, res) => {
    const params = validate(TechIdParamSchema, req.params, res)
    if (!params) return
    const projectId = req.project!.id

    try {
      await prisma.projectTechnology.delete({
        where: {
          projectId_technologyId: {
            projectId,
            technologyId: params.technology_id,
          },
        },
      })
      res.json({ message: 'Technology removed successfully.' })
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
    }
  }) as RequestHandler<{ project_name: string; technology_id: string }>,
}

export default projectsController
