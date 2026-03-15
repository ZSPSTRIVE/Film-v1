import request from '@/utils/request'

export interface Actor {
  id: number
  name: string
  avatarUrl?: string
  roleType?: number
  characterName?: string
}

export interface Media {
  id: number
  title: string
  originalTitle?: string
  type: number
  status: number
  releaseDate?: string
  duration?: number
  coverUrl?: string
  backdropUrl?: string
  summary?: string
  rating?: number
  trailerUrl?: string
  actors?: Actor[]
  commentCount?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

export interface AiSearchResult {
  answer: string
  mediaList: Media[]
  normalizedQuery?: string
  intentSummary?: string
  retrievalMode?: string
  matchedCount?: number
}

export interface AiQaResult {
  conversationId: string
  answer: string
  references?: string[]
}

export const mediaApi = {
  searchMedia(params: {
    keyword?: string
    type?: number
    status?: number
    page?: number
    pageSize?: number
  }): Promise<ApiResponse<PageResult<Media>>> {
    return request.get('/v1/media/search', { params })
  },

  naturalLanguageSearch(params: {
    query: string
    page?: number
    pageSize?: number
  }): Promise<ApiResponse<AiSearchResult>> {
    return request.get('/v1/media/ai-search', { params })
  },

  getMediaDetail(id: number): Promise<ApiResponse<Media>> {
    return request.get(`/v1/media/${id}`)
  },

  generateSummary(data: {
    mediaId: number
    originalSummary: string
  }): Promise<ApiResponse<string>> {
    return request.post('/v1/media/ai-summary', null, { params: data })
  },

  askQuestion(
    mediaId: number,
    data: {
      question: string
      conversationId?: string
    }
  ): Promise<ApiResponse<AiQaResult>> {
    return request.post(`/v1/media/${mediaId}/ai-question`, data)
  }
}
