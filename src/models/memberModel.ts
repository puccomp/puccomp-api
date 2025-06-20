import db from '../db/db.js'

interface Member {
  id: number
  email: string
  password: string
  name: string
  surname: string
  bio: string | null
  course: string
  avatar_url: string | null
  entry_date: string
  exit_date: string | null
  is_active: 1 | 0
  is_admin: 1 | 0
  github_url: string | null
  instagram_url: string | null
  linkedin_url: string | null
  role_id: number
}

interface MemberWithRole extends Omit<Member, 'password'> {
  role: string
}

type MemberData = Omit<Member, 'id'>

const memberModel = {
  save: (data: Partial<MemberData>): { lastInsertRowid: number | bigint } => {
    const {
      email,
      password,
      name,
      surname,
      bio = null,
      course,
      avatar_url = null,
      entry_date = new Date().toISOString().split('T')[0],
      exit_date = null,
      is_active = 1,
      github_url = null,
      instagram_url = null,
      linkedin_url = null,
      is_admin = 0,
      role_id,
    } = data

    const insertMemberQuery = db.prepare(`
      INSERT INTO member (
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
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `)

    const finalEntryDate = entry_date || new Date().toISOString().split('T')[0]
    const finalExitDate = exit_date || null
    const finalIsActive = is_active !== undefined ? (is_active ? 1 : 0) : 1
    const finalIsAdmin = is_admin !== undefined ? (is_admin ? 1 : 0) : 0

    return insertMemberQuery.run(
      email,
      password,
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
  },

  all: (): MemberWithRole[] => {
    const getAllMembersQuery = db.prepare<[]>(`
      SELECT 
        m.id, m.name, m.surname, m.avatar_url, m.bio, m.course, m.entry_date, 
        m.exit_date, m.is_active, m.github_url, m.instagram_url, 
        m.linkedin_url, r.name AS role
      FROM member AS m
      INNER JOIN role AS r ON m.role_id = r.id
    `)
    return getAllMembersQuery.all() as MemberWithRole[]
  },

  find: (id: number): MemberWithRole | undefined => {
    const getMemberByIdQuery = db.prepare<[number]>(`
      SELECT 
        m.id, m.name, m.surname, m.email, m.avatar_url, m.bio, m.course, 
        m.entry_date, m.exit_date, m.is_active, m.github_url, 
        m.instagram_url, m.linkedin_url, m.is_admin, r.name AS role
      FROM member AS m
      INNER JOIN role AS r ON m.role_id = r.id
      WHERE m.id = ?
    `)
    return getMemberByIdQuery.get(id) as MemberWithRole | undefined
  },

  findByEmail: (email: string): Member | undefined => {
    return db
      .prepare<[string]>('SELECT * FROM member WHERE email = ?')
      .get(email) as Member | undefined
  },

  update: (id: number, data: Partial<Omit<Member, 'id'>>): number => {
    const fields = Object.keys(data).filter((key) => key !== 'id')
    if (fields.length === 0) return 0

    const setClause = fields.map((field) => `${field} = ?`).join(', ')
    const values = fields.map((field) => data[field as keyof typeof data])

    const query = db.prepare(`UPDATE member SET ${setClause} WHERE id = ?`)
    const result = query.run(...values, id)
    return result.changes
  },

  delete: (id: number) => {
    const deleteMemberQuery = db.prepare(`DELETE FROM member WHERE id = ?`)
    return deleteMemberQuery.run(id)
  },

  exists: (id: number) =>
    !!db.prepare('SELECT 1 FROM member WHERE id = ?').get(id),
}

export default memberModel
export type { Member, MemberWithRole, MemberData }
