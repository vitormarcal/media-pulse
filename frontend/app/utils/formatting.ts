const locale = 'pt-BR'

export function formatRelativeDate(value: string | null | undefined) {
  if (!value) return 'Sem registro recente'

  const date = new Date(value)
  const diffMs = date.getTime() - Date.now()
  const minutes = Math.round(diffMs / 60000)
  const hours = Math.round(diffMs / 3600000)
  const days = Math.round(diffMs / 86400000)

  if (Math.abs(minutes) < 60) {
    return new Intl.RelativeTimeFormat(locale, { numeric: 'auto' }).format(minutes, 'minute')
  }

  if (Math.abs(hours) < 48) {
    return new Intl.RelativeTimeFormat(locale, { numeric: 'auto' }).format(hours, 'hour')
  }

  return new Intl.RelativeTimeFormat(locale, { numeric: 'auto' }).format(days, 'day')
}

export function formatAbsoluteDate(value: string | null | undefined) {
  if (!value) return ''

  return new Intl.DateTimeFormat(locale, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(new Date(value))
}

export function formatShortNumber(value: number) {
  return new Intl.NumberFormat(locale, {
    notation: value >= 1000 ? 'compact' : 'standard',
    maximumFractionDigits: 1,
  }).format(value)
}
