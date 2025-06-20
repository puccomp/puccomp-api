import db from '../db/db.js'
import { TechnologyType } from '../utils/enums.js'

interface Technology {
  id: number
  name: string
  icon_url: string | null
  type: TechnologyType
}
type TechnologyData = Partial<Omit<Technology, 'id'>>

const technologyModel = {
  find: (name: string): Technology | undefined =>
    db.prepare('SELECT * FROM technology WHERE name = ?').get(name) as
      | Technology
      | undefined,

  all: (): Technology[] =>
    db.prepare('SELECT * FROM technology').all() as Technology[],

  save: (data: Omit<TechnologyData, 'id'>): number | bigint => {
    const result = db
      .prepare(
        `
      INSERT INTO technology (name, icon_url, type)
      VALUES (?, ?, ?)
    `
      )
      .run(data.name, data.icon_url, data.type)
    return result.lastInsertRowid
  },

  update: (id: number, data: TechnologyData): number => {
    const fields = Object.keys(data).filter(
      (key) => data[key as keyof TechnologyData] !== undefined
    )
    if (fields.length === 0) return 0

    const setClause = fields.map((field) => `${field} = ?`).join(', ')
    const values = fields.map((field) => data[field as keyof TechnologyData])

    const query = db.prepare(`UPDATE technology SET ${setClause} WHERE id = ?`)
    const result = query.run(...values, id)
    return result.changes
  },

  exists: (id: number): boolean =>
    !!db.prepare('SELECT 1 FROM technology WHERE id = ?').get(id),

  delete: (id: number): number => {
    const result = db.prepare('DELETE FROM technology WHERE id = ?').run(id)
    return result.changes
  },
}

export default technologyModel
