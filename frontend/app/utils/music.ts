import type { MusicSummaryResponse, RecentAlbumsPageResponse } from '~/types/home'
import type {
  AlbumLibraryPageResponse,
  AlbumLibraryRow,
  AlbumPageData,
  AlbumPageResponse,
  AlbumTrackModel,
  ArtistLibraryPageResponse,
  ArtistLibraryRow,
  MusicCollectionContextMetric,
  MusicCollectionData,
  MusicLibraryCardModel,
  MusicLibraryKind,
  MusicLibraryMetric,
  MusicLibraryPageData,
  MusicSearchResponse,
  TopAlbumResponse,
  TopArtistResponse,
  TopTrackResponse,
  TrackLibraryPageResponse,
  TrackLibraryRow,
} from '~/types/music'
import type { EditorialHighlight, EditorialShelfItem } from '~/types/home'
import { formatAbsoluteDate, formatRelativeDate, formatShortNumber } from '~/utils/formatting'

function trackPosition(track: AlbumPageResponse['tracks'][number]) {
  const disc = track.discNumber != null ? `${track.discNumber}.` : ''
  const number = track.trackNumber != null ? String(track.trackNumber).padStart(2, '0') : '00'
  return `${disc}${number}`
}

function mapTrack(track: AlbumPageResponse['tracks'][number]): AlbumTrackModel {
  return {
    id: `track-${track.trackId}`,
    title: track.title,
    position: trackPosition(track),
    meta: `${track.playCount} plays`,
    lastPlayed: track.lastPlayed ? formatRelativeDate(track.lastPlayed) : 'Ainda sem play',
  }
}

function recentAlbumToShelfItem(album: RecentAlbumsPageResponse['items'][number]): EditorialShelfItem {
  return {
    id: `album-${album.albumId}`,
    type: 'music',
    title: album.albumTitle,
    subtitle: album.year ? `${album.artistName} · ${album.year}` : album.artistName,
    imageUrl: album.coverUrl,
    href: `/music/albums/${album.albumId}`,
    meta: `${formatShortNumber(album.playCount)} plays`,
    detail: formatRelativeDate(album.lastPlayed),
    timestamp: album.lastPlayed,
  }
}

function topArtistToShelfItem(artist: TopArtistResponse): EditorialShelfItem {
  return {
    id: `artist-${artist.artistId}`,
    type: 'music',
    title: artist.artistName,
    subtitle: artist.albumTitle ? `Puxado por ${artist.albumTitle}` : 'Artista em rotação',
    imageUrl: artist.coverUrl,
    href: null,
    meta: `${formatShortNumber(artist.playCount)} plays`,
    detail: artist.albumTitle ? 'Recorte do período' : 'Sem álbum líder visível',
  }
}

function topTrackToShelfItem(track: TopTrackResponse): EditorialShelfItem {
  return {
    id: `track-${track.trackId}`,
    type: 'music',
    title: track.title,
    subtitle: `${track.artistName} · ${track.albumTitle}`,
    imageUrl: null,
    href: null,
    meta: `${formatShortNumber(track.playCount)} plays`,
    detail: 'Faixa mais recorrente do período',
  }
}

function neverPlayedAlbumToShelfItem(album: TopAlbumResponse): EditorialShelfItem {
  return {
    id: `never-${album.albumId}`,
    type: 'music',
    title: album.albumTitle,
    subtitle: album.artistName,
    imageUrl: null,
    href: `/music/albums/${album.albumId}`,
    meta: 'Ainda sem primeira audição',
    detail: 'Pronto para virar descoberta',
  }
}

function sortByTimestamp<T extends { timestamp?: string | null }>(items: T[]) {
  return [...items].sort((a, b) => {
    const aTime = a.timestamp ? new Date(a.timestamp).getTime() : 0
    const bTime = b.timestamp ? new Date(b.timestamp).getTime() : 0
    return bTime - aTime
  })
}

function toHighlight(item: EditorialShelfItem): EditorialHighlight {
  return {
    id: item.id,
    type: item.type,
    title: item.title,
    subtitle: item.subtitle,
    eyebrow: 'Música',
    imageUrl: item.imageUrl,
    href: item.href,
    timestamp: item.timestamp ?? new Date().toISOString(),
    meta: `${item.meta} · ${item.detail}`,
  }
}

function buildCollectionMetrics(payload: {
  summary: MusicSummaryResponse
  topArtists: TopArtistResponse[]
  neverPlayedAlbums: TopAlbumResponse[]
}): MusicCollectionContextMetric[] {
  const leadingArtist = payload.topArtists[0]

  return [
    {
      id: 'artists',
      label: 'Artistas no recorte',
      value: formatShortNumber(payload.summary.artistsCount),
      note: 'quem realmente apareceu no período mais recente, não no arquivo inteiro',
    },
    {
      id: 'albums',
      label: 'Álbuns em circulação',
      value: formatShortNumber(payload.summary.albumsCount),
      note: 'o tamanho da vitrine recente antes de abrir a biblioteca completa',
    },
    {
      id: 'tracks',
      label: 'Faixas tocadas',
      value: formatShortNumber(payload.summary.tracksCount),
      note: 'o nível de detalhe que esse momento musical já alcançou',
    },
    {
      id: 'frontier',
      label: 'Ainda sem play',
      value: formatShortNumber(payload.neverPlayedAlbums.length),
      note: leadingArtist
        ? `${leadingArtist.artistName} lidera o recorte com ${formatShortNumber(leadingArtist.playCount)} plays`
        : 'a área de descoberta continua pronta para a próxima entrada',
    },
  ]
}

export function buildMusicCollectionData(payload: {
  summary: MusicSummaryResponse
  recentAlbums: RecentAlbumsPageResponse
  topArtists: TopArtistResponse[]
  topTracks: TopTrackResponse[]
  neverPlayedAlbums: TopAlbumResponse[]
}): MusicCollectionData {
  const featuredAlbums = sortByTimestamp(payload.recentAlbums.items.map(recentAlbumToShelfItem)).slice(0, 6)
  const heroLead = featuredAlbums[0] ? toHighlight(featuredAlbums[0]) : null
  const heroSupporting = featuredAlbums.slice(1, 5).map(toHighlight)

  return {
    generatedAt: new Date().toISOString(),
    hero: {
      title: 'Os discos, artistas e faixas que estão definindo o momento',
      intro:
        'Uma primeira página para reencontrar o que está em rotação sem cair numa biblioteca fria: os álbuns mais presentes, os artistas que puxaram o período e as faixas que insistiram em voltar.',
      lead: heroLead,
      supporting: heroSupporting,
    },
    featuredAlbums,
    topArtists: payload.topArtists.slice(0, 6).map(topArtistToShelfItem),
    topTracks: payload.topTracks.slice(0, 6).map(topTrackToShelfItem),
    discoveryAlbums: payload.neverPlayedAlbums.slice(0, 8).map(neverPlayedAlbumToShelfItem),
    context: {
      eyebrow: 'Panorama do recorte',
      title: 'O tamanho e a textura dessa fase musical',
      description: 'Um contexto curto para lembrar o peso da escuta recente sem transformar a seção em dashboard de streaming.',
      summary: `${formatShortNumber(payload.summary.albumsCount)} álbuns e ${formatShortNumber(payload.summary.tracksCount)} faixas passaram por aqui no último mês, com espaço claro para novas descobertas.`,
      metrics: buildCollectionMetrics(payload),
    },
  }
}

function tabs(summary: MusicSummaryResponse) {
  return [
    {
      kind: 'artists' as const,
      label: 'Artistas',
      summary: `${formatShortNumber(summary.artistsCount)} no recorte`,
    },
    {
      kind: 'albums' as const,
      label: 'Álbuns',
      summary: `${formatShortNumber(summary.albumsCount)} no recorte`,
    },
    {
      kind: 'tracks' as const,
      label: 'Faixas',
      summary: `${formatShortNumber(summary.tracksCount)} no recorte`,
    },
  ]
}

function buildLibraryMetrics(summary: MusicSummaryResponse): MusicLibraryMetric[] {
  return [
    {
      id: 'artists',
      label: 'Artistas na escuta',
      value: formatShortNumber(summary.artistsCount),
      note: 'a frente de nomes que realmente entrou em circulação no período',
    },
    {
      id: 'albums',
      label: 'Álbuns em foco',
      value: formatShortNumber(summary.albumsCount),
      note: 'a unidade principal de descoberta e retorno na seção de música',
    },
    {
      id: 'tracks',
      label: 'Faixas em jogo',
      value: formatShortNumber(summary.tracksCount),
      note: 'o nível de granularidade que a escuta recente já alcançou',
    },
    {
      id: 'range',
      label: 'Janela do recorte',
      value: formatAbsoluteDate(summary.range.start),
      note: `até ${formatAbsoluteDate(summary.range.end)}`,
    },
  ]
}

function artistCard(item: ArtistLibraryRow): MusicLibraryCardModel {
  return {
    id: `artist-${item.artistId}`,
    kind: 'artists',
    title: item.artistName,
    subtitle: `${formatShortNumber(item.albumsCount)} álbuns · ${formatShortNumber(item.tracksCount)} faixas`,
    href: null,
    imageUrl: item.coverUrl,
    primaryMeta: `${formatShortNumber(item.totalPlays)} plays acumulados`,
    secondaryMeta: item.lastPlayed ? `Último play ${formatRelativeDate(item.lastPlayed)}` : 'Ainda sem play recente',
    aside: item.lastPlayed ? 'Em rotação' : 'Quieto',
    isDormant: !item.lastPlayed,
  }
}

function albumAside(item: AlbumLibraryRow) {
  if (item.playCount === 0) return 'Intocado'
  if (item.playedTracks >= item.totalTracks && item.totalTracks > 0) return 'Coberto'
  return 'Parcial'
}

function albumCard(item: AlbumLibraryRow): MusicLibraryCardModel {
  return {
    id: `album-${item.albumId}`,
    kind: 'albums',
    title: item.albumTitle,
    subtitle: item.year ? `${item.artistName} · ${item.year}` : item.artistName,
    href: `/music/albums/${item.albumId}`,
    imageUrl: item.coverUrl,
    primaryMeta: `${formatShortNumber(item.playCount)} plays · ${formatShortNumber(item.playedTracks)}/${formatShortNumber(item.totalTracks)} faixas`,
    secondaryMeta: item.lastPlayed ? `Último play ${formatRelativeDate(item.lastPlayed)}` : 'Ainda sem primeira audição',
    aside: albumAside(item),
    isDormant: item.playCount === 0,
  }
}

function trackCard(item: TrackLibraryRow): MusicLibraryCardModel {
  return {
    id: `track-${item.trackId}`,
    kind: 'tracks',
    title: item.title,
    subtitle: item.albumTitle ? `${item.artistName} · ${item.albumTitle}` : item.artistName,
    href: null,
    imageUrl: item.coverUrl,
    primaryMeta: `${formatShortNumber(item.totalPlays)} plays acumulados`,
    secondaryMeta: item.lastPlayed ? `Último play ${formatRelativeDate(item.lastPlayed)}` : 'Ainda sem play recente',
    aside: item.lastPlayed ? 'Faixa ativa' : 'Quieta',
    isDormant: !item.lastPlayed,
  }
}

function searchArtistCard(item: MusicSearchResponse['artists'][number]): MusicLibraryCardModel {
  return {
    id: `search-artist-${item.id}`,
    kind: 'artists',
    title: item.name,
    subtitle: 'Artista encontrado pela busca',
    href: null,
    imageUrl: null,
    primaryMeta: 'Entrada localizada pela busca',
    secondaryMeta: 'Página de artista entra na próxima etapa',
    aside: 'Busca',
  }
}

function searchAlbumCard(item: MusicSearchResponse['albums'][number]): MusicLibraryCardModel {
  return {
    id: `search-album-${item.id}`,
    kind: 'albums',
    title: item.title,
    subtitle: item.year ? `${item.artistName} · ${item.year}` : item.artistName,
    href: `/music/albums/${item.id}`,
    imageUrl: null,
    primaryMeta: 'Entrada localizada pela busca',
    secondaryMeta: 'Álbum disponível para abertura direta',
    aside: 'Busca',
  }
}

function searchTrackCard(item: MusicSearchResponse['tracks'][number]): MusicLibraryCardModel {
  return {
    id: `search-track-${item.id}`,
    kind: 'tracks',
    title: item.title,
    subtitle: `${item.artistName} · ${item.albumTitle}`,
    href: null,
    imageUrl: null,
    primaryMeta: 'Entrada localizada pela busca',
    secondaryMeta: 'Página de faixa entra na próxima etapa',
    aside: 'Busca',
  }
}

function buildSpotlightFromCard(card: MusicLibraryCardModel | undefined, fallbackTitle: string) {
  if (!card) return null

  return {
    title: card.title,
    subtitle: card.subtitle,
    imageUrl: card.imageUrl,
    href: card.href,
    meta: card.primaryMeta,
    note: card.secondaryMeta,
  }
}

function kindCopy(kind: MusicLibraryKind) {
  switch (kind) {
    case 'artists':
      return {
        name: 'artistas',
        title: 'Biblioteca de artistas',
        description: 'Os nomes que estruturam sua escuta, para reconhecer recorrência e lacunas sem depender de memória bruta.',
      }
    case 'albums':
      return {
        name: 'álbuns',
        title: 'Biblioteca de álbuns',
        description: 'O arquivo principal de descoberta e retorno, onde a capa e o contexto continuam guiando a navegação.',
      }
    case 'tracks':
      return {
        name: 'faixas',
        title: 'Biblioteca de faixas',
        description: 'A camada mais granular da coleção, útil para replay, recorrência e busca de detalhe.',
      }
  }
}

export function buildMusicLibraryCards(
  kind: 'artists',
  items: ArtistLibraryPageResponse['items'],
): MusicLibraryCardModel[]
export function buildMusicLibraryCards(
  kind: 'albums',
  items: AlbumLibraryPageResponse['items'],
): MusicLibraryCardModel[]
export function buildMusicLibraryCards(
  kind: 'tracks',
  items: TrackLibraryPageResponse['items'],
): MusicLibraryCardModel[]
export function buildMusicLibraryCards(
  kind: MusicLibraryKind,
  items: ArtistLibraryPageResponse['items'] | AlbumLibraryPageResponse['items'] | TrackLibraryPageResponse['items'],
): MusicLibraryCardModel[] {
  if (kind === 'artists') {
    return (items as ArtistLibraryPageResponse['items']).map(artistCard)
  }

  if (kind === 'albums') {
    return (items as AlbumLibraryPageResponse['items']).map(albumCard)
  }

  return (items as TrackLibraryPageResponse['items']).map(trackCard)
}

export function buildMusicLibraryPageData(payload: {
  summary: MusicSummaryResponse
  selectedKind: MusicLibraryKind
  query: string
  artists: ArtistLibraryPageResponse | null
  albums: AlbumLibraryPageResponse | null
  tracks: TrackLibraryPageResponse | null
  searchResults: MusicSearchResponse | null
}): MusicLibraryPageData {
  const selectedCopy = kindCopy(payload.selectedKind)
  const searchQuery = payload.query.trim()
  const isSearch = !!searchQuery

  const libraryItems =
    payload.selectedKind === 'artists'
      ? buildMusicLibraryCards('artists', payload.artists?.items ?? [])
      : payload.selectedKind === 'albums'
        ? buildMusicLibraryCards('albums', payload.albums?.items ?? [])
        : buildMusicLibraryCards('tracks', payload.tracks?.items ?? [])

  const searchItems =
    payload.selectedKind === 'artists'
      ? (payload.searchResults?.artists ?? []).map(searchArtistCard)
      : payload.selectedKind === 'albums'
        ? (payload.searchResults?.albums ?? []).map(searchAlbumCard)
        : (payload.searchResults?.tracks ?? []).map(searchTrackCard)

  const items = isSearch ? searchItems : libraryItems
  const spotlight = buildSpotlightFromCard(
    items[0],
    isSearch ? `Resultados para ${searchQuery}` : selectedCopy.title,
  )

  const libraryCursor =
    payload.selectedKind === 'artists'
      ? payload.artists?.nextCursor ?? null
      : payload.selectedKind === 'albums'
        ? payload.albums?.nextCursor ?? null
        : payload.tracks?.nextCursor ?? null

  return {
    hero: {
      title: isSearch ? `Busca em ${selectedCopy.name}` : selectedCopy.title,
      intro: isSearch
        ? `Um corte da biblioteca de música para localizar ${selectedCopy.name} a partir da busca, sem perder a mesma linguagem editorial do restante da seção.`
        : selectedCopy.description,
      backLink: '/music',
      backLabel: 'Voltar ao recorte',
      accentLink: '/music/library?kind=albums',
      accentLabel: 'Abrir álbuns',
      spotlight,
    },
    filters: {
      query: searchQuery,
      selectedKind: payload.selectedKind,
      tabs: tabs(payload.summary),
    },
    context: {
      eyebrow: isSearch ? 'Busca' : 'Arquivo',
      title: isSearch
        ? `Onde ${searchQuery} apareceu`
        : `Como ${selectedCopy.name} entram no seu arquivo`,
      description: isSearch
        ? 'A busca funciona como atalho editorial: encontra primeiro, depois deixa a navegação continuar pelo mesmo ritmo visual.'
        : 'A library existe para navegação densa e reconhecimento rápido, não para virar uma tabela fria.',
      summary: isSearch
        ? `${formatShortNumber(items.length)} resultados na camada de ${selectedCopy.name}.`
        : `${formatShortNumber(payload.summary.artistsCount)} artistas, ${formatShortNumber(payload.summary.albumsCount)} álbuns e ${formatShortNumber(payload.summary.tracksCount)} faixas compõem a frente recente de música.`,
      metrics: buildLibraryMetrics(payload.summary),
    },
    section: {
      id: payload.selectedKind,
      eyebrow: isSearch ? 'Resultados' : 'Arquivo principal',
      title: isSearch ? `Resultados em ${selectedCopy.name}` : selectedCopy.title,
      description: isSearch
        ? 'Os resultados ficam separados por camada para preservar o eixo da navegação.'
        : selectedCopy.description,
      summary: isSearch
        ? 'Busca curta, leitura rápida e continuidade visual com o resto da seção.'
        : 'A exploração continua centrada em reconhecimento visual e contexto curto.',
      items,
      emptyMessage: isSearch
        ? `Nada apareceu em ${selectedCopy.name} para essa busca.`
        : `Nenhuma entrada de ${selectedCopy.name} apareceu ainda.`,
    },
    libraryCursor: isSearch ? null : libraryCursor,
    mode: isSearch ? 'search' : 'library',
  }
}

export function buildAlbumPageData(album: AlbumPageResponse): AlbumPageData {
  return {
    id: String(album.albumId),
    title: album.albumTitle,
    artistName: album.artistName,
    year: album.year,
    coverUrl: album.coverUrl,
    heroMeta: [
      album.year ? String(album.year) : null,
      album.artistName,
      `${album.totalPlays} plays`,
      album.lastPlayed ? `Último ${formatRelativeDate(album.lastPlayed)}` : 'Sem play recente',
    ].filter(Boolean) as string[],
    stats: {
      totalPlays: album.totalPlays,
      tracksCount: album.tracks.length,
      latestPlay: album.lastPlayed ? formatRelativeDate(album.lastPlayed) : 'Sem play recente',
      latestPlayAbsolute: album.lastPlayed ? formatAbsoluteDate(album.lastPlayed) : null,
    },
    tracks: album.tracks.map(mapTrack),
    recentDays: album.playsByDay.slice(-12).map((day) => ({
      id: day.day,
      label: day.day,
      plays: day.plays,
    })),
  }
}
