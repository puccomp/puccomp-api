const TechnologyUsageLevel = Object.freeze({
  PRIMARY: 'primary',
  SECONDARY: 'secondary',
  SUPPORTIVE: 'supportive',
  OBSOLETE: 'obsolete',
})

const TechnologyType = Object.freeze({
  LANGUAGE: 'language',
  FRAMEWORK: 'framework',
  LIBRARY: 'library',
  TOOL: 'tool',
  OTHER: 'other',
})

export { TechnologyUsageLevel, TechnologyType }
