import express, { Request, Response, NextFunction, Router } from 'express'
import projectsController from '../controllers/projectController.js'
import projectModel, { Project } from '../models/projectModel.js'
import { memUpload } from '../utils/uploads.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import { multerErrorHandler } from '../middlewares/errorHandlers.js'

declare global {
  namespace Express {
    export interface Request {
      project?: Project
    }
  }
}

const checkProjectExists = (
  req: Request<{ project_name: string }>,
  res: Response,
  next: NextFunction
): void => {
  const { project_name } = req.params
  try {
    const project = projectModel.find(project_name)
    if (!project) {
      res.status(404).json({ message: 'Project not found.' })
      return
    }
    req.project = project as Project
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
  checkProjectExists,
  memUpload.single('image'),
  projectsController.update
)

router.use(multerErrorHandler)

router.get('/', projectsController.all)

router.get('/:project_name', checkProjectExists, projectsController.get)

router.delete(
  '/:project_name',
  isAuth,
  checkProjectExists,
  projectsController.delete
)

router.post(
  '/:project_name/contributors',
  isAuth,
  checkProjectExists,
  projectsController.addContributor
)

router.get(
  '/:project_name/contributors',
  checkProjectExists,
  projectsController.allContributors
)

router.delete(
  '/:project_name/contributors/:member_id',
  isAuth,
  checkProjectExists,
  projectsController.removeContributor
)

router.post(
  '/:project_name/technologies',
  isAuth,
  checkProjectExists,
  projectsController.addTech
)

router.get(
  '/:project_name/technologies',
  checkProjectExists,
  projectsController.allTechs
)

router.delete(
  '/:project_name/technologies/:technology_id',
  isAuth,
  checkProjectExists,
  projectsController.removeTech
)

export default router
