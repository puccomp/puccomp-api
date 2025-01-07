import Database from 'better-sqlite3'
import bcrypt from 'bcryptjs'
import fs from 'fs'

const databaseDir = 'data'
if (!fs.existsSync(databaseDir)) {
  fs.mkdirSync(databaseDir, { recursive: true })
  console.log(`Directory ${databaseDir} created.`)
}

const db = new Database(`${databaseDir}/database.sqlite`)

const createUserTableQuery = `
CREATE TABLE IF NOT EXISTS Users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL
);`

const createMembersTableQuery = `
CREATE TABLE IF NOT EXISTS Members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    surname TEXT NOT NULL,
    role TEXT NOT NULL,
    imageProfile TEXT,
    course TEXT NOT NULL,
    description TEXT NOT NULL,
    instagramUrl TEXT,
    githubUrl TEXT,
    linkedinUrl TEXT,
    date TEXT NOT NULL,
    isActive BOOLEAN NOT NULL
);`

const createCvApplicationsTableQuery = `
CREATE TABLE IF NOT EXISTS cv_pplications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    linkedIn TEXT,
    gitHub TEXT,
    course TEXT NOT NULL,
    period TEXT NOT NULL,
    resume TEXT NOT NULL
);`

const createProjectProposalTableQuery = `
CREATE TABLE IF NOT EXISTS project_proposals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    description TEXT NOT NULL,
    features TEXT NOT NULL,
    visual_identity TEXT NOT NULL,
    budget TEXT NOT NULL,
    date TEXT NOT NULL
);`

db.exec(createUserTableQuery)
db.exec(createMembersTableQuery)
db.exec(createCvApplicationsTableQuery)
db.exec(createProjectProposalTableQuery)

const seedDefaultUser = async () => {
  const username = process.env.DEFAULT_ADMIN_USERNAME
  const password = process.env.DEFAULT_ADMIN_PASSWORD
  try {
    const insertUserQuery = db.prepare(`
      INSERT INTO users (username, password)
      VALUES (?, ?)
    `)
    insertUserQuery.run(username, bcrypt.hashSync(password, 8))
    console.log('Default user created successfully!')
  } catch (error) {
    console.error('Error creating default user:', error.message)
  }
}

seedDefaultUser()

export default db
