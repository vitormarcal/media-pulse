const locale = 'pt-BR'
const relativeDateFormatter = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })

function isDayPrecisionTimestamp(value: string, date: Date) {
  return (
    value.includes('T') &&
    date.getUTCHours() === 0 &&
    date.getUTCMinutes() === 0 &&
    date.getUTCSeconds() === 0 &&
    date.getUTCMilliseconds() === 0
  )
}

function diffCalendarDays(date: Date, now: Date) {
  const utcDate = Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())
  const utcNow = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate())
  return Math.round((utcDate - utcNow) / 86400000)
}

export function formatRelativeDate(value: string | null | undefined) {
  if (!value) return 'Sem registro recente'

  const date = new Date(value)
  const now = new Date()
  const diffMs = date.getTime() - now.getTime()
  const minutes = Math.round(diffMs / 60000)
  const hours = Math.round(diffMs / 3600000)
  const days = isDayPrecisionTimestamp(value, date) ? diffCalendarDays(date, now) : Math.round(diffMs / 86400000)

  if (Math.abs(minutes) < 60) {
    return relativeDateFormatter.format(minutes, 'minute')
  }

  if (Math.abs(hours) < 48 && !isDayPrecisionTimestamp(value, date)) {
    return relativeDateFormatter.format(hours, 'hour')
  }

  return relativeDateFormatter.format(days, 'day')
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
