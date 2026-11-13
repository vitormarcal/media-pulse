export interface ArtworkTextToken {
  kind: 'text' | 'strong' | 'em'
  value: string
}

// Only paired inline emphasis. Vue renders values as escaped text, never as HTML.
export function artworkTextTokens(text: string): ArtworkTextToken[] {
  const tokens: ArtworkTextToken[] = []
  const pattern = /(?<!\*)\*\*([^*\n]+)\*\*(?!\*)|(?<!\*)\*([^*\n]+)\*(?!\*)/g
  let offset = 0
  for (const match of text.matchAll(pattern)) {
    const value = match[1] ?? match[2] ?? ''
    if (!value.trim() || value !== value.trim()) continue
    if (match.index > offset) tokens.push({ kind: 'text', value: text.slice(offset, match.index) })
    tokens.push({ kind: match[1] != null ? 'strong' : 'em', value })
    offset = match.index + match[0].length
  }
  if (offset < text.length) tokens.push({ kind: 'text', value: text.slice(offset) })
  return tokens
}

export function artworkTextPreview(tokens: ArtworkTextToken[], limit: number): ArtworkTextToken[] {
  const result: ArtworkTextToken[] = []
  let remaining = limit
  for (const token of tokens) {
    if (token.value.length <= remaining) {
      result.push(token)
      remaining -= token.value.length
      continue
    }
    const cut = token.value.slice(0, remaining)
    const boundary = cut.lastIndexOf(' ')
    const value = cut.slice(0, boundary > cut.length * 0.7 ? boundary : cut.length).trimEnd()
    if (value) result.push({ ...token, value })
    result.push({ kind: 'text', value: '…' })
    return result
  }
  return result
}
