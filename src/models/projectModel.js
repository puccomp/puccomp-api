import db from '../db/db.js'

const projectModel = {
  save: (name, description, image_key, createdAt = null, updatedAt = null) => {
    const useCurrentDateForCreated = createdAt === null
    const useCurrentDateForUpdated = updatedAt === null

    const query = `
      INSERT INTO project (name, description, image_key, created_at, updated_at)
      VALUES (?, ?, ?, ${useCurrentDateForCreated ? 'CURRENT_DATE' : '?'}, 
                   ${useCurrentDateForUpdated ? 'CURRENT_DATE' : '?'})
    `
    const params = [name, description, image_key]
    if (!useCurrentDateForCreated) params.push(createdAt)
    if (!useCurrentDateForUpdated) params.push(updatedAt)

    const result = db.prepare(query).run(...params)
    return result.lastInsertRowid
  },

  find: (name) => db.prepare('SELECT * FROM project WHERE name = ?').get(name),

  all: () => db.prepare('SELECT * FROM project').all(),

  update: (name, description, imageKey, createdAt, updatedAt, oldName) => {
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
      .run(name, description, imageKey, createdAt, updatedAt, oldName)
    return result.changes
  },

  delete: (name) => {
    const result = db.prepare('DELETE FROM project WHERE name = ?').run(name)
    return result.changes
  },

  saveContributor: (memberID, id) => {
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

  allContributors: (id) =>
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
      .all(id),

  hasContributor: (memberID, id) => {
    return !!db
      .prepare(
        `SELECT 1 FROM contributor WHERE member_id = ? AND project_id = ?`
      )
      .get(memberID, id)
  },

  deleteContributor: (memberID, id) => {
    const result = db
      .prepare(`DELETE FROM contributor WHERE member_id = ? AND project_id = ?`)
      .run(memberID, id)

    return result.changes
  },

  hasTech: (technologyID, id) =>
    !!db
      .prepare(
        `SELECT 1 FROM project_technology WHERE technology_id = ? AND  project_id = ?`
      )
      .get(technologyID, id),

  saveTech: (technologyID, id, usageLevel) => {
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

  allTechs: (id) =>
    db
      .prepare(
        `SELECT t.id, t.name, t.icon_url, t.type, pt.usage_level
      FROM project_technology pt
      JOIN technology t ON pt.technology_id = t.id
      WHERE pt.project_id = ?`
      )
      .all(id),

  deleteTech: (technologyID, id) => {
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

export default projectModel
