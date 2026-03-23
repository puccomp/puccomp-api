import { execSync } from 'child_process'

export default function setup() {
  const testDbUrl = (process.env.DATABASE_URL ?? '').replace(
    /schema=[^&]+/,
    'schema=test'
  )
  const env = { ...process.env, DATABASE_URL: testDbUrl }

  // Drop and recreate the test schema for a clean slate on every run
  const baseDbUrl = (process.env.DATABASE_URL ?? '').replace(
    /[?&]schema=[^&]*/,
    '',
  )
  execSync(
    `echo "DROP SCHEMA IF EXISTS test CASCADE; CREATE SCHEMA test;" | npx prisma db execute --url "${baseDbUrl}" --stdin`,
    { env, stdio: 'inherit' },
  )

  execSync('npx prisma migrate deploy', { env, stdio: 'inherit' })
}
