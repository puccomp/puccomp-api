import express, { Router } from 'express'
import authController from '../controllers/authController.js'
import isAuth from '../middlewares/isAuth.js'
import isAdmin from '../middlewares/isAdmin.js'

const router: Router = express.Router()

router.post('/login', authController.login)
router.post('/invite', isAuth, isAdmin, authController.invite)
router.post('/accept-invite', authController.acceptInvite)
router.get('/me', isAuth, authController.me)

export default router
