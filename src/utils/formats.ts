export const toCamelCase = (str: string) =>
  str.replace(/([-_][a-z])/g, (group) =>
    group.toUpperCase().replace('-', '').replace('_', '')
  )

export const toSnakeCase = (str: string) =>
  str.replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`)

export const keysToSnakeCase = (obj: any): any => {
  if (Array.isArray(obj)) {
    return obj.map((v) => keysToSnakeCase(v))
  } else if (obj !== null && obj.constructor === Object) {
    return Object.keys(obj).reduce(
      (acc, key) => {
        acc[toSnakeCase(key)] = keysToSnakeCase(obj[key])
        return acc
      },
      {} as { [key: string]: any }
    )
  }
  return obj
}

export const formatDate = (date: Date | string | null | undefined): string | null => {
  if (!date) return null
  const d = new Date(date)
  if (isNaN(d.getTime())) return null
  return d.toISOString().split('T')[0] // YYYY-MM-DD
}
