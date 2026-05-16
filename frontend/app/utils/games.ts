import type {
  GameDetailsResponse,
  GameLibraryCardDto,
  GameLibraryCardModel,
  GamePageData,
  GamesLibraryPageData,
  GamesLibraryResponse,
  GamesSearchResponse,
  GamesStatsResponse,
} from '~/types/games'
import { formatAbsoluteDate, formatRelativeDate, formatShortNumber } from '~/utils/formatting'

const statusLabels: Record<string, string> = {
  PLAYING: 'Jogando',
  BACKLOG: 'Backlog',
  COMPLETED: 'Finalizado',
  ABANDONED: 'Abandonado',
}

function gameHref(slug: string | null, gameId: number) {
  return `/games/${slug || gameId}`
}

export function buildGameLibraryCard(game: GameLibraryCardDto): GameLibraryCardModel {
  const statusLabel = game.currentStatus ? statusLabels[game.currentStatus] : 'Sem sessão'
  return {
    id: `game-${game.gameId}`,
    gameId: game.gameId,
    title: game.title,
    subtitle: [game.year ? String(game.year) : null, statusLabel].filter(Boolean).join(' · '),
    href: gameHref(game.slug, game.gameId),
    imageUrl: game.coverUrl,
    meta: game.latestSessionAt
      ? `${game.sessionCount} sessões · ${formatRelativeDate(game.latestSessionAt)}`
      : `${game.sessionCount} sessões`,
    isDormant: game.sessionCount === 0,
  }
}

export function buildGamesLibraryPageData(payload: {
  stats: GamesStatsResponse
  library: GamesLibraryResponse
  query: string
  searchResults: GamesSearchResponse | null
}): GamesLibraryPageData {
  const items = (payload.query && payload.searchResults ? payload.searchResults.games : payload.library.items).map(
    buildGameLibraryCard,
  )

  return {
    hero: {
      title: payload.query ? 'Games encontrados pela busca' : 'Games',
      intro: payload.query
        ? 'A busca encurta o caminho para encontrar um jogo já catalogado ou iniciar a entrada manual.'
        : 'Um catálogo manual para backlog, jornadas em andamento, finalizações e abandonos.',
      accentLink: payload.query ? `/games?q=${encodeURIComponent(payload.query)}&add=1` : '/games?add=1',
      accentLabel: 'Adicionar game',
      spotlight: items[0] ?? null,
    },
    query: payload.query,
    stats: [
      {
        id: 'catalog',
        label: 'Games no arquivo',
        value: formatShortNumber(payload.stats.totalGamesCount),
        note: 'entradas criadas manualmente',
      },
      {
        id: 'sessions',
        label: 'Sessões',
        value: formatShortNumber(payload.stats.sessionsCount),
        note: 'jornadas registradas',
      },
      {
        id: 'playing',
        label: 'Jogando',
        value: formatShortNumber(payload.stats.activeCount),
        note: 'estado mais recente',
      },
      {
        id: 'backlog',
        label: 'Backlog',
        value: formatShortNumber(payload.stats.backlogCount),
        note: 'ainda esperando vez',
      },
    ],
    items,
    libraryCursor: payload.query ? null : payload.library.nextCursor,
    mode: payload.query ? 'search' : 'library',
  }
}

export function buildGamePageData(game: GameDetailsResponse): GamePageData {
  const latestSession = game.sessions[0]?.startedAt ?? null
  const gallery = [
    ...(game.coverUrl ? [game.coverUrl] : []),
    ...game.images
      .slice()
      .sort((left, right) => {
        if (left.isPrimary !== right.isPrimary) return left.isPrimary ? -1 : 1
        return right.id - left.id
      })
      .map((image) => image.url),
  ].filter((value, index, array) => array.indexOf(value) === index)

  return {
    gameId: game.gameId,
    slug: game.slug ?? String(game.gameId),
    title: game.title,
    originalTitle: game.originalTitle,
    year: game.year,
    description: game.description,
    coverUrl: game.coverUrl,
    gallery: gallery.slice(0, 4),
    rating: game.rating,
    heroMeta: [
      game.year ? String(game.year) : null,
      `${game.sessions.length} sessões`,
      latestSession ? `Última ${formatRelativeDate(latestSession)}` : 'Sem sessão registrada',
    ].filter(Boolean) as string[],
    stats: {
      totalSessions: game.sessions.length,
      latestSessionAt: latestSession,
      latestSessionRelative: latestSession ? formatRelativeDate(latestSession) : 'Sem registro recente',
    },
    identifiers: game.externalIds.map((identifier) => ({
      id: `${identifier.provider}-${identifier.externalId}`,
      provider: identifier.provider,
      externalId: identifier.externalId,
    })),
    sessions: game.sessions.map((session) => ({
      id: `session-${session.sessionId}`,
      sessionId: session.sessionId,
      title: statusLabels[session.status],
      status: session.status,
      statusLabel: statusLabels[session.status],
      relativeStartedAt: formatRelativeDate(session.startedAt),
      meta: session.endedAt
        ? `${formatAbsoluteDate(session.startedAt)} até ${formatAbsoluteDate(session.endedAt)}`
        : `Desde ${formatAbsoluteDate(session.startedAt)}`,
      notes: session.notes,
    })),
    comments: game.comments,
  }
}
