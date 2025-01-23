import Redis from 'ioredis'

const redis = new Redis(
  process.env.REDIS_URL || {
    host: process.env.REDIS_HOST || '127.0.0.1',
    port: process.env.REDIS_PORT || 6379,
    password: process.env.REDIS_PASSWORD || '',
  }
)

export default redis

redis.on('connect', () => {
  console.log('Connected to Redis successfully!')
})

redis.on('error', (err) => {
  console.error('Redis connection error:', err)
})
