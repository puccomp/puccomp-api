import { RequestHandler } from 'express'
import jwt from 'jsonwebtoken'
import bcrypt from 'bcryptjs'
import { TokenPayload } from '../middlewares/isAuth.js'
import memberModel, { Member, MemberData } from '../models/memberModel.js'

type CreateMemberDTO = Omit<Member, 'id'>

const memberController = {
  login: ((req, res) => {
    try {
      const { email, password } = req.body
      if (!email || !password) {
        res.status(400).json({ message: 'Email and password are required.' })
        return
      }
      const member = memberModel.findByEmail(email)
      if (!member) {
        res.status(404).send({ message: 'Member not found' })
        return
      }

      const isPasswordValid = bcrypt.compareSync(password, member.password)
      if (!isPasswordValid) {
        res.status(401).send({ message: 'Invalid password' })
        return
      }

      const tokenPayload: TokenPayload = {
        id: member.id,
        is_active: Boolean(member.is_active),
        is_admin: Boolean(member.is_admin),
      }
      const token: string = jwt.sign(
        tokenPayload,
        process.env.JWT_SECRET_KEY!,
        {
          expiresIn: '15m',
        }
      )

      res.json({ token })
    } catch (err) {
      const error = err as Error
      console.error(error.message)
      res.sendStatus(503)
    }
  }) as RequestHandler,

  insert: ((req, res) => {
    try {
      const { email, password, name, surname, course } =
        req.body as CreateMemberDTO

      if (!email || !password || !name || !surname || !course) {
        res.status(400).json({ message: 'Missing required fields.' })
        return
      }

      if (!isSingleWord(name)) {
        res.status(400).json({ error: 'Name must be a single word.' })
        return
      }

      if (!isSingleWord(surname)) {
        res.status(400).json({ error: 'Surname must be a single word.' })
        return
      }
      const hashedPassword = bcrypt.hashSync(password, 8)
      const result = memberModel.save({ ...req.body, password: hashedPassword })

      res.status(201).json({
        message: 'Member created successfully.',
        memberId: result.lastInsertRowid,
      })
    } catch (err) {
      const error = err as Error & { code?: string }
      console.error(error.message)
      if (
        error.code === 'SQLITE_CONSTRAINT_FOREIGNKEY' ||
        error.message.includes('FOREIGN KEY constraint failed')
      ) {
        res.status(400).json({ message: 'Invalid role id provided.' })
        return
      }
      if (error.code === 'SQLITE_CONSTRAINT_UNIQUE') {
        res.status(409).json({ message: 'Email already exists.' })
        return
      }
      res.status(500).json({ error: 'Failed to create member.' })
    }
  }) as RequestHandler<{}, {}, CreateMemberDTO>,

  get: ((req, res) => {
    try {
      const id = parseInt(req.params.id, 10)
      if (isNaN(id)) {
        res.status(400).json({ error: 'Invalid id format.' })
        return
      }

      const member = memberModel.find(id)
      if (!member) {
        res.status(404).json({ error: 'Member not found.' })
        return
      }

      res.json({
        ...member,
        is_active: Boolean(member.is_active),
        is_admin: Boolean(member.is_admin),
      })
    } catch (error) {
      console.error((error as Error).message)
      res.status(500).json({ error: 'Failed to retrieve member.' })
    }
  }) as RequestHandler<{ id: string }>,

  all: ((_req, res) => {
    try {
      const members = memberModel.all()
      res.json(
        members.map((member) => ({
          ...member,
          is_active: Boolean(member.is_active),
        }))
      )
    } catch (error) {
      console.error((error as Error).message)
      res.status(500).json({ error: 'Failed to retrieve members.' })
    }
  }) as RequestHandler,

  update: ((req, res) => {
    try {
      const id = parseInt(req.params.id, 10)
      if (isNaN(id)) {
        res.status(400).json({ error: 'Invalid id format.' })
        return
      }

      const dataToUpdate = req.body as Partial<MemberData>
      if (Object.keys(dataToUpdate).length === 0) {
        res.status(400).json({ message: 'No fields to update provided.' })
        return
      }

      const existingMember = memberModel.find(id)
      if (!existingMember) {
        res.status(404).json({ message: 'Member not found.' })
        return
      }

      if (dataToUpdate.password) {
        dataToUpdate.password = bcrypt.hashSync(dataToUpdate.password, 8)
      }
      if (dataToUpdate.name && !isSingleWord(dataToUpdate.name)) {
        res.status(400).json({ error: 'Name must be a single word.' })
        return
      }
      if (dataToUpdate.surname && !isSingleWord(dataToUpdate.surname)) {
        res.status(400).json({ error: 'Surname must be a single word.' })
        return
      }

      const changes = memberModel.update(id, dataToUpdate)
      if (changes === 0) {
        res
          .status(304)
          .json({ message: 'Data is the same, no changes were made.' })
        return
      }

      res.json({ message: 'Member updated successfully.' })
    } catch (error) {
      console.error((error as Error).message)
      res.status(500).json({ error: 'Failed to update member.' })
    }
  }) as RequestHandler<{ id: string }, {}, Partial<CreateMemberDTO>>,

  delete: ((req, res) => {
    try {
      const id = parseInt(req.params.id, 10)
      if (isNaN(id)) {
        res.status(400).json({ error: 'Invalid id format.' })
        return
      }

      const result = memberModel.delete(id)
      if (result.changes === 0) {
        res.status(404).json({ message: 'Member not found.' })
        return
      }

      res.json({ message: 'Member deleted successfully.' })
    } catch (error) {
      console.error((error as Error).message)
      res.status(500).json({ error: 'Failed to delete member.' })
    }
  }) as RequestHandler<{ id: string }>,
}

const isSingleWord = (word: string): boolean => {
  const singleWordRegex = /^[^\s]+$/
  return singleWordRegex.test(word)
}

export default memberController
