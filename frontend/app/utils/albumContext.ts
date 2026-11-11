import type { AlbumContext } from '~/types/music'
const typeLabels: Record<string, string> = {
  Album: 'Álbum',
  Single: 'Single',
  EP: 'EP',
  Broadcast: 'Transmissão',
  Other: 'Outro',
  Compilation: 'Coletânea',
  Soundtrack: 'Trilha sonora',
  Live: 'Ao vivo',
  Remix: 'Remix',
  Spokenword: 'Palavra falada',
  Interview: 'Entrevista',
  Audiobook: 'Audiolivro',
  'Audio drama': 'Audiodrama',
  'DJ-mix': 'Mix de DJ',
  'Mixtape/Street': 'Mixtape',
}
export function albumContextMeta(context: AlbumContext | null | undefined, year?: number | null): string[] {
  const types = context
    ? [context.primaryType, ...context.secondaryTypes]
        .filter((type): type is string => !!type)
        .map((type) => typeLabels[type] || type)
    : []
  const date = context?.firstReleaseDate
  let release = year ? String(year) : ''
  if (date && /^\d{4}(?:-\d{2}){0,2}$/.test(date)) {
    const parts = date.split('-').map(Number)
    release =
      parts.length === 1
        ? date
        : new Intl.DateTimeFormat('pt-BR', {
            year: 'numeric',
            month: 'short',
            ...(parts.length === 3 ? { day: '2-digit' as const } : {}),
          }).format(new Date(parts[0]!, parts[1]! - 1, parts[2] || 1))
    if (parts.length > 1) release = `Primeiro lançamento: ${release}`
  }
  return [...types, release].filter(Boolean)
}
