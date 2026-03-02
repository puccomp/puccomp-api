import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    exclude: ['dist/**', 'node_modules/**'],
    environment: 'node',
    env: {
      NODE_ENV: 'test',
    },
    // Single fork so integration tests share one DB connection and run sequentially
    pool: 'forks',
    singleFork: true,
  },
})
