import { http } from './http'

export interface SearchChannelHit {
  id: number
  name: string
  slug: string
  description?: string
  ownerType: 'USER' | 'TEAM'
  ownerSlug: string
}

export interface SearchUserHit {
  id: number
  username: string
  nickname?: string
  avatar?: string
}

export interface SearchConversationHit {
  id: string
  title: string
  updateTime?: string
}

export interface GlobalSearchResult {
  channels: SearchChannelHit[]
  users: SearchUserHit[]
  conversations: SearchConversationHit[]
}

export const searchApi = {
  query(keyword: string): Promise<GlobalSearchResult> {
    return http.get<GlobalSearchResult>('/search', { params: { q: keyword } })
  },
}
