import { RequestHandler } from 'express'
import sharp from 'sharp'
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
  UpdateTechSchema,
  CreateAssetSchema,
  UpdateAssetSchema,
  ProjectQuerySchema,
  MemberIdParamSchema,
  TechIdParamSchema,
  AssetIdParamSchema,
} from '../schemas/projectSchemas.js'

const sortMap: Record<string, string> = {
  priority: 'priority',
  created_at: 'createdAt',
  name: 'name',
  start_date: 'startDate',
}


const formatProject = (
  project: Project & { assets?: ProjectAsset[] }
) => {
  const { createdAt, updatedAt, startDate, endDate, deadline, isFeatured, isInternal, ...rest } = project
  return {
    ...rest,
    created_at: formatDate(createdAt),
    updated_at: updatedAt ? formatDate(updatedAt) : null,
    start_date: startDate ? formatDate(startDate) : null,
    end_date: endDate ? formatDate(endDate) : null,
    deadline: deadline ? formatDate(deadline) : null,
    is_featured: isFeatured,
    is_internal: isInternal,
    assets: project.assets?.map(formatAsset) ?? undefined,
    contributors_url: `${BASE_URL}/api/projects/${project.slug}/contributors`,
    technologies_url: `${BASE_URL}/api/projects/${project.slug}/technologies`,
  }
}

const formatProjectSummary = (
  project: Project & { assets?: ProjectAsset[] }
) => {
  const { created_at, updated_at, ...rest } = formatProject(project)
  return rest
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
      deadline,
      is_internal,
    } = body

    const resolvedStatus = status ?? 'PLANNING'

    // Auto-manage startDate:
    //   PLANNING → always null
    //   IN_PROGRESS → today if not provided (schema already rejects explicit null)
    //   PAUSED/DONE → from body (schema requires it)
    const resolvedStartDate: Date | null =
      resolvedStatus === 'PLANNING'
        ? null
        : start_date
          ? new Date(start_date)
          : resolvedStatus === 'IN_PROGRESS'
            ? new Date()
            : null

    // Auto-manage endDate:
    //   Only DONE can have endDate; auto-set today if absent
    const resolvedEndDate: Date | null =
      resolvedStatus !== 'DONE'
        ? null
        : end_date !== undefined
          ? end_date ? new Date(end_date) : null
          : new Date()

    try {
      const project = await prisma.project.create({
        data: {
          name,
          slug: slug ?? generateSlug(name),
          description,
          status: resolvedStatus,
          isFeatured: is_featured ?? false,
          priority: priority ?? 0,
          startDate: resolvedStartDate,
          endDate: resolvedEndDate,
          deadline: deadline ? new Date(deadline) : null,
          isInternal: is_internal ?? false,
          createdAt: new Date(),
          updatedAt: new Date(),
        },
      })

      res.status(201).json({
        message: 'Projeto criado com sucesso.',
        project_id: project.id,
        project_slug: project.slug,
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

  all: (async (req, res) => {
    const query = validate(ProjectQuerySchema, req.query, res)
    if (!query) return

    const { page, limit, sort_by, order, status, is_featured, is_internal } =
      query
    const skip = (page - 1) * limit
    const where = {
      ...(status !== undefined && { status }),
      ...(is_featured !== undefined && { isFeatured: is_featured }),
      ...(is_internal !== undefined && { isInternal: is_internal }),
    }
    const orderBy = [
      { [sortMap[sort_by]]: order },
      ...(sort_by !== 'priority' ? [{ priority: 'desc' as const }] : []),
    ]

    try {
      const [projects, total] = await Promise.all([
        prisma.project.findMany({
          where,
          include: { assets: { orderBy: { order: 'asc' } } },
          orderBy,
          take: limit,
          skip,
        }),
        prisma.project.count({ where }),
      ])

      res.json({
        data: projects.map(formatProjectSummary),
        pagination: {
          total,
          page,
          limit,
          total_pages: Math.ceil(total / limit),
        },
      })
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
      deadline,
      is_internal,
    } = body
    const oldProject = req.project!
    const oldStatus = oldProject.status

    const effectiveStatus = status ?? oldStatus

    // Resolve startDate:
    //   → PLANNING: auto-clear
    //   → IN_PROGRESS (from PLANNING): auto-set today if no startDate in body or DB
    //   → other: use body value or leave unchanged
    let resolvedStartDate: Date | null | undefined
    if (status === 'PLANNING') {
      resolvedStartDate = null
    } else if (start_date !== undefined) {
      resolvedStartDate = start_date ? new Date(start_date) : null
    } else if (status === 'IN_PROGRESS' && !oldProject.startDate) {
      resolvedStartDate = new Date()
    }

    // Resolve endDate:
    //   → non-DONE: auto-clear
    //   → DONE: use body value, auto-set today if transitioning in, else keep DB value
    let resolvedEndDate: Date | null | undefined
    if (status !== undefined && status !== 'DONE') {
      resolvedEndDate = null
    } else if (status === 'DONE') {
      if (end_date !== undefined) {
        resolvedEndDate = end_date ? new Date(end_date) : null
      } else if (oldStatus !== 'DONE') {
        resolvedEndDate = new Date() // first time reaching DONE — auto-set today
      }
      // else already DONE and no end_date in body → leave DB value (undefined = no change)
    } else if (end_date !== undefined) {
      resolvedEndDate = end_date ? new Date(end_date) : null
    }

    // Validate final state (controller checks DB-dependent invariants)
    const finalStartDate =
      resolvedStartDate !== undefined ? resolvedStartDate : oldProject.startDate
    const finalEndDate =
      resolvedEndDate !== undefined ? resolvedEndDate : oldProject.endDate

    if (
      (effectiveStatus === 'IN_PROGRESS' || effectiveStatus === 'PAUSED') &&
      !finalStartDate
    ) {
      res.status(422).json({
        message: `start_date é obrigatório quando o status é ${effectiveStatus}.`,
      })
      return
    }
    if (effectiveStatus === 'DONE' && !finalStartDate) {
      res.status(422).json({ message: 'start_date é obrigatório quando o status é DONE.' })
      return
    }
    if (effectiveStatus === 'DONE' && !finalEndDate) {
      res.status(422).json({ message: 'end_date é obrigatório quando o status é DONE.' })
      return
    }

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
          startDate: resolvedStartDate,
          endDate: resolvedEndDate,
          deadline: deadline !== undefined
            ? (deadline ? new Date(deadline) : null)
            : undefined,
          isInternal: is_internal,
          updatedAt: new Date(),
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
    const file = req.file!

    const body = validate(CreateAssetSchema, req.body, res)
    if (!body) return

    const projectId = req.project!.id
    const assetType = body.type ?? 'IMAGE'

    // Bug 5: parse seguro — valor inválido cai no default 10
    const parsed = parseInt(process.env.MAX_ASSETS_PER_PROJECT ?? '10', 10)
    const maxAssets = Number.isNaN(parsed) || parsed <= 0 ? 10 : parsed

    // Verificação antecipada (otimização; verificação autoritativa é dentro da transação)
    const assetCount = await prisma.projectAsset.count({ where: { projectId } })
    if (assetCount >= maxAssets) {
      res.status(422).json({
        message: `Limite de ${maxAssets} assets por projeto atingido.`,
      })
      return
    }

    const baseName = sanitizeFileName(file.originalname).replace(/\.[^.]+$/, '')
    const isImage = assetType === 'IMAGE'
    const fileName = isImage ? `${baseName}.webp` : sanitizeFileName(file.originalname)
    const assetKey = `projects/${projectId}/assets/${Date.now()}_${fileName}`

    const uploadFile = isImage
      ? {
          buffer: await sharp(file.buffer)
            .resize(800, 600, { fit: 'cover', position: 'centre' })
            .webp({ quality: 82 })
            .toBuffer(),
          mimetype: 'image/webp',
          originalname: `${baseName}.webp`,
        }
      : file

    // Bug 3: transação garante atomicidade entre contagem e criação
    let limitExceeded = false
    try {
      await uploadObjectToS3(uploadFile, assetKey)

      const asset = await prisma.$transaction(async (tx) => {
        const count = await tx.projectAsset.count({ where: { projectId } })
        if (count >= maxAssets) {
          limitExceeded = true
          throw new Error('LIMIT_EXCEEDED')
        }
        return tx.projectAsset.create({
          data: {
            projectId,
            key: assetKey,
            type: assetType,
            caption: body.caption,
            order: body.order ?? 0,
          },
        })
      })

      res.status(201).json({
        message: 'Asset adicionado com sucesso.',
        asset: formatAsset(asset),
      })
    } catch (err) {
      await deleteObjectFromS3(assetKey).catch((cleanupErr) => {
        console.error(`S3 CLEANUP FAILED for key ${assetKey}:`, cleanupErr)
      })
      if (limitExceeded) {
        res.status(422).json({
          message: `Limite de ${maxAssets} assets por projeto atingido.`,
        })
        return
      }
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
        data: { caption: body.caption, order: body.order },
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
      // Bug 2: DB primeiro — se S3 falhar, o registro já foi removido (orphan)
      // é menos grave do que ter um registro apontando para arquivo inexistente
      await prisma.projectAsset.delete({ where: { id: asset.id } })
      await deleteObjectFromS3(asset.key)
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
          id: contributor.member.id,
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
      const techs = await prisma.projectTechnology.findMany({
        where: { projectId },
        include: { technology: true },
        orderBy: { technology: { name: 'asc' } },
      })
      res.json(
        techs.map(({ usageLevel, technology }) => ({
          id: technology.id,
          name: technology.name,
          slug: technology.slug,
          type: technology.type,
          color: technology.color,
          description: technology.description,
          icon_url: technology.iconUrl,
          usage_level: usageLevel,
        }))
      )
    } catch (err) {
      console.error(err)
      res.status(500).json({ message: 'Falha ao buscar as tecnologias.' })
    }
  }) as RequestHandler,

  updateTech: (async (req, res) => {
    const params = validate(TechIdParamSchema, req.params, res)
    if (!params) return

    const body = validate(UpdateTechSchema, req.body, res)
    if (!body) return

    const projectId = req.project!.id

    try {
      await prisma.projectTechnology.update({
        where: {
          projectId_technologyId: {
            projectId,
            technologyId: params.technology_id,
          },
        },
        data: { usageLevel: body.usage_level },
      })
      res.json({ message: 'Nível de uso atualizado com sucesso.' })
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
      res.status(500).json({ message: 'Falha ao atualizar a tecnologia.' })
    }
  }) as RequestHandler<{ slug: string; technology_id: string }>,

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
