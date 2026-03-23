import { z } from 'zod'

const typeNames: Record<string, string> = {
  string: 'texto',
  number: 'número',
  boolean: 'booleano',
  array: 'lista',
  object: 'objeto',
  date: 'data',
  bigint: 'bigint',
  symbol: 'símbolo',
  function: 'função',
  null: 'nulo',
  undefined: 'indefinido',
  never: 'never',
  nan: 'NaN',
  unknown: 'desconhecido',
}

const formatNames: Record<string, string> = {
  email: 'e-mail',
  url: 'URL',
  uuid: 'UUID',
  cuid: 'CUID',
  regex: 'formato',
  datetime: 'data/hora',
  ip: 'endereço IP',
}

function receivedType(input: unknown): string {
  if (input === null) return 'nulo'
  if (input === undefined) return 'indefinido'
  if (Array.isArray(input)) return 'lista'
  return typeNames[typeof input] ?? typeof input
}

export function setupZodLocale() {
  z.config({
    localeError: (issue) => {
      switch (issue.code) {
        case 'invalid_type': {
          const expected = typeNames[issue.expected] ?? issue.expected
          const received = receivedType(issue.input)
          return `Entrada inválida: esperado ${expected}, recebido ${received}.`
        }
        case 'too_small': {
          const min = issue.minimum
          if (issue.origin === 'string') {
            return min === 1
              ? 'Campo obrigatório.'
              : `Deve ter ao menos ${min} caractere${min !== 1 ? 's' : ''}.`
          }
          if (issue.origin === 'number') {
            return `Deve ser maior${issue.inclusive ? ' ou igual' : ''} a ${min}.`
          }
          if (issue.origin === 'array') {
            return `Deve ter ao menos ${min} item${min !== 1 ? 's' : ''}.`
          }
          return `Valor muito pequeno (mínimo: ${min}).`
        }
        case 'too_big': {
          const max = issue.maximum
          if (issue.origin === 'string') {
            return `Deve ter no máximo ${max} caractere${max !== 1 ? 's' : ''}.`
          }
          if (issue.origin === 'number') {
            return `Deve ser menor${issue.inclusive ? ' ou igual' : ''} a ${max}.`
          }
          if (issue.origin === 'array') {
            return `Deve ter no máximo ${max} item${max !== 1 ? 's' : ''}.`
          }
          return `Valor muito grande (máximo: ${max}).`
        }
        case 'invalid_format': {
          const fmt = formatNames[issue.format] ?? issue.format
          return `Formato de ${fmt} inválido.`
        }
        case 'invalid_value': {
          const values = issue.values.join(', ')
          return `Valor inválido. Esperado: ${values}.`
        }
        case 'unrecognized_keys': {
          return `Chave(s) desconhecida(s): ${(issue as { keys?: string[] }).keys?.join(', ')}.`
        }
        case 'invalid_union':
          return 'Entrada inválida.'
        case 'not_multiple_of':
          return `Deve ser múltiplo de ${(issue as { multipleOf?: number }).multipleOf}.`
        default:
          return 'Valor inválido.'
      }
    },
  })
}
