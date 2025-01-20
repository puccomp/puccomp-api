import express from 'express'
import database from '../db.js'
import authMiddleware from '../middleware/authMiddleware.js'

const router = express.Router()

// FIND ALL
router.get('/', (req, res) => {
  try {
    const getAllMembers = database.prepare('SELECT * FROM Members')
    const members = getAllMembers.all()
    res.json(members)
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to retrieve members.' })
  }
})

// FIND BY ID
router.get('/:id', (req, res) => {
  const { id } = req.params

  try {
    const getMemberById = database.prepare('SELECT * FROM Members WHERE id = ?')
    const member = getMemberById.get(id)

    if (!member) return res.status(404).json({ error: 'Member not found.' })

    res.json(member)
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to retrieve member.' })
  }
})

// SAVE
router.post('/', authMiddleware, (req, res) => {
  const {
    name,
    surname,
    role,
    imageProfile,
    course,
    description,
    instagramUrl,
    githubUrl,
    linkedinUrl,
    date,
    isActive,
  } = req.body

  const dateRegex = /^\d{4}\/(1|2)$/
  const singleWordRegex = /^[^\s]+$/

  if (!dateRegex.test(date))
    return res
      .status(400)
      .json({ error: 'Invalid date format. Use format YYYY/1 or YYYY/2.' })

  if (!singleWordRegex.test(name))
    return res.status(400).json({ error: 'Name must be a single word.' })

  if (!singleWordRegex.test(surname))
    return res.status(400).json({ error: 'Surname must be a single word.' })

  try {
    const insertMember = database.prepare(`
      INSERT INTO Members (name, surname, role, imageProfile, course, description, instagramUrl, githubUrl, linkedinUrl, date, isActive)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `)

    const result = insertMember.run(
      name,
      surname,
      role,
      imageProfile || null,
      course,
      description,
      instagramUrl || null,
      githubUrl || null,
      linkedinUrl || null,
      date,
      isActive ? 1 : 0
    )

    res.status(201).json({ id: result.lastInsertRowid })
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to create member.' })
  }
})

// UPDATE
router.put('/:id', authMiddleware, (req, res) => {
  const { id } = req.params
  const {
    name,
    surname,
    role,
    imageProfile,
    course,
    description,
    instagramUrl,
    githubUrl,
    linkedinUrl,
    date,
    isActive,
  } = req.body

  try {
    const updateMember = database.prepare(`
      UPDATE Members
      SET name = ?, surname = ?, role = ?, imageProfile = ?, course = ?, description = ?, instagramUrl = ?, githubUrl = ?, linkedinUrl = ?, date = ?, isActive = ?
      WHERE id = ?
    `)

    const result = updateMember.run(
      name,
      surname,
      role,
      imageProfile || null,
      course,
      description,
      instagramUrl || null,
      githubUrl || null,
      linkedinUrl || null,
      date,
      isActive ? 1 : 0,
      id
    )

    if (result.changes === 0)
      return res.status(404).json({ error: 'Member not found.' })

    res.json({ message: 'Member updated successfully.' })
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to update member.' })
  }
})

// DELETE
router.delete('/:id', authMiddleware, (req, res) => {
  const { id } = req.params

  try {
    const deleteMember = database.prepare('DELETE FROM Members WHERE id = ?')
    const result = deleteMember.run(id)

    if (result.changes === 0)
      return res.status(404).json({ error: 'Member not found.' })

    res.json({ message: 'Member deleted successfully.' })
  } catch (error) {
    console.error(error.message)
    res.status(500).json({ error: 'Failed to delete member.' })
  }
})

export default router
