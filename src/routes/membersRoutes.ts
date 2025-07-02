import express, { Router } from 'express'
import memberController from '../controllers/memberController.js'

// MIDDLEWARES
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'

const router: Router = express.Router()

router.post('/login', memberController.login)

router.post('/', isAuth, isAdmin, memberController.insert)

router.get('/', memberController.all)

router.get('/:id', memberController.get)

router.put('/:id', isAuth, isAdmin, memberController.update)

router.delete('/:id', isAuth, isAdmin, memberController.delete)

export default router
