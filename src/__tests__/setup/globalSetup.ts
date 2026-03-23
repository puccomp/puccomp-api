import { execSync } from 'child_process'

export default function setup() {
  const testDbUrl = (process.env.DATABASE_URL ?? '').replace(
    /schema=[^&]+/,
    'schema=test'
  )
  execSync('npx prisma migrate deploy', {
    env: { ...process.env, DATABASE_URL: testDbUrl },
    stdio: 'inherit',
  })
}
