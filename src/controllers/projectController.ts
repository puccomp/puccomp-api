import { RequestHandler } from 'express'
import { BASE_URL } from '../index.js'
import { formatDate } from '../utils/formats.js'
import { deleteObjectFromS3, getS3URL, uploadObjectToS3 } from '../utils/s3.js'
import { Prisma, Project, ProjectAsset } from '@prisma/client'
import prisma from '../utils/prisma.js'
import { validate } from '../utils/validate.js'
import { sanitizeFileName } from '../utils/uploads.js'
import { generateSlug } from '../utils/slug.js'
import {
  CreateProjectSchema,
  UpdateProjectSchema,
  AddContributorSchema,
  AddTechSchema,
  CreateAssetSchema,
  UpdateAssetSchema,
  MemberIdParamSchema,
  TechIdParamSchema,
  AssetIdParamSchema,
} from '../schemas/projectSchemas.js'


const formatProject = (
  project: Project & { assets?: ProjectAsset[] }
) => {
  const { createdAt, updatedAt, ...rest } = project
  return {
    ...rest,
    created_at: formatDate(createdAt),
    updated_at: updatedAt ? formatDate(updatedAt) : null,
    start_date: project.startDate ? formatDate(project.startDate) : null,
    end_date: project.endDate ? formatDate(project.endDate) : null,
    is_featured: project.isFeatured,
    is_internal: project.isInternal,
    assets: project.assets?.map(formatAsset) ?? undefined,
    contributors_url: `${BASE_URL}/api/projects/${project.slug}/contributors`,
    technologies_url: `${BASE_URL}/api/projects/${project.slug}/technologies`,
  }
}

const formatAsset = (asset: ProjectAsset) => ({
  id: asset.id,
  type: asset.type,
  url: getS3URL(asset.key),
  caption: asset.caption,
  order: asset.order,
})

const projectsController = {
  insert: (async (req, res) => {
    const body = validate(CreateProjectSchema, req.body, res)
    if (!body) return
    const {
      name,
      description,
      slug,
      status,
      is_featured,
      priority,
      start_date,
      end_date,
      is_internal,
      created_at,
      updated_at,
    } = body

    try {
      const project = await prisma.project.create({
        data: {
          name,
          slug: slug ?? generateSlug(name),
          description,
          status: status ?? 'PLANNING',
          isFeatured: is_featured ?? false,
          priority: priority ?? 0,
          startDate: start_date ? new Date(start_date) : null,
          endDate: end_date ? new Date(end_date) : null,
          isInternal: is_internal ?? false,
          createdAt: created_at ? new Date(created_at) : new Date(),
          updatedAt: updated_at ? new Date(updated_at) : new Date(),
        },
      })

      res.status(201).json({
        message: 'Projeto criado com sucesso.',
        project_id: project.id,
        project_url: `${BASE_URL}/api/projects/${project.slug}`,
      })
    } catch (err) {
      if (
        err instanceof Prisma.PrismaClientKnownRequestError &&
        err.code === 'P2002'
      ) {
        res
          .status(409)
          .json({ message: 'Já existe um projeto com este nome ou slug.' })
        return
      }
      console.error(err)
      res.status(500).json({ message: 'Falha ao criar o projeto.' })
    }
  }) as RequestHandler,

  get: (async (req, res) => {
    try {
      const project = await prisma.project.findUnique({
        where: { id: req.project!.id },
        include: { assets: { orderBy: { order: 'asc' } } },
      })
      res.json(formatProject(project!))
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Falha ao buscar o projeto.' })
    }
  }) as RequestHandler,

  all: (async (_req, res) => {
    try {
      const projects = await prisma.project.findMany({
        include: { assets: { orderBy: { order: 'asc' } } },
        orderBy: [{ priority: 'desc' }, { createdAt: 'desc' }],
      })
      res.json(projects.map(formatProject))
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Falha ao buscar os projetos.' })
    }
  }) as RequestHandler,

  update: (async (req, res) => {
    const body = validate(UpdateProjectSchema, req.body, res)
    if (!body) return
    const {
      name,
      description,
      slug,
      status,
      is_featured,
      priority,
      start_date,
      end_date,
      is_internal,
      created_at,
      updated_at,
    } = body
    const oldProject = req.project!

    try {
      const updatedProject = await prisma.project.update({
        where: { id: oldProject.id },
        data: {
          name,
          description,
          slug,
          status,
          isFeatured: is_featured,
          priority,
          startDate: start_date !== undefined
            ? (start_date ? new Date(start_date) : null)
            : undefined,
          endDate: end_date !== undefined
            ? (end_date ? new Date(end_date) : null)
            : undefined,
          isInternal: is_internal,
          createdAt: created_at ? new Date(created_at) : undefined,
          updatedAt: updated_at ? new Date(updated_at) : undefined,
        },
      })

      res.json({
        message: 'Projeto atualizado com sucesso.',
        project_url: `${BASE_URL}/api/projects/${updatedProject.slug}`,
      })
    } catch (err) {
      if (
        err instanceof Prisma.PrismaClientKnownRequestError &&
        err.code === 'P2002'
      ) {
        res.status(409).json({ message: 'Já existe um projeto com este slug.' })
        return
      }
      console.error(err)
      res.status(500).json({ message: 'Falha ao atualizar o projeto.' })
    }
  }) as RequestHandler,

  delete: (async (req, res) => {
    const { id } = req.project!

    try {
      const assets = await prisma.projectAsset.findMany({
        where: { projectId: id },
        select: { key: true },
      })

      await Promise.allSettled(assets.map((a) => deleteObjectFromS3(a.key)))

      await prisma.project.delete({ where: { id } })

      res.json({ message: 'Projeto excluído com sucesso.' })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Falha ao excluir o projeto.' })
    }
  }) as RequestHandler,

  // ── Assets ──────────────────────────────────────────────────────────────

  addAsset: (async (req, res) => {
    const file = req.file
    if (!file) {
      res.status(400).json({ message: 'Nenhum arquivo enviado.' })
      return
    }

    const body = validate(CreateAssetSchema, req.body, res)
    if (!body) return

    const projectId = req.project!.id
    const assetKey = `projects/${projectId}/assets/${Date.now()}_${sanitizeFileName(file.originalname)}`

    try {
      await uploadObjectToS3(file, assetKey)

      const asset = await prisma.projectAsset.create({
        data: {
          projectId,
          key: assetKey,
          type: body.type ?? 'IMAGE',
          caption: body.caption,
          order: body.order ?? 0,
        },
      })

      res.status(201).json({
        message: 'Asset adicionado com sucesso.',
        asset: formatAsset(asset),
      })
    } catch (err) {
      await deleteObjectFromS3(assetKey).catch((cleanupErr) => {
        console.error(`S3 CLEANUP FAILED for key ${assetKey}:`, cleanupErr)
      })
      console.error(err)
      res.status(500).json({ message: 'Falha ao adicionar o asset.' })
    }
  }) as RequestHandler,

  allAssets: (async (req, res) => {
    try {
      const assets = await prisma.projectAsset.findMany({
        where: { projectId: req.project!.id },
        orderBy: { order: 'asc' },
      })
      res.json(assets.map(formatAsset))
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Falha ao buscar os assets.' })
    }
  }) as RequestHandler,

  updateAsset: (async (req, res) => {
    const params = validate(AssetIdParamSchema, req.params, res)
    if (!params) return

    const body = validate(UpdateAssetSchema, req.body, res)
    if (!body) return

    const asset = await prisma.projectAsset.findFirst({
      where: { id: params.asset_id, projectId: req.project!.id },
    })
    if (!asset) {
      res.status(404).json({ message: 'Asset não encontrado neste projeto.' })
      return
    }

    try {
      const updated = await prisma.projectAsset.update({
        where: { id: asset.id },
        data: { caption: body.caption, order: body.order, type: body.type },
      })
      res.json({
        message: 'Asset atualizado com sucesso.',
        asset: formatAsset(updated),
      })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Falha ao atualizar o asset.' })
    }
  }) as RequestHandler<{ slug: string; asset_id: string }>,

  deleteAsset: (async (req, res) => {
    const params = validate(AssetIdParamSchema, req.params, res)
    if (!params) return

    const asset = await prisma.projectAsset.findFirst({
      where: { id: params.asset_id, projectId: req.project!.id },
    })
    if (!asset) {
      res.status(404).json({ message: 'Asset não encontrado neste projeto.' })
      return
    }

    try {
      await deleteObjectFromS3(asset.key)
      await prisma.projectAsset.delete({ where: { id: asset.id } })
      res.json({ message: 'Asset excluído com sucesso.' })
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Falha ao excluir o asset.' })
    }
  }) as RequestHandler<{ slug: string; asset_id: string }>,

  // ── Contributors ────────────────────────────────────────────────────────

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
        .json({ message: 'Contribuidor adicionado com sucesso.', data: contributor })
    } catch (err) {
      if (err instanceof Prisma.PrismaClientKnownRequestError) {
        if (err.code === 'P2002') {
          res.status(409).json({
            message: 'O membro já é contribuidor deste projeto.',
          })
          return
        }
        if (err.code === 'P2003' || err.code === 'P2025') {
          res.status(404).json({ message: 'Membro ou projeto não encontrado.' })
          return
        }
      }
      console.error(err)
      res.status(500).json({ message: 'Falha ao adicionar o contribuidor.' })
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
      res.status(500).json({ message: 'Falha ao buscar os contribuidores.' })
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
      res.json({ message: 'Contribuidor removido com sucesso.' })
    } catch (err) {
      if (
        err instanceof Prisma.PrismaClientKnownRequestError &&
        err.code === 'P2025'
      ) {
        res
          .status(404)
          .json({ message: 'Contribuidor não encontrado neste projeto.' })
        return
      }
      console.error(err)
      res.status(500).json({ message: 'Falha ao remover o contribuidor.' })
    }
  }) as RequestHandler<{ slug: string; member_id: string }>,

  // ── Technologies ────────────────────────────────────────────────────────

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
        res.status(404).json({ message: 'Tecnologia não encontrada.' })
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
        message: 'Tecnologia adicionada ao projeto com sucesso.',
        project_id: projectTech.projectId,
        technology_id: technology.id,
      })
    } catch (err) {
      if (err instanceof Prisma.PrismaClientKnownRequestError) {
        if (err.code === 'P2002') {
          res.status(409).json({
            message: 'A tecnologia já está associada a este projeto.',
          })
          return
        }
        if (err.code === 'P2003' || err.code === 'P2025') {
          res.status(404).json({ message: 'Tecnologia ou projeto não encontrado.' })
          return
        }
      }
      console.error(err)
      res.status(500).json({ message: 'Falha ao adicionar a tecnologia.' })
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
      res.status(500).json({ message: 'Falha ao buscar as tecnologias.' })
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
      res.json({ message: 'Tecnologia removida com sucesso.' })
    } catch (err) {
      if (
        err instanceof Prisma.PrismaClientKnownRequestError &&
        err.code === 'P2025'
      ) {
        res
          .status(404)
          .json({ message: 'A tecnologia não está associada a este projeto.' })
        return
      }
      console.error(err)
      res.status(500).json({ message: 'Falha ao remover a tecnologia.' })
    }
  }) as RequestHandler<{ slug: string; technology_id: string }>,
}

export default projectsController
