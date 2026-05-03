export interface MediaCommentDto {
  id: number
  body: string
  commentedAt: string
  createdAt: string
  updatedAt: string
  edited: boolean
}

export interface CreateMediaCommentRequest {
  body: string
  commentedAt: string
}

export interface UpdateMediaCommentRequest {
  body: string
  commentedAt: string
}
