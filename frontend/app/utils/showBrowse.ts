import type { CurrentlyWatchingShowDto, EditorialHighlight, ShowCardDto } from '~/types/home'
import type { ShowLibraryCardDto, ShowLibraryCardModel, ShowsSearchResponse, ShowsByYearResponse } from '~/types/shows'
import { formatRelativeDate } from '~/utils/formatting'

export interface ShowBrowseCard extends ShowLibraryCardModel {
  showId: number
}
type Identity = Pick<ShowLibraryCardDto, 'showId' | 'title' | 'slug' | 'year' | 'coverUrl'>
export function showBrowseProgress(watched: number, total: number) {
  if (total <= 0)
    return watched > 0
      ? `${watched} ${watched === 1 ? 'episódio visto' : 'episódios vistos'} · total indisponível`
      : 'Sem episódios cadastrados'
  if (watched <= 0) return 'Sem episódio registrado'
  if (watched >= total)
    return total === 1 ? 'O episódio cadastrado foi visto' : `Todos os ${total} episódios cadastrados vistos`
  return `${watched}/${total} episódios vistos`
}
function card(show: Identity, activityLabel: string, progressLabel = ''): ShowBrowseCard {
  return {
    id: `browse-${show.showId}`,
    showId: show.showId,
    title: show.title,
    subtitle: show.year == null ? '' : String(show.year),
    href: show.slug ? `/shows/${show.slug}` : null,
    imageUrl: show.coverUrl,
    activityLabel,
    progressLabel,
    progressValue: 0,
    aside: '',
  }
}
function activity(date: string | null) {
  return date ? `Última atividade ${formatRelativeDate(date)}` : ''
}
export function showBrowseCards(shows: ShowLibraryCardDto[]) {
  return shows.map((show) =>
    card(show, activity(show.lastWatchedAt), showBrowseProgress(show.watchedEpisodesCount, show.episodesCount)),
  )
}
export function showBrowseSearchCards(shows: ShowsSearchResponse['shows']) {
  return shows.map((show) => card(show, show.watchedAt ? activity(show.watchedAt) : 'Sem episódio registrado'))
}
export function showBrowseYearCards(shows: ShowsByYearResponse['watched']) {
  return shows.map((show) =>
    card(
      show,
      `Último registro no ano ${formatRelativeDate(show.lastWatchedAt)}`,
      `${show.watchCountInYear} ${show.watchCountInYear === 1 ? 'registro de visualização' : 'registros de visualização'} no ano`,
    ),
  )
}
function highlight(
  show: Identity,
  timestamp: string | null,
  progress?: CurrentlyWatchingShowDto['progress'],
): EditorialHighlight {
  return {
    id: `show-${show.showId}`,
    type: 'show',
    title: show.title,
    subtitle: show.year == null ? '' : String(show.year),
    eyebrow: progress ? 'Em andamento' : 'Atividade recente',
    imageUrl: show.coverUrl,
    href: show.slug ? `/shows/${show.slug}` : null,
    timestamp: timestamp ?? '',
    meta: [
      progress ? showBrowseProgress(progress.watchedEpisodesCount, progress.episodesCount) : '',
      activity(timestamp),
    ]
      .filter(Boolean)
      .join(' · '),
  }
}
export function buildShowBrowseHero(current: CurrentlyWatchingShowDto[], recent: ShowCardDto[]) {
  const ongoing = current
    .filter(
      (show) =>
        show.progress.watchedEpisodesCount > 0 && show.progress.watchedEpisodesCount < show.progress.episodesCount,
    )
    .sort((a, b) => b.lastWatchedAt.localeCompare(a.lastWatchedAt))
  const candidates = new Map<number, EditorialHighlight>()
  for (const show of recent) {
    const item = highlight(show, show.watchedAt)
    if (!candidates.has(show.showId) || item.timestamp > candidates.get(show.showId)!.timestamp)
      candidates.set(show.showId, item)
  }
  for (const show of [...ongoing].reverse())
    candidates.set(show.showId, highlight(show, show.lastWatchedAt, show.progress))
  const sorted = [...candidates.values()].sort((a, b) => b.timestamp.localeCompare(a.timestamp))
  const lead = ongoing.length ? candidates.get(ongoing[0]!.showId)! : (sorted[0] ?? null)
  return {
    title: ongoing.length ? 'Séries em andamento' : 'Séries recentes',
    lead,
    supporting: sorted.filter((item) => item.id !== lead?.id).slice(0, 4),
    hasCurrent: ongoing.length > 0,
  }
}
