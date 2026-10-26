export interface OwnerSession {
  authenticated: boolean
  csrfToken: string
}

export function useOwnerAuth() {
  return useState<OwnerSession>('owner-session', () => ({ authenticated: false, csrfToken: '' }))
}
