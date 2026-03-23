import { describe, it, expect } from 'vitest'
import { generateSlug } from '../../utils/slug.js'

describe('generateSlug', () => {
  it('converts spaces to hyphens', () => {
    expect(generateSlug('My Project')).toBe('my-project')
  })

  it('removes diacritics and accented characters', () => {
    expect(generateSlug('Gestão de TI')).toBe('gestao-de-ti')
    expect(generateSlug('Ação & Reação')).toBe('acao-reacao')
    expect(generateSlug('Óleo e Vírus')).toBe('oleo-e-virus')
  })

  it('removes special characters', () => {
    expect(generateSlug('API! RESTful & v2')).toBe('api-restful-v2')
    expect(generateSlug('C++ Game Engine')).toBe('c-game-engine')
    expect(generateSlug('Node.js Backend')).toBe('nodejs-backend')
  })

  it('collapses multiple spaces and hyphens into one', () => {
    expect(generateSlug('my  double  space')).toBe('my-double-space')
    expect(generateSlug('already-has--double')).toBe('already-has-double')
  })

  it('trims leading and trailing whitespace before converting', () => {
    expect(generateSlug('  leading trailing  ')).toBe('leading-trailing')
  })

  it('lowercases the entire result', () => {
    expect(generateSlug('COMP Junior Company')).toBe('comp-junior-company')
  })

  it('handles names that only have special characters between words', () => {
    expect(generateSlug('Sistema (v2.0)')).toBe('sistema-v20')
  })
})
