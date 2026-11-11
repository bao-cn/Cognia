import { httpClient } from './http'

export const documentApi = {
  async raw(documentId: number | string): Promise<Blob> {
    const response = await httpClient.get<Blob>(`/documents/${documentId}/raw`, {
      responseType: 'blob',
      timeout: 60000,
    })
    return response.data
  },
}
