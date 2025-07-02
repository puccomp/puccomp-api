import express, { Request, Response, NextFunction, Router } from 'express'
import { Project } from '@prisma/client'
import projectsController from '../controllers/projectController.js'
import { memUpload } from '../utils/uploads.js'

// MIDDLEWARES
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

const findProjectByName = async (
  req: Request<{ project_name: string }>,
  res: Response,
  next: NextFunction
): Promise<void> => {
  const { project_name } = req.params
  try {
    const project = await prisma.project.findUnique({
      where: { name: project_name },
    })
    if (!project) {
      res.status(404).json({ message: 'Project not found.' })
      return
    }
    req.project = project
    next()
  } catch (error) {
    console.error(error)
    res.status(500).json({ message: 'Error while fetching project data.' })
  }
}

const router: Router = express.Router()

router.post('/', isAuth, memUpload.single('image'), projectsController.insert)

router.put(
  '/:project_name',
  isAuth,
  findProjectByName,
  memUpload.single('image'),
  projectsController.update
)

router.use(multerErrorHandler)

router.get('/', projectsController.all)

router.get('/:project_name', findProjectByName, projectsController.get)

router.delete(
  '/:project_name',
  isAuth,
  findProjectByName,
  projectsController.delete
)

router.post(
  '/:project_name/contributors',
  isAuth,
  findProjectByName,
  projectsController.addContributor
)

router.get(
  '/:project_name/contributors',
  findProjectByName,
  projectsController.allContributors
)

router.delete(
  '/:project_name/contributors/:member_id',
  isAuth,
  findProjectByName,
  projectsController.removeContributor
)

router.post(
  '/:project_name/technologies',
  isAuth,
  findProjectByName,
  projectsController.addTech
)

router.get(
  '/:project_name/technologies',
  findProjectByName,
  projectsController.allTechs
)

router.delete(
  '/:project_name/technologies/:technology_id',
  isAuth,
  findProjectByName,
  projectsController.removeTech
)

export default router
