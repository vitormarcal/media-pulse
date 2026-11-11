export function albumTrackHighlights(tracks: ReadonlyArray<{ trackId: number; playCount: number }>): Set<number> {
  if (tracks.length < 2) return new Set()
  const highestCount = tracks.reduce((highest, track) => Math.max(highest, track.playCount), 0)
  if (highestCount < 3) return new Set()
  const leaders = tracks.filter((track) => track.playCount === highestCount)
  if (leaders.length === tracks.length || leaders.length > 3) return new Set()
  return new Set(leaders.map((track) => track.trackId))
}
