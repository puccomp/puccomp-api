import express from 'express'
import projectsController from '../controllers/projectController.js'
import projectModel from '../models/projectModel.js'
import { memUpload } from '../utils/uploads.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'

const checkProjectExists = (req, res, next) => {
  const { project_name } = req.params
  const project = projectModel.find(project_name)
  if (!project) return res.status(404).json({ message: 'Project not found.' })
  req.project = project
  next()
}

const router = express.Router()

router.post('/', isAuth, memUpload.single('image'), projectsController.insert)

router.get('/', projectsController.all)

router.get('/:project_name', checkProjectExists, projectsController.get)

router.put(
  '/:project_name',
  isAuth,
  checkProjectExists,
  memUpload.single('image'),
  projectsController.update
)

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
