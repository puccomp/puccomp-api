import express, { Request, Response, NextFunction, Router } from 'express'
import { Project } from '@prisma/client'
import projectsController from '../controllers/projectController.js'
import { memUpload } from '../utils/uploads.js'
import isAuth from '../middlewares/isAuth.js'
import { multerErrorHandler } from '../middlewares/errorHandlers.js'
import prisma from '../utils/prisma.js'

declare global {
  namespace Express {
    export interface Request {
      project?: Project
    }
  }
}

const findProjectBySlug = async (
  req: Request<{ slug: string }>,
  res: Response,
  next: NextFunction
): Promise<void> => {
  const { slug } = req.params
  try {
    const project = await prisma.project.findUnique({ where: { slug } })
    if (!project) {
      res.status(404).json({ message: 'Projeto não encontrado.' })
      return
    }
    req.project = project
    next()
  } catch (error) {
    console.error(error)
    res.status(500).json({ message: 'Erro ao buscar os dados do projeto.' })
  }
}

const router: Router = express.Router()

router.post('/', isAuth, projectsController.insert)

router.patch('/:slug', isAuth, findProjectBySlug, projectsController.update)

router.use(multerErrorHandler)

router.get('/', projectsController.all)

router.get('/:slug', findProjectBySlug, projectsController.get)

router.delete('/:slug', isAuth, findProjectBySlug, projectsController.delete)

// Contributors
router.post(
  '/:slug/contributors',
  isAuth,
  findProjectBySlug,
  projectsController.addContributor
)

router.get(
  '/:slug/contributors',
  findProjectBySlug,
  projectsController.allContributors
)

router.delete(
  '/:slug/contributors/:member_id',
  isAuth,
  findProjectBySlug,
  projectsController.removeContributor
)

// Technologies
router.post(
  '/:slug/technologies',
  isAuth,
  findProjectBySlug,
  projectsController.addTech
)

router.get(
  '/:slug/technologies',
  findProjectBySlug,
  projectsController.allTechs
)

router.delete(
  '/:slug/technologies/:technology_id',
  isAuth,
  findProjectBySlug,
  projectsController.removeTech
)

// Assets
router.post(
  '/:slug/assets',
  isAuth,
  findProjectBySlug,
  memUpload.single('file'),
  projectsController.addAsset
)

router.get(
  '/:slug/assets',
  findProjectBySlug,
  projectsController.allAssets
)

router.patch(
  '/:slug/assets/:asset_id',
  isAuth,
  findProjectBySlug,
  projectsController.updateAsset
)

router.delete(
  '/:slug/assets/:asset_id',
  isAuth,
  findProjectBySlug,
  projectsController.deleteAsset
)

export default router
