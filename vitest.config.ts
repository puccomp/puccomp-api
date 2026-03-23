import { defineConfig } from 'vitest/config'

// Reuse the same Postgres database but isolate tests in a dedicated schema
const testDbUrl = (process.env.DATABASE_URL ?? '').replace(
  /schema=[^&]+/,
  'schema=test'
)

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    exclude: ['dist/**', 'node_modules/**'],
    environment: 'node',
    globalSetup: './src/__tests__/setup/globalSetup.ts',
    env: {
      NODE_ENV: 'test',
      DATABASE_URL: testDbUrl,
    },
    // Single fork + no file parallelism: integration tests share one DB connection
    // and test files execute sequentially, preventing beforeEach cleanup conflicts
    pool: 'forks',
    singleFork: true,
    fileParallelism: false,
  },
})
