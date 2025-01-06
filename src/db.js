import Database from 'better-sqlite3'
import bcrypt from 'bcryptjs'

const db = new Database(':memory:')

// Table Creation Queries
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

// INITIAL INSERTS

const insertMemberQuery = `
INSERT INTO Members (name, surname, role, imageProfile, course, description, instagramUrl, githubUrl, linkedinUrl, date, isActive)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);`

db.prepare(insertMemberQuery).run(
  'John',
  'Doe',
  'Developer',
  null,
  'Computer Science',
  'Enthusiastic developer',
  'https://instagram.com/johndoe',
  'https://github.com/johndoe',
  'https://linkedin.com/in/johndoe',
  '2025-01-01',
  1
)

db.prepare(insertMemberQuery).run(
  'Jane',
  'Smith',
  'Designer',
  null,
  'Design',
  'Creative designer focused on UI/UX',
  'https://instagram.com/janesmith',
  'https://github.com/janesmith',
  'https://linkedin.com/in/janesmith',
  '2025-01-02',
  1
)

const insertUserQuery = `
INSERT INTO Users (username, password)
VALUES (?, ?);`

db.prepare(insertUserQuery).run('aaa', bcrypt.hashSync('aaa', 8))

export default db
