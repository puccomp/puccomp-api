import express from 'express'
import jwt from 'jsonwebtoken'
import bcrypt from 'bcryptjs'
import db from '../db.js'

// MIDDLEWARES
import authMiddleware from '../middlewares/authMiddleware.js'
import adminMiddleware from '../middlewares/adminMiddleware.js'

const router = express.Router()

// LOGIN
router.post('/login', (req, res) => {
  const { email, password } = req.body

  try {
    const stmt = db.prepare('SELECT * FROM member WHERE email = ?')
    const member = stmt.get(email)

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
})

// SAVE
router.post('/', authMiddleware, adminMiddleware, (req, res) => {
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

  const singleWordRegex = /^[^\s]+$/

  if (!singleWordRegex.test(name))
    return res.status(400).json({ error: 'Name must be a single word.' })

  if (!singleWordRegex.test(surname))
    return res.status(400).json({ error: 'Surname must be a single word.' })

  try {
    const checkEmailQuery = db.prepare('SELECT id FROM member WHERE email = ?')
    const existingMember = checkEmailQuery.get(email)
    if (existingMember)
      return res.status(409).json({ message: 'Email already exists.' })

    const roleQuery = db.prepare('SELECT id FROM role WHERE id = ?')
    const validRole = roleQuery.get(role_id)
    if (!validRole)
      return res.status(400).json({ message: 'Invalid role_id provided.' })

    const hashedPassword = bcrypt.hashSync(password, 8)

    const finalEntryDate = entry_date || new Date().toISOString().split('T')[0]
    const finalExitDate = exit_date || null
    const finalIsActive = is_active !== undefined ? (is_active ? 1 : 0) : 1
    const finalIsAdmin = is_admin !== undefined ? (is_admin ? 1 : 0) : 0

    const insertMemberQuery = db.prepare(`
      INSERT INTO member (
        email, password, name, surname, bio, course, avatar_url,
        entry_date, exit_date, is_active, github_url, instagram_url, linkedin_url, is_admin, role_id
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `)

    const result = insertMemberQuery.run(
      email,
      hashedPassword,
      name,
      surname,
      bio || null,
      course,
      avatar_url || null,
      finalEntryDate,
      finalExitDate,
      finalIsActive,
      github_url || null,
      instagram_url || null,
      linkedin_url || null,
      finalIsAdmin,
      role_id
    )

    res.status(201).json({
      message: 'Member created successfully.',
      memberId: result.lastInsertRowid,
    })
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to create member.' })
  }
})

// FIND ALL
router.get('/', (req, res) => {
  try {
    const getAllMembers = db.prepare(`
      SELECT 
        m.id, 
        m.name, 
        m.surname, 
        m.avatar_url,
        m.bio, 
        m.course, 
        m.entry_date, 
        m.exit_date, 
        m.is_active, 
        m.github_url, 
        m.instagram_url, 
        m.linkedin_url,
        r.name AS role
      FROM member AS m
      INNER JOIN role AS r ON m.role_id = r.id
    `)
    const members = getAllMembers.all()
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
})

// FIND BY ID
router.get('/:id', (req, res) => {
  const { id } = req.params

  try {
    const getMemberById = db.prepare(`
      SELECT 
        m.id, 
        m.name, 
        m.surname, 
        m.email,
        m.avatar_url,
        m.bio, 
        m.course, 
        m.entry_date, 
        m.exit_date, 
        m.is_active, 
        m.github_url, 
        m.instagram_url, 
        m.linkedin_url, 
        m.is_admin,
        r.name AS role
      FROM member AS m
      INNER JOIN role AS r ON m.role_id = r.id
      WHERE m.id = ?
    `)
    const member = getMemberById.get(id)

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
})

// UPDATE
router.put('/:id', authMiddleware, adminMiddleware, (req, res) => {
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

  const singleWordRegex = /^[^\s]+$/
  if (name && !singleWordRegex.test(name))
    return res.status(400).json({ error: 'Name must be a single word.' })

  if (surname && !singleWordRegex.test(surname))
    return res.status(400).json({ error: 'Surname must be a single word.' })

  try {
    const checkMemberQuery = db.prepare('SELECT * FROM member WHERE id = ?')
    const existingMember = checkMemberQuery.get(id)

    if (!existingMember)
      return res.status(404).json({ message: 'Member not found.' })

    const updateFields = {
      email: email || existingMember.email,
      password: password
        ? bcrypt.hashSync(password, 8)
        : existingMember.password,
      name: name || existingMember.name,
      surname: surname || existingMember.surname,
      bio: bio || existingMember.bio,
      course: course || existingMember.course,
      avatar_url: avatar_url || existingMember.avatar_url,
      entry_date: entry_date || existingMember.entry_date,
      exit_date: exit_date || existingMember.exit_date,
      is_active:
        is_active !== undefined
          ? is_active
            ? 1
            : 0
          : existingMember.is_active,
      github_url: github_url || existingMember.github_url,
      instagram_url: instagram_url || existingMember.instagram_url,
      linkedin_url: linkedin_url || existingMember.linkedin_url,
      is_admin:
        is_admin !== undefined ? (is_admin ? 1 : 0) : existingMember.is_admin,
      role_id: role_id || existingMember.role_id,
    }

    if (role_id) {
      const roleQuery = db.prepare('SELECT id FROM role WHERE id = ?')
      const validRole = roleQuery.get(role_id)
      if (!validRole)
        return res.status(400).json({ message: 'Invalid role_id provided.' })
    }

    const updateMemberQuery = db.prepare(`
      UPDATE member SET 
        email = ?, 
        password = ?, 
        name = ?, 
        surname = ?, 
        bio = ?, 
        course = ?, 
        avatar_url = ?, 
        entry_date = ?, 
        exit_date = ?, 
        is_active = ?, 
        github_url = ?, 
        instagram_url = ?, 
        linkedin_url = ?, 
        is_admin = ?, 
        role_id = ?
      WHERE id = ?
    `)

    updateMemberQuery.run(
      updateFields.email,
      updateFields.password,
      updateFields.name,
      updateFields.surname,
      updateFields.bio,
      updateFields.course,
      updateFields.avatar_url,
      updateFields.entry_date,
      updateFields.exit_date,
      updateFields.is_active,
      updateFields.github_url,
      updateFields.instagram_url,
      updateFields.linkedin_url,
      updateFields.is_admin,
      updateFields.role_id,
      id
    )

    res.json({ message: 'Member updated successfully.' })
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to update member.' })
  }
})

// DELETE
router.delete('/:id', authMiddleware, adminMiddleware, (req, res) => {
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
})

export default router
