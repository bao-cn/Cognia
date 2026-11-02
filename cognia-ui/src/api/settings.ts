import { http } from './http'
import type {
  AdminSiteSettings,
  DownloadAsset,
  MailTemplate,
  MailTemplatePreview,
  PublicSiteSettings,
  UpdateDownloadAssetPayload,
  UpdateMailTemplatePayload,
  UpdateSiteSettingsPayload,
} from '@/types/api'

export const settingsApi = {
  public(): Promise<PublicSiteSettings> {
    return http.get<PublicSiteSettings>('/settings/public')
  },
  getAdmin(): Promise<AdminSiteSettings> {
    return http.get<AdminSiteSettings>('/settings')
  },
  update(payload: UpdateSiteSettingsPayload): Promise<AdminSiteSettings> {
    return http.put<AdminSiteSettings>('/settings', payload)
  },
  testSmtp(recipient: string): Promise<void> {
    return http.post<void>('/settings/smtp/test', { recipient })
  },
  mailTemplates(): Promise<MailTemplate[]> {
    return http.get<MailTemplate[]>('/settings/mail-templates')
  },
  updateMailTemplate(code: string, payload: UpdateMailTemplatePayload): Promise<MailTemplate> {
    return http.put<MailTemplate>(`/settings/mail-templates/${code}`, payload)
  },
  resetMailTemplate(code: string): Promise<MailTemplate> {
    return http.post<MailTemplate>(`/settings/mail-templates/${code}/reset`)
  },
  previewMailTemplate(code: string, payload: UpdateMailTemplatePayload): Promise<MailTemplatePreview> {
    return http.post<MailTemplatePreview>(`/settings/mail-templates/${code}/preview`, payload)
  },
  publicDownloads(): Promise<DownloadAsset[]> {
    return http.get<DownloadAsset[]>('/settings/downloads/public')
  },
  downloadAssets(): Promise<DownloadAsset[]> {
    return http.get<DownloadAsset[]>('/settings/downloads')
  },
  updateDownloadAsset(code: string, payload: UpdateDownloadAssetPayload): Promise<DownloadAsset> {
    return http.put<DownloadAsset>(`/settings/downloads/${code}`, payload)
  },
}
