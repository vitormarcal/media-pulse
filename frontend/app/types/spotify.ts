export type SpotifyAuthorizationStatus = 'UNKNOWN' | 'HEALTHY' | 'REAUTHORIZATION_REQUIRED' | 'ERROR'

export interface SpotifyStatusResponse {
  enabled: boolean
  status: SpotifyAuthorizationStatus
  lastSuccessAt: string | null
  lastFailureAt: string | null
  message: string | null
  reauthorizationAvailable: boolean
  reauthorizationUrl: string | null
}
