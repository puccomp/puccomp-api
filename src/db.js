import Database from 'better-sqlite3'
import bcrypt from 'bcryptjs'
import fs from 'fs'

const databaseDir = 'data'

if (!fs.existsSync(databaseDir)) {
  fs.mkdirSync(databaseDir, { recursive: true })
  console.log(`Directory ${databaseDir} created.`)
}

// const db = new Database(`${databaseDir}/database.sqlite`);
const db = new Database(':memory:')

const createRole = `
CREATE TABLE IF NOT EXISTS role (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    level INTEGER NOT NULL CHECK (level >= 0),
    created_at DATE NOT NULL DEFAULT CURRENT_DATE,
    updated_at DATE NOT NULL DEFAULT CURRENT_DATE
);`

const createMember = `
CREATE TABLE IF NOT EXISTS member (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT NOT NULL UNIQUE, 
    password TEXT NOT NULL,
    name TEXT NOT NULL,
    surname TEXT NOT NULL,
    bio TEXT,
    course TEXT NOT NULL,
    avatar_url TEXT,
    entry_date DATE NOT NULL,
    exit_date DATE,
    is_active BOOLEAN NOT NULL CHECK (is_active IN (0, 1)),
    github_url TEXT,
    instagram_url TEXT,
    linkedin_url TEXT,
    is_admin BOOLEAN NOT NULL CHECK (is_admin IN (0, 1)),
    role_id INTEGER NOT NULL,
    FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE SET NULL
);`

const createProject = `
CREATE TABLE IF NOT EXISTS project (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL,
    image_url TEXT,
    created_at DATE NOT NULL DEFAULT CURRENT_DATE,
    updated_at DATE NOT NULL DEFAULT CURRENT_DATE
);`

const createContributor = `
CREATE TABLE IF NOT EXISTS contributor (
    member_id INTEGER NOT NULL,
    project_id INTEGER NOT NULL,
    PRIMARY KEY (member_id, project_id),
    FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);`

const createTechnology = `
CREATE TABLE IF NOT EXISTS technology (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    icon_url TEXT,
    type TEXT CHECK (type IN ('language', 'framework', 'library', 'tool', 'other')) DEFAULT 'other'
);`

const createProjectTechnology = `
CREATE TABLE IF NOT EXISTS project_technology (
    project_id INTEGER NOT NULL,
    technology_id INTEGER NOT NULL,
    usage_level TEXT NOT NULL CHECK (usage_level IN ('primary', 'secondary', 'experimental')),
    PRIMARY KEY (project_id, technology_id),
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (technology_id) REFERENCES technology (id) ON DELETE CASCADE
);`

const createCvApplication = `
CREATE TABLE IF NOT EXISTS cv_application (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    linkedin TEXT,
    github TEXT,
    course TEXT NOT NULL,
    period TEXT NOT NULL,
    resume TEXT NOT NULL
);`

const createProjectProposal = `
CREATE TABLE IF NOT EXISTS project_proposal (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    description TEXT NOT NULL,
    features TEXT,
    visual_identity TEXT,
    budget TEXT,
    date DATE NOT NULL DEFAULT CURRENT_DATE
);`

;[
  createRole,
  createMember,
  createProject,
  createTechnology,
  createContributor,
  createProjectTechnology,
  createCvApplication,
  createProjectProposal,
].forEach((query) => db.exec(query))

console.log('All tables have been created or already exist.')

const seedDefaultRole = () => {
  const defaultRole = {
    name: 'Diretor Técnico',
    description: 'Responsável técnico principal da organização',
    level: 0,
  }

  try {
    const checkRoleQuery = db.prepare(`
      SELECT id FROM role WHERE name = ?
    `)
    let role = checkRoleQuery.get(defaultRole.name)

    if (!role) {
      const insertRoleQuery = db.prepare(`
        INSERT INTO role (name, description, level, created_at, updated_at)
        VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE)
      `)

      const result = insertRoleQuery.run(
        defaultRole.name,
        defaultRole.description,
        defaultRole.level
      )

      role = { id: result.lastInsertRowid }
      console.log(`Role "${defaultRole.name}" created successfully!`)
    } else console.log(`Role "${defaultRole.name}" already exists.`)

    return role.id
  } catch (err) {
    console.error('Error creating default role:', err.message)
    throw err
  }
}

const seedDefaultAdmin = (roleId) => {
  const defaultAdmin = {
    email: process.env.DEFAULT_ADMIN_EMAIL,
    name: process.env.DEFAULT_ADMIN_NAME,
    surname: process.env.DEFAULT_ADMIN_SURNAME,
    course: process.env.DEFAULT_ADMIN_COURSE,
    password: process.env.DEFAULT_ADMIN_PASSWORD,
    entry_date: process.env.DEFAULT_ADMIN_ENTRY_DATE,
    is_active: 1,
    is_admin: 1,
    role_id: roleId,
  }

  try {
    const checkAdminQuery = db.prepare(`
      SELECT COUNT(*) AS count FROM member WHERE is_admin = 1
    `)
    const { count } = checkAdminQuery.get()

    if (count > 0) {
      console.log('Default admin already exists. No action taken.')
      return
    }

    const insertAdminQuery = db.prepare(`
      INSERT INTO member (
        email, name, surname, course, password, entry_date,
        is_active, is_admin, role_id
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `)

    insertAdminQuery.run(
      defaultAdmin.email,
      defaultAdmin.name,
      defaultAdmin.surname,
      defaultAdmin.course,
      bcrypt.hashSync(defaultAdmin.password, 8),
      defaultAdmin.entry_date,
      defaultAdmin.is_active,
      defaultAdmin.is_admin,
      defaultAdmin.role_id
    )

    console.log('Default admin created successfully!')
  } catch (err) {
    console.error('Error creating default admin:', err.message)
  }
}

try {
  const defaultRoleId = seedDefaultRole()
  seedDefaultAdmin(defaultRoleId)
} catch (err) {
  console.error('Error during seeding process:', err.message)
}

const seedProject = (name, description, imageUrl = null) => {
  try {
    const checkProjectQuery = db.prepare(
      'SELECT id FROM project WHERE name = ?'
    )
    const existingProject = checkProjectQuery.get(name)

    if (existingProject) {
      console.log(`Project "${name}" already exists.`)
      return existingProject.id
    }

    const insertProjectQuery = db.prepare(`
      INSERT INTO project (name, description, image_url, created_at, updated_at)
      VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE)
    `)

    const result = insertProjectQuery.run(name, description, imageUrl)

    console.log(`Project "${name}" created successfully!`)
    return result.lastInsertRowid
  } catch (err) {
    console.error(`Error creating project "${name}":`, err.message)
    throw err
  }
}

try {
  const projectId = seedProject(
    'Project-Alpha',
    'This is a description of Project Alpha.',
    'https://example.com/image.png'
  )
  console.log(`Seeded project with ID: ${projectId}`)
} catch (err) {
  console.error('Error during project seeding:', err.message)
}

const seedTechnology = (name, iconUrl = null) => {
  try {
    const checkTechnologyQuery = db.prepare(`
      SELECT id FROM technology WHERE name = ?
    `)
    const existingTechnology = checkTechnologyQuery.get(name)

    if (existingTechnology) {
      console.log(`Technology "${name}" already exists.`)
      return existingTechnology.id
    }

    const insertTechnologyQuery = db.prepare(`
      INSERT INTO technology (name, icon_url)
      VALUES (?, ?)
    `)
    const result = insertTechnologyQuery.run(name, iconUrl)

    console.log(`Technology "${name}" created successfully!`)
    return result.lastInsertRowid
  } catch (err) {
    console.error(`Error creating technology "${name}":`, err.message)
    throw err
  }
}

;[
  { name: 'JavaScript', icon_url: 'https://example.com/icons/javascript.png' },
  { name: 'Python', icon_url: 'https://example.com/icons/python.png' },
  { name: 'React', icon_url: 'https://example.com/icons/react.png' },
  { name: 'Node.js', icon_url: 'https://example.com/icons/nodejs.png' },
  { name: 'Django', icon_url: 'https://example.com/icons/django.png' },
  { name: 'Vue.js', icon_url: 'https://example.com/icons/vuejs.png' },
].forEach((tech) => {
  seedTechnology(tech.name, tech.icon_url)
})

export default db
