import { http } from './http'
import type {
  AdminStorageSettings,
  StorageConnectionTestResult,
  StoragePresignedUrl,
  StoredObject,
  UpdateStorageSettingsPayload,
} from '@/types/api'

export const storageApi = {
  getSettings() {
    return http.get<AdminStorageSettings>('/storage/settings')
  },
  updateSettings(payload: UpdateStorageSettingsPayload) {
    return http.put<AdminStorageSettings>('/storage/settings', payload)
  },
  testConnection() {
    return http.post<StorageConnectionTestResult>('/storage/settings/test')
  },
  syncLifecycle() {
    return http.post<{ message: string }>('/storage/settings/lifecycle/sync')
  },
  uploadObject(file: File, directory?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (directory) {
      formData.append('directory', directory)
    }
    return http.post<StoredObject>('/storage/objects', formData, {
      timeout: 120000,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  uploadTemp(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<StoredObject>('/storage/temp', formData, {
      timeout: 120000,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  deleteObject(key: string) {
    return http.delete<void>('/storage/objects', { params: { key } })
  },
  presignedUrl(key: string, expiresInSeconds?: number) {
    return http.get<StoragePresignedUrl>('/storage/objects/presigned-url', {
      params: { key, expiresInSeconds },
    })
  },
}
