import express from 'express'
import bcrypt from 'bcryptjs'
import jwt from 'jsonwebtoken'
import db from '../db.js'
import authMiddleware from '../middleware/authMiddleware.js'

const router = express.Router()

// AUTHENTICATION
router.post('/login', (req, res) => {
  const { username, password } = req.body
  try {
    const stmt = db.prepare('SELECT * FROM users WHERE username = ?')
    const user = stmt.get(username)

    if (!user) return res.status(404).send({ message: 'User not found' })

    const isPasswordValid = bcrypt.compareSync(password, user.password)

    if (!isPasswordValid)
      return res.status(401).send({ message: 'Invalid password' })

    const token = jwt.sign({ id: user.id }, process.env.JWT_SECRET, {
      expiresIn: '24h',
    })
    res.json({ token })
  } catch (err) {
    console.log(err.message)
    res.sendStatus(503)
  }
})

// SAVE
router.post('/register', (req, res) => {
  const { username, password } = req.body
  const hashedPassword = bcrypt.hashSync(password, 8)
  try {
    const stmt = db.prepare(
      `INSERT INTO users (username, password) VALUES (?, ?)`
    )
    const result = stmt.run(username, hashedPassword)
    const token = jwt.sign(
      { id: result.lastInsertRowid },
      process.env.JWT_SECRET,
      { expiresIn: '24h' }
    )
    res.json({ token })
  } catch (err) {
    console.log(err.message)
    res.sendStatus(503)
  }
})

// GET ALL
router.get('/', authMiddleware, (req, res) => {
  try {
    const stmt = db.prepare('SELECT id, username FROM users')
    const users = stmt.all()

    res.json(users)
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to retrieve users.' })
  }
})

export default router
