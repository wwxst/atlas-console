export function withListKeyword(search: string, value: string): string {
  const params = new URLSearchParams(search)
  const keyword = value.trim()
  params.delete('q')
  params.delete('page')
  if (keyword) params.set('keyword', keyword)
  else params.delete('keyword')
  return params.toString()
}
