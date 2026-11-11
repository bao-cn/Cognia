import type { FavoriteList } from '@/types/api'
import { http } from './http'

export const favoriteApi = {
  list(): Promise<FavoriteList> {
    return http.get<FavoriteList>('/favorites')
  },
  favoriteConversation(conversationId: string): Promise<boolean> {
    return http.put<boolean>(`/favorites/conversations/${conversationId}`)
  },
  unfavoriteConversation(conversationId: string): Promise<boolean> {
    return http.delete<boolean>(`/favorites/conversations/${conversationId}`)
  },
  favoriteMessage(messageId: string): Promise<boolean> {
    return http.put<boolean>(`/favorites/messages/${messageId}`)
  },
  unfavoriteMessage(messageId: string): Promise<boolean> {
    return http.delete<boolean>(`/favorites/messages/${messageId}`)
  },
}
