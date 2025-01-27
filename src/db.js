import Database from 'better-sqlite3'
import fs from 'fs'
import { seedDefaultAdmin, seedDefaultRole } from './seedDB.js'

const databaseDir = 'data'

if (!fs.existsSync(databaseDir)) {
  fs.mkdirSync(databaseDir, { recursive: true })
  console.log(`Directory ${databaseDir} created.`)
}

const db = new Database(`${databaseDir}/database.sqlite`)
// const db = new Database(':memory:')

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

try {
  const defaultRoleId = seedDefaultRole(db)
  seedDefaultAdmin(db, defaultRoleId)
} catch (err) {
  console.error('Error during seeding process:', err.message)
}

export default db
