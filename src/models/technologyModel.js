import db from '../db/db.js'

const technologyModel = {
  find: (name) =>
    db.prepare('SELECT * FROM technology WHERE name = ?').get(name),

  all: () => db.prepare('SELECT * FROM technology').all(),

  save: (name, iconUrl = null, type) => {
    const result = db.prepare(`
      INSERT INTO technology (name, icon_url, type)
      VALUES (?, ?, ?)
    `).run(name,iconUrl, type)
    return result.lastInsertRowid
  },

  update: (name, iconUrl = null, type, id) => {
    const result = db.prepare(`
      UPDATE technology
      SET 
        name = COALESCE(?, name),
        icon_url = COALESCE(?, icon_url),
        type = COALESCE(?, type)
      WHERE id = ?
    `).run(name, iconUrl, type, id)
    return result.changes
  },

  exists: (id) => !!db.prepare('SELECT 1 FROM technology WHERE id = ?').get(id),

  delete: (id) => {
    const result = db.prepare(
      'DELETE FROM technology WHERE id = ?'
    ).run(id)
    return result.changes
  }
}

export default technologyModel
