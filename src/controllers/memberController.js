import db from '../db/db.js'
import jwt from 'jsonwebtoken'
import bcrypt from 'bcryptjs'
import memberModel from '../models/memberModel.js'

const memberController = {
  login: (req, res) => {
    const { email, password } = req.body

    try {
      const member = memberModel.findByEmail(email)
      if (!member) return res.status(404).send({ message: 'Member not found' })

      const isPasswordValid = bcrypt.compareSync(password, member.password)

      if (!isPasswordValid)
        return res.status(401).send({ message: 'Invalid password' })

      const token = jwt.sign(
        {
          id: member.id,
          is_active: member.is_active,
          is_admin: member.is_admin,
        },
        process.env.JWT_SECRET,
        { expiresIn: '15m' }
      )

      res.json({ token })
    } catch (err) {
      console.error(err.message)
      res.sendStatus(503)
    }
  },

  insert: (req, res) => {
    const {
      email,
      password,
      name,
      surname,
      bio,
      course,
      avatar_url,
      entry_date,
      exit_date,
      is_active,
      github_url,
      instagram_url,
      linkedin_url,
      is_admin,
      role_id,
    } = req.body

    if (!email || !password || !name || !surname || !course)
      return res.status(400).json({ message: 'Missing required fields.' })

    if (!isSingleWord(name))
      return res.status(400).json({ error: 'Name must be a single word.' })

    if (!isSingleWord(surname))
      return res.status(400).json({ error: 'Surname must be a single word.' })

    try {
      const hashedPassword = bcrypt.hashSync(password, 8)
      const result = memberModel.save(
        email,
        hashedPassword,
        name,
        surname,
        bio,
        course,
        avatar_url,
        entry_date,
        exit_date,
        is_active,
        github_url,
        instagram_url,
        linkedin_url,
        is_admin,
        role_id
      )

      res.status(201).json({
        message: 'Member created successfully.',
        memberId: result.lastInsertRowid,
      })
    } catch (error) {
      console.error(error.message)
      if (
        error.code === 'SQLITE_CONSTRAINT_FOREIGNKEY' ||
        error.message.includes('FOREIGN KEY constraint failed')
      )
        return res.status(400).json({ message: 'Invalid role_id provided.' })

      if (error.code === 'SQLITE_CONSTRAINT_UNIQUE')
        return res.status(409).json({ message: 'Email already exists.' })
      res.status(500).json({ error: 'Failed to create member.' })
    }
  },

  get: (req, res) => {
    const { id } = req.params

    try {
      const member = memberModel.find(id)
      if (!member) return res.status(404).json({ error: 'Member not found.' })

      res.json({
        ...member,
        is_active: Boolean(member.is_active),
        is_admin: Boolean(member.is_admin),
      })
    } catch (error) {
      console.error(error.message)
      res.status(500).json({ error: 'Failed to retrieve member.' })
    }
  },

  all: (req, res) => {
    try {
      const members = memberModel.all()
      res.json(
        members.map((member) => ({
          ...member,
          is_active: Boolean(member.is_active),
        }))
      )
    } catch (error) {
      console.error(error.message)
      res.status(500).json({ error: 'Failed to retrieve members.' })
    }
  },

  update: (req, res) => {
    const { id } = req.params
    const {
      email,
      password,
      name,
      surname,
      bio,
      course,
      avatar_url,
      entry_date,
      exit_date,
      is_active,
      github_url,
      instagram_url,
      linkedin_url,
      is_admin,
      role_id,
    } = req.body

    if (!id) return res.status(400).json({ message: 'Member ID is required.' })

    if (name && !isSingleWord(name))
      return res.status(400).json({ error: 'Name must be a single word.' })

    if (surname && !isSingleWord(surname))
      return res.status(400).json({ error: 'Surname must be a single word.' })

    try {
      const checkMemberQuery = db.prepare('SELECT * FROM member WHERE id = ?')
      const existingMember = checkMemberQuery.get(id)

      if (!existingMember)
        return res.status(404).json({ message: 'Member not found.' })

      if (role_id) {
        const roleQuery = db.prepare('SELECT id FROM role WHERE id = ?')
        const validRole = roleQuery.get(role_id)
        if (!validRole)
          return res.status(400).json({ message: 'Invalid role_id provided.' })
      }

      memberModel.update(
        id,
        email,
        password,
        name,
        surname,
        bio,
        course,
        avatar_url,
        entry_date,
        exit_date,
        is_active,
        github_url,
        instagram_url,
        linkedin_url,
        is_admin,
        role_id
      )

      res.json({ message: 'Member updated successfully.' })
    } catch (error) {
      console.error(error.message)
      res.status(500).json({ error: 'Failed to update member.' })
    }
  },

  delete: (req, res) => {
    const { id } = req.params

    if (!id) return res.status(400).json({ message: 'Member ID is required.' })

    try {
      const checkMemberQuery = db.prepare('SELECT * FROM member WHERE id = ?')
      const existingMember = checkMemberQuery.get(id)

      if (!existingMember)
        return res.status(404).json({ message: 'Member not found.' })

      const deleteMemberQuery = db.prepare('DELETE FROM member WHERE id = ?')
      deleteMemberQuery.run(id)

      res.json({ message: 'Member deleted successfully.' })
    } catch (error) {
      console.error(error.message)
      res.status(500).json({ error: 'Failed to delete member.' })
    }
  },
}

const isSingleWord = (word) => {
  const singleWordRegex = /^[^\s]+$/
  return singleWordRegex.test(word)
}

export default memberController
