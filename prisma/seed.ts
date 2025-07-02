import { PrismaClient } from '@prisma/client'
import bcrypt from 'bcryptjs'

const prisma = new PrismaClient()

async function main() {
  console.log('Start seeding ...')

  const existingAdmin = await prisma.member.findUnique({
    where: { email: process.env.DEFAULT_ADMIN_EMAIL },
  })

  if (!existingAdmin) {
    const hashedPassword = await bcrypt.hash(
      process.env.DEFAULT_ADMIN_PASSWORD!,
      10
    )

    const adminRole = await prisma.role.upsert({
      where: { name: 'Admin' },
      update: {},
      create: {
        name: 'Admin',
        description: 'Administrador com acesso total ao sistema.',
        level: 10,
      },
    })

    await prisma.member.create({
      data: {
        email: process.env.DEFAULT_ADMIN_EMAIL!,
        name: process.env.DEFAULT_ADMIN_NAME!,
        surname: process.env.DEFAULT_ADMIN_SURNAME!,
        password: hashedPassword,
        course: process.env.DEFAULT_ADMIN_COURSE!,
        entryDate: new Date(process.env.DEFAULT_ADMIN_ENTRY_DATE!),
        isActive: true,
        isAdmin: true,
        roleId: adminRole.id,
      },
    })
    console.log('Default admin created.')
  } else {
    console.log('Admin already exists.')
  }

  console.log('Seeding finished.')
}

main()
  .catch((e) => {
    console.error(e)
    process.exit(1)
  })
  .finally(async () => {
    await prisma.$disconnect()
  })
