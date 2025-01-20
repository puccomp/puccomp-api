import redis from './redisClient.js'

const DEFAULT_CACHE_TTL = 144000
const GITHUB_API_BASE_URL = 'https://api.github.com'

const fetchOrganizationRepositories = async () => {
  const token = process.env.GITHUB_TOKEN
  const orgName = process.env.GITHUB_ORG_NAME

  if (!token) throw new Error('GitHub token is not configured.')

  const response = await fetch(`${GITHUB_API_BASE_URL}/orgs/${orgName}/repos`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) throw new Error('Failed to fetch GitHub repositories.')

  return await response.json()
}

const fetchRepositoryLanguages = async (languagesURL) => {
  const token = process.env.GITHUB_TOKEN

  const response = await fetch(languagesURL, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) throw new Error('Failed to fetch repository languages.')

  return await response.json()
}

const calculateLanguagePercentages = (languages) => {
  const totalSize = Object.values(languages).reduce(
    (sum, size) => sum + size,
    0
  )
  return Object.entries(languages).map(([language, size]) => ({
    language,
    percentage: ((size / totalSize) * 100).toFixed(2),
  }))
}

const fetchRepositoryContributors = async (contributorsURL) => {
  const token = process.env.GITHUB_TOKEN

  const response = await fetch(contributorsURL, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) throw new Error('Failed to fetch repository contributors.')

  return await response.json()
}

const enrichRepository = async (repo, baseURL) => {
  const languages = await fetchRepositoryLanguages(repo.languages_url)
  const contributors = await fetchRepositoryContributors(repo.contributors_url)

  return {
    id: repo.id,
    name: repo.name,
    description: repo.description || 'No description available',
    url: repo.html_url,
    language: repo.language,
    private: repo.private,
    created_at: repo.created_at,
    contributors: contributors.map((contributor) => ({
      id: contributor.id,
      login: contributor.login,
      url: contributor.url,
      avatar_url: contributor.avatar_url,
      contributions: contributor.contributions,
    })),
    languages: calculateLanguagePercentages(languages),
    image_url: `${baseURL}/github-project/image/${repo.name}`,
  }
}

const getCachedFeaturedRepositories = async (req) => {
  const CACHE_KEY = 'github_projects'
  const FEATURED_TOPIC = 'featured'
  const API_BASE_URL = `${req.protocol}://${req.get('host')}`

  const cachedData = await redis.get(CACHE_KEY)
  if (cachedData) return JSON.parse(cachedData)

  const allRepos = await fetchOrganizationRepositories()

  const featuredRepos = allRepos.filter(
    (repo) => repo.topics && repo.topics.includes(FEATURED_TOPIC)
  )

  const enrichedRepos = await Promise.all(
    featuredRepos.map((repo) => enrichRepository(repo, API_BASE_URL))
  )

  await redis.set(
    CACHE_KEY,
    JSON.stringify(enrichedRepos),
    'EX',
    DEFAULT_CACHE_TTL
  )

  return enrichedRepos
}

const fetchProjectImage = async (repoName) => {
  const token = process.env.GITHUB_TOKEN
  const orgName = process.env.GITHUB_ORG_NAME
  const repoAssets = 'project-assets'
  const filePath = `images/featured_${repoName}.png`

  const cacheKey = `github_image_${repoName}`
  const cachedImage = await redis.get(cacheKey)

  if (cachedImage)
    return { content: Buffer.from(cachedImage, 'base64'), fromCache: true }

  const url = `https://api.github.com/repos/${orgName}/${repoAssets}/contents/${filePath}`

  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    const error = await response.json()
    console.error('GitHub API Error:', error)
    throw new Error('Image not found.')
  }

  const data = await response.json()

  if (!data.content || data.encoding !== 'base64') {
    throw new Error('Failed to fetch image content.')
  }

  const imageBuffer = Buffer.from(data.content, 'base64')

  const TTL = 14400
  await redis.set(cacheKey, data.content, 'EX', TTL)

  return { content: imageBuffer, fromCache: false }
}

export { getCachedFeaturedRepositories, fetchProjectImage }
