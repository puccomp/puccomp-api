import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    exclude: ['dist/**', 'node_modules/**'],
    environment: 'node',
    env: {
      NODE_ENV: 'test',
    },
    // Single fork + no file parallelism: integration tests share one DB connection
    // and test files execute sequentially, preventing beforeEach cleanup conflicts
    pool: 'forks',
    singleFork: true,
    fileParallelism: false,
  },
})
