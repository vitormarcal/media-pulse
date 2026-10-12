import type { MediaRatingDto } from './ratings'
export interface ArtworkArtist {
  id: number
  name: string
  biography: string | null
  countryOfOrigin: string | null
  bornAt: string | null
  diedAt: string | null
  portraitUrl: string | null
}
export interface Artwork {
  id: number
  title: string
  artistId: number | null
  year: number | null
  type: string | null
  movements: string[]
  technique: string | null
  description: string | null
  favorite: boolean
  createdAt: string
  movementsRevision: number
  movementsSyncPending: boolean
  movementsSyncMessage: string | null
}
export interface ArtworkExperience {
  id: number
  artworkId: number
  experiencedAt: string | null
  experienceType: 'DIGITAL_VIEWING' | 'IN_PERSON' | null
  notes: string | null
  sourceName: string | null
  sourceUrl: string | null
  createdAt: string
}
export interface ArtworkImage {
  id: number
  artworkId: number
  url: string
  sourceUrl: string | null
  caption: string | null
  primary: boolean
}
export interface ArtworkList {
  id: number
  name: string
  description: string | null
  count?: number
}
export interface ArtworkCard {
  id: number
  title: string
  artist: ArtworkArtist | null
  year: number | null
  type: string | null
  movements: string[]
  favorite: boolean
  imageUrl: string | null
}
export interface ArtworkLibrary {
  items: ArtworkCard[]
  nextPage: number | null
}
export interface ArtworkDetails {
  artwork: Artwork
  artist: ArtworkArtist | null
  experiences: ArtworkExperience[]
  images: ArtworkImage[]
  lists: ArtworkList[]
  rating: MediaRatingDto | null
}
export type ArtworkExperienceInput = Pick<
  ArtworkExperience,
  'experiencedAt' | 'experienceType' | 'notes' | 'sourceName' | 'sourceUrl'
>
