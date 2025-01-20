import express from 'express'
import {
  getCachedFeaturedRepositories,
  fetchProjectImage,
} from '../utils/githubService.js'

const router = express.Router()

router.get('/github-projects', async (req, res) => {
  try {
    const featuredRepos = await getCachedFeaturedRepositories(req)

    const dto = featuredRepos.map((repo) => ({
      id: repo.id,
      name: repo.name,
      language: repo.language,
      created_at: repo.created_at,
      image_url: repo.image_url,
    }))

    res.json(dto)
  } catch (error) {
    console.error('Error fetching GitHub projects:', error)
    res.status(500).json({ message: 'Failed to fetch GitHub projects.' })
  }
})

router.get('/github-projects/:repoName', async (req, res) => {
  try {
    const { repoName } = req.params
    const featuredRepos = await getCachedFeaturedRepositories(req)

    const repo = featuredRepos.find((repo) => repo.name === repoName)

    if (!repo) return res.status(404).json({ message: 'Repository not found.' })

    res.json(repo)
  } catch (error) {
    console.error('Error fetching repository details:', error)
    res.status(500).json({ message: 'Failed to fetch repository details.' })
  }
})

router.get('/github-project-image/:repoName', async (req, res) => {
  try {
    const { repoName } = req.params
    const imageBuffer = await fetchProjectImage(repoName)
    res.setHeader('Content-Type', 'image/png')
    res.send(imageBuffer.content)
  } catch (error) {
    console.error('Failed to fetch image:', error)
    res.status(500).json({ message: 'Failed to fetch image.' })
  }
})

export default router
