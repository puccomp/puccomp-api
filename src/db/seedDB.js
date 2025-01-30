import bcrypt from 'bcryptjs'

export const seedDefaultRole = (db) => {
  try {
    const defaultRole = {
      name: 'Diretor Técnico',
      description: 'Responsável técnico principal da organização',
      level: 0,
    }

    const checkRoleQuery = db.prepare('SELECT id FROM role WHERE name = ?')
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
    } else {
      console.log(`Role "${defaultRole.name}" already exists.`)
    }

    return role.id
  } catch (err) {
    console.error('Error creating default role:', err.message)
    throw err
  }
}

export const seedDefaultAdmin = (db, roleId) => {
  try {
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

export const seedProject = (db) => {
  try {
    const project = {
      name: 'Project-Alpha',
      description: 'This is a description of Project Alpha.',
      imageUrl: 'https://example.com/image.png',
    }

    const checkProjectQuery = db.prepare(
      'SELECT id FROM project WHERE name = ?'
    )
    const existingProject = checkProjectQuery.get(project.name)

    if (existingProject) {
      console.log(`Project "${project.name}" already exists.`)
      return existingProject.id
    }

    const insertProjectQuery = db.prepare(`
      INSERT INTO project (name, description, image_url, created_at, updated_at)
      VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE)
    `)

    const result = insertProjectQuery.run(
      project.name,
      project.description,
      project.imageUrl
    )

    console.log(`Project "${project.name}" created successfully!`)
    return result.lastInsertRowid
  } catch (err) {
    console.error('Error creating project:', err.message)
    throw err
  }
}

export const seedTechnologies = (db) => {
  try {
    const technologies = [
      {
        name: 'JavaScript',
        icon_url: 'https://example.com/icons/javascript.png',
      },
      { name: 'Python', icon_url: 'https://example.com/icons/python.png' },
      { name: 'React', icon_url: 'https://example.com/icons/react.png' },
      { name: 'Node.js', icon_url: 'https://example.com/icons/nodejs.png' },
      { name: 'Django', icon_url: 'https://example.com/icons/django.png' },
      { name: 'Vue.js', icon_url: 'https://example.com/icons/vuejs.png' },
    ]

    const insertTechnologyQuery = db.prepare(`
      INSERT INTO technology (name, icon_url)
      VALUES (?, ?)
    `)

    const checkTechnologyQuery = db.prepare(`
      SELECT id FROM technology WHERE name = ?
    `)

    technologies.forEach((tech) => {
      const existingTechnology = checkTechnologyQuery.get(tech.name)
      if (existingTechnology) {
        console.log(`Technology "${tech.name}" already exists.`)
      } else {
        insertTechnologyQuery.run(tech.name, tech.icon_url)
        console.log(`Technology "${tech.name}" created successfully!`)
      }
    })
  } catch (err) {
    console.error('Error seeding technologies:', err.message)
  }
}
