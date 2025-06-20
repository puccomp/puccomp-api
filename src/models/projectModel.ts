import db from '../db/db.js'
import { TechnologyUsageLevel } from '../utils/enums.js'

interface Project {
  id: number
  name: string
  description: string
  image_key: string | null
  created_at: string
  updated_at: string
}

interface ProjectContributor {
  member_id: number
  name: string
  surname: string
  github_url: string | null
  linkedin_url: string | null
  instagram_url: string | null
  avatar_url: string | null
  is_active: 1 | 0
}

interface ProjectTechnology {
  id: number
  name: string
  icon_url: string | null
  type: string
  usage_level: TechnologyUsageLevel
}

type ProjectData = Partial<Omit<Project, 'id'>>

const projectModel = {
  save: (data: Omit<ProjectData, 'id'>): number | bigint => {
    const {
      name,
      description,
      image_key,
      created_at = null,
      updated_at = null,
    } = data

    const useCurrentDateForCreated = created_at === null
    const useCurrentDateForUpdated = updated_at === null

    const query = `
      INSERT INTO project (name, description, image_key, created_at, updated_at)
      VALUES (?, ?, ?, ${useCurrentDateForCreated ? 'CURRENT_DATE' : '?'}, 
                   ${useCurrentDateForUpdated ? 'CURRENT_DATE' : '?'})
    `
    const params = [name, description, image_key]
    if (!useCurrentDateForCreated) params.push(created_at)
    if (!useCurrentDateForUpdated) params.push(updated_at)

    const result = db.prepare(query).run(...params)
    return result.lastInsertRowid
  },

  find: (name: string) =>
    db.prepare('SELECT * FROM project WHERE name = ?').get(name),

  all: (): Project[] => db.prepare('SELECT * FROM project').all() as Project[],

  update: (oldName: string, data: ProjectData): number => {
    const result = db
      .prepare(
        `
      UPDATE project 
      SET 
        name = COALESCE(?, name),
        description = COALESCE(?, description),
        image_key = COALESCE(?, image_key),
        created_at = COALESCE(?, created_at),
        updated_at = COALESCE(?, updated_at)
      WHERE name = ?
    `
      )
      .run(
        data.name,
        data.description,
        data.image_key,
        data.created_at,
        data.updated_at,
        oldName
      )
    return result.changes
  },

  delete: (name: string) => {
    const result = db.prepare('DELETE FROM project WHERE name = ?').run(name)
    return result.changes
  },

  saveContributor: (memberID: number, id: number): number => {
    const result = db
      .prepare(
        `
        INSERT INTO contributor (member_id, project_id)
        VALUES (?, ?)
      `
      )
      .run(memberID, id)

    return result.changes
  },

  allContributors: (id: number): ProjectContributor[] =>
    db
      .prepare(
        `
        SELECT 
          m.id AS member_id, 
          m.name, 
          m.surname, 
          m.github_url, 
          m.linkedin_url, 
          m.instagram_url, 
          m.avatar_url,
          m.is_active
        FROM contributor c
        JOIN member m ON c.member_id = m.id
        WHERE c.project_id = ?
      `
      )
      .all(id) as ProjectContributor[],

  hasContributor: (memberID: number, id: number): boolean => {
    return !!db
      .prepare(
        `SELECT 1 FROM contributor WHERE member_id = ? AND project_id = ?`
      )
      .get(memberID, id)
  },

  deleteContributor: (memberID: number, id: number): number => {
    const result = db
      .prepare(`DELETE FROM contributor WHERE member_id = ? AND project_id = ?`)
      .run(memberID, id)

    return result.changes
  },

  hasTech: (technologyID: number, id: number): boolean =>
    !!db
      .prepare(
        `SELECT 1 FROM project_technology WHERE technology_id = ? AND  project_id = ?`
      )
      .get(technologyID, id),

  saveTech: (
    technologyID: number,
    id: number,
    usageLevel: TechnologyUsageLevel
  ): number => {
    const result = db
      .prepare(
        `
        INSERT INTO project_technology (project_id, technology_id, usage_level)
        VALUES (?, ?, ?)
      `
      )
      .run(id, technologyID, usageLevel)
    return result.changes
  },

  allTechs: (id: number): ProjectTechnology[] =>
    db
      .prepare(
        `SELECT t.id, t.name, t.icon_url, t.type, pt.usage_level
      FROM project_technology pt
      JOIN technology t ON pt.technology_id = t.id
      WHERE pt.project_id = ?`
      )
      .all(id) as ProjectTechnology[],

  deleteTech: (technologyID: number, id: number): number => {
    const result = db
      .prepare(
        `
            DELETE FROM project_technology 
            WHERE project_id = ? AND technology_id = ?
          `
      )
      .run(id, technologyID)
    return result.changes
  },
}

export type { Project, ProjectContributor, ProjectTechnology, ProjectData }
export default projectModel
