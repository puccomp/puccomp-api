import db from '../db/db.js'

const memberModel = {
  save: (
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
  ) => {
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

  all: () => {
    const getAllMembersQuery = db.prepare(`
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
      INNER JOIN role AS r 
      ON m.role_id = r.id
    `)

    return getAllMembersQuery.all()
  },

  find: (id) => {
    const getMemberByIdQuery = db.prepare(`
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
      INNER JOIN role AS r 
      ON m.role_id = r.id
      WHERE m.id = ?
    `)

    return getMemberByIdQuery.get(id)
  },

  delete: (id) => {
    const deleteMemberQuery = db.prepare(`
      DELETE FROM member
      WHERE id = ?
    `)

    return deleteMemberQuery.run(id)
  },

  exists: (id) => !!db.prepare('SELECT 1 FROM member WHERE id = ?').get(id),

  findByEmail: (email) =>
    db.prepare('SELECT * FROM member WHERE email = ?').get(email),

  update: (
    id,
    email,
    password,
    name,
    surname,
    bio,
    course,
    avatarUrl,
    entryDate,
    exitDate,
    isActive,
    githubUrl,
    instagramUrl,
    linkedinUrl,
    isAdmin,
    roleID
  ) => {
    const result = db
      .prepare(
        `UPDATE member SET email = COALESCE(?, email),password = COALESCE(?, password), name = COALESCE(?, name), surname = COALESCE(?, surname), bio = COALESCE(?, bio), course = COALESCE(?, course), avatar_url = COALESCE(?, avatar_url), entry_date = COALESCE(?, entry_date), exit_date = COALESCE(?, exit_date), is_active = COALESCE(?, is_active), github_url = COALESCE(?, github_url), instagram_url = COALESCE(?, instagram_url),  linkedin_url = COALESCE(?, linkedin_url), is_admin = COALESCE(?, is_admin), role_id = COALESCE(?, role_id) WHERE id = ?`
      )
      .run(
        email,
        password,
        name,
        surname,
        bio,
        course,
        avatarUrl,
        entryDate,
        exitDate,
        isActive,
        githubUrl,
        instagramUrl,
        linkedinUrl,
        isAdmin,
        roleID,
        id
      )
    return result.changes
  },
}

export default memberModel
