import type { MusicSummaryResponse, RecentAlbumsPageResponse } from '~/types/home'
import type {
  AlbumLibraryPageResponse,
  AlbumLibraryRow,
  AlbumPageData,
  AlbumPageResponse,
  AlbumTrackModel,
  ArtistPageData,
  ArtistPageResponse,
  ArtistTrackModel,
  ArtistLibraryPageResponse,
  ArtistLibraryRow,
  MusicByYearResponse,
  MusicCollectionContextMetric,
  MusicCollectionData,
  MusicLibraryCardModel,
  MusicLibraryKind,
  MusicLibraryMetric,
  MusicLibraryPageData,
  MusicLibraryYearChip,
  MusicSearchResponse,
  MusicStatsResponse,
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

function mapArtistTrack(track: ArtistPageResponse['topTracks'][number]): ArtistTrackModel {
  return {
    id: `artist-track-${track.trackId}`,
    title: track.title,
    albumTitle: track.albumTitle,
    albumHref: track.albumId != null ? `/music/albums/${track.albumId}` : null,
    meta: `${formatShortNumber(track.playCount)} plays`,
    lastPlayed: track.lastPlayed ? formatRelativeDate(track.lastPlayed) : 'Sem play recente',
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
    href: `/music/artists/${artist.artistId}`,
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

function tabs(summary: MusicStatsResponse['total']) {
  return [
    {
      kind: 'artists' as const,
      label: 'Artistas',
      summary: `${formatShortNumber(summary.uniqueArtistsCount)} no arquivo`,
    },
    {
      kind: 'albums' as const,
      label: 'Álbuns',
      summary: `${formatShortNumber(summary.uniqueAlbumsCount)} no arquivo`,
    },
    {
      kind: 'tracks' as const,
      label: 'Faixas',
      summary: `${formatShortNumber(summary.uniqueTracksCount)} no arquivo`,
    },
  ]
}

function buildLibraryMetrics(stats: MusicStatsResponse): MusicLibraryMetric[] {
  return [
    {
      id: 'artists',
      label: 'Artistas no histórico',
      value: formatShortNumber(stats.total.uniqueArtistsCount),
      note: 'quem já apareceu de fato no histórico de plays consolidado',
    },
    {
      id: 'albums',
      label: 'Álbuns no histórico',
      value: formatShortNumber(stats.total.uniqueAlbumsCount),
      note: 'a unidade principal de descoberta e retorno já registrada no arquivo',
    },
    {
      id: 'tracks',
      label: 'Faixas no histórico',
      value: formatShortNumber(stats.total.uniqueTracksCount),
      note: 'a camada mais granular já tocada ao longo do arquivo',
    },
    {
      id: 'plays',
      label: 'Plays acumulados',
      value: formatShortNumber(stats.total.playsCount),
      note: stats.firstPlayAt
        ? `do primeiro em ${formatAbsoluteDate(stats.firstPlayAt)} ao mais recente`
        : 'ainda sem janela consolidada suficiente',
    },
  ]
}

function buildYearChips(stats: MusicStatsResponse): MusicLibraryYearChip[] {
  return stats.years
    .slice()
    .sort((a, b) => b.year - a.year)
    .slice(0, 12)
    .map((year) => ({
      year: year.year,
      label: String(year.year),
      detail: `${formatShortNumber(year.uniqueAlbumsCount)} álbuns`,
    }))
}

function artistCard(item: ArtistLibraryRow): MusicLibraryCardModel {
  return {
    id: `artist-${item.artistId}`,
    kind: 'artists',
    title: item.artistName,
    subtitle: `${formatShortNumber(item.albumsCount)} álbuns · ${formatShortNumber(item.tracksCount)} faixas`,
    href: `/music/artists/${item.artistId}`,
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
    href: `/music/artists/${item.id}`,
    imageUrl: null,
    primaryMeta: 'Entrada localizada pela busca',
    secondaryMeta: 'Artista disponível para abertura direta',
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

function yearArtistCard(item: TopArtistResponse): MusicLibraryCardModel {
  return {
    id: `year-artist-${item.artistId}`,
    kind: 'artists',
    title: item.artistName,
    subtitle: item.albumTitle ? `Puxado por ${item.albumTitle}` : 'Artista em destaque no ano',
    href: `/music/artists/${item.artistId}`,
    imageUrl: item.coverUrl,
    primaryMeta: `${formatShortNumber(item.playCount)} plays no ano`,
    secondaryMeta: 'Leitura do recorte anual',
    aside: 'Ano',
  }
}

function yearTrackCard(item: TopTrackResponse): MusicLibraryCardModel {
  return {
    id: `year-track-${item.trackId}`,
    kind: 'tracks',
    title: item.title,
    subtitle: `${item.artistName} · ${item.albumTitle}`,
    href: null,
    imageUrl: null,
    primaryMeta: `${formatShortNumber(item.playCount)} plays no ano`,
    secondaryMeta: 'Faixa recorrente do recorte anual',
    aside: 'Ano',
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
  stats: MusicStatsResponse
  selectedKind: MusicLibraryKind
  selectedYear: number | null
  query: string
  artists: ArtistLibraryPageResponse | null
  albums: AlbumLibraryPageResponse | null
  tracks: TrackLibraryPageResponse | null
  searchResults: MusicSearchResponse | null
  yearResults: MusicByYearResponse | null
}): MusicLibraryPageData {
  const selectedCopy = kindCopy(payload.selectedKind)
  const searchQuery = payload.query.trim()
  const isSearch = !!searchQuery
  const yearMode = payload.selectedYear != null && !!payload.yearResults

  if (yearMode && payload.yearResults) {
    const yearAlbums = buildMusicLibraryCards('albums', payload.yearResults.albums)
    const yearArtists = payload.yearResults.artists.map(yearArtistCard)
    const yearTracks = payload.yearResults.tracks.map(yearTrackCard)
    const spotlight = buildSpotlightFromCard(yearAlbums[0], `O ano musical de ${payload.yearResults.year}`)

    return {
      hero: {
        title: `A música em ${payload.yearResults.year}`,
        intro: 'Um recorte anual para reconhecer fases de escuta sem perder o peso editorial da seção. O eixo continua sendo álbum, com artistas e faixas entrando como apoio do mesmo período.',
        backLink: '/music',
        backLabel: 'Voltar ao recorte',
        accentLink: '/music/library?kind=albums',
        accentLabel: 'Ver biblioteca inteira',
        spotlight,
      },
      filters: {
        query: searchQuery,
        selectedKind: 'albums',
        selectedYear: payload.selectedYear,
        tabs: tabs(payload.stats.total),
        years: buildYearChips(payload.stats),
      },
      context: {
        eyebrow: 'Ano',
        title: `O que ${payload.yearResults.year} concentrou`,
        description: 'O ano funciona aqui como memória de fase: quais discos seguraram mais peso, quais artistas puxaram o período e quais faixas insistiram em voltar.',
        summary: `${formatShortNumber(payload.yearResults.stats.uniqueAlbumsCount)} álbuns, ${formatShortNumber(payload.yearResults.stats.uniqueArtistsCount)} artistas e ${formatShortNumber(payload.yearResults.stats.playsCount)} plays compõem o recorte anual.`,
        metrics: [
          {
            id: 'plays',
            label: 'Plays no ano',
            value: formatShortNumber(payload.yearResults.stats.playsCount),
            note: 'o volume bruto de escuta que realmente passou por esse período',
          },
          {
            id: 'albums',
            label: 'Álbuns no ano',
            value: formatShortNumber(payload.yearResults.stats.uniqueAlbumsCount),
            note: 'o eixo principal de navegação e memória dentro do recorte anual',
          },
          {
            id: 'artists',
            label: 'Artistas no ano',
            value: formatShortNumber(payload.yearResults.stats.uniqueArtistsCount),
            note: 'quem realmente estruturou essa fase musical',
          },
          {
            id: 'tracks',
            label: 'Faixas no ano',
            value: formatShortNumber(payload.yearResults.stats.uniqueTracksCount),
            note: 'a camada fina de repetição e retorno desse período',
          },
        ],
      },
      sections: [
        {
          id: 'year-albums',
          eyebrow: 'Álbuns do ano',
          title: 'Os discos que seguraram o centro do período',
          description: 'O miolo do recorte anual: capa, repetição e cobertura do álbum como memória principal.',
          summary: 'Aqui o ano é tratado como fase de escuta, não como tabela de arquivo.',
          items: yearAlbums,
          emptyMessage: 'Nenhum álbum apareceu nesse ano.',
        },
        {
          id: 'year-artists',
          eyebrow: 'Artistas do ano',
          title: 'Quem puxou essa fase',
          description: 'Os artistas entram como espinha dorsal do período, apoiando a leitura dos álbuns mais presentes.',
          summary: 'Eles organizam o recorte sem competir com o protagonismo do álbum.',
          items: yearArtists,
          emptyMessage: 'Nenhum artista apareceu nesse ano.',
        },
        {
          id: 'year-tracks',
          eyebrow: 'Faixas do ano',
          title: 'O detalhe que voltou mais',
          description: 'A camada de replay fino que ajuda a reconhecer o período no nível da faixa.',
          summary: 'Faixa aparece como apoio tático, não como centro da página.',
          items: yearTracks,
          emptyMessage: 'Nenhuma faixa apareceu nesse ano.',
        },
      ],
      libraryCursor: null,
      mode: 'year',
    }
  }

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
      selectedYear: payload.selectedYear,
      tabs: tabs(payload.stats.total),
      years: buildYearChips(payload.stats),
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
        : `${formatShortNumber(payload.stats.total.uniqueArtistsCount)} artistas, ${formatShortNumber(payload.stats.total.uniqueAlbumsCount)} álbuns e ${formatShortNumber(payload.stats.total.uniqueTracksCount)} faixas compõem o arquivo já tocado de música.`,
      metrics: buildLibraryMetrics(payload.stats),
    },
    sections: [
      {
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
    ],
    libraryCursor: isSearch ? null : libraryCursor,
    mode: isSearch ? 'search' : 'library',
  }
}

export function buildAlbumPageData(album: AlbumPageResponse): AlbumPageData {
  return {
    id: String(album.albumId),
    title: album.albumTitle,
    artistName: album.artistName,
    artistHref: `/music/artists/${album.artistId}`,
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

export function buildArtistPageData(artist: ArtistPageResponse): ArtistPageData {
  const albums = artist.albums.map((album) => albumCard({
    albumId: album.albumId,
    albumTitle: album.albumTitle,
    artistId: artist.artistId,
    artistName: artist.artistName,
    coverUrl: album.coverUrl,
    year: album.year,
    totalTracks: album.totalTracks,
    playedTracks: album.playedTracks,
    playCount: album.playCount,
    lastPlayed: album.lastPlayed,
  }))
  const heroCover = artist.albums.find((album) => album.coverUrl)?.coverUrl ?? null

  return {
    id: String(artist.artistId),
    title: artist.artistName,
    coverUrl: heroCover,
    heroMeta: [
      `${formatShortNumber(artist.libraryAlbumsCount)} álbuns na library`,
      `${formatShortNumber(artist.libraryTracksCount)} faixas na library`,
      `${formatShortNumber(artist.totalPlays)} plays`,
      artist.lastPlayed ? `Último ${formatRelativeDate(artist.lastPlayed)}` : 'Sem play recente',
    ],
    stats: {
      totalPlays: artist.totalPlays,
      libraryAlbumsCount: artist.libraryAlbumsCount,
      libraryTracksCount: artist.libraryTracksCount,
      uniqueAlbumsPlayed: artist.uniqueAlbumsPlayed,
      uniqueTracksPlayed: artist.uniqueTracksPlayed,
      latestPlay: artist.lastPlayed ? formatRelativeDate(artist.lastPlayed) : 'Sem play recente',
      latestPlayAbsolute: artist.lastPlayed ? formatAbsoluteDate(artist.lastPlayed) : null,
    },
    albums,
    topTracks: artist.topTracks.map(mapArtistTrack),
    recentDays: artist.playsByDay.slice(-12).map((day) => ({
      id: day.day,
      label: day.day,
      plays: day.plays,
    })),
  }
}
