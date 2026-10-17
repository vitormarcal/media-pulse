import type { BookSeriesMetadataField, BookSeriesSuggestion } from '~/types/book-series'
export function defaultBookSeriesFields(suggestion: BookSeriesSuggestion): BookSeriesMetadataField[] {
  const fields: BookSeriesMetadataField[] = []
  if (suggestion.description) fields.push('DESCRIPTION')
  if (suggestion.hardcover.primaryAuthor) fields.push('PRIMARY_AUTHOR')
  if (suggestion.hardcover.isCompleted != null) fields.push('PUBLICATION_STATUS')
  if (suggestion.hardcover.booksCount != null || suggestion.hardcover.primaryBooksCount != null) fields.push('COUNTS')
  if (suggestion.hardcover.slug) fields.push('EXTERNAL_SLUG')
  return fields
}
