import { http } from './http'
import type { PermissionItem, RoleItem, RolePayload } from '@/types/api'

export const roleApi = {
  list(keyword?: string): Promise<RoleItem[]> {
    return http.get<RoleItem[]>('/roles', {
      params: keyword?.trim() ? { keyword: keyword.trim() } : undefined,
    })
  },
  get(id: string): Promise<RoleItem> {
    return http.get<RoleItem>(`/roles/${id}`)
  },
  create(payload: RolePayload): Promise<RoleItem> {
    return http.post<RoleItem>('/roles', payload)
  },
  update(id: string, payload: RolePayload): Promise<RoleItem> {
    return http.put<RoleItem>(`/roles/${id}`, payload)
  },
  remove(id: string): Promise<void> {
    return http.delete<void>(`/roles/${id}`)
  },
  assignPermissions(id: string, permissionIds: string[]): Promise<RoleItem> {
    return http.put<RoleItem>(`/roles/${id}/permissions`, { permissionIds })
  },
}

export const permissionApi = {
  list(): Promise<PermissionItem[]> {
    return http.get<PermissionItem[]>('/permissions')
  },
}
