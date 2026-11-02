import { http } from './http'
import type {
  ConsentStatus,
  LegalDocument,
  LegalDocType,
  UpdateLegalDocumentPayload,
} from '@/types/api'

export const legalApi = {
  documents(): Promise<LegalDocument[]> {
    return http.get<LegalDocument[]>('/legal/documents')
  },
  consentStatus(): Promise<ConsentStatus> {
    return http.get<ConsentStatus>('/legal/consent')
  },
  submitConsent(privacyVersion: number, termsVersion: number): Promise<ConsentStatus> {
    return http.post<ConsentStatus>('/legal/consent', { privacyVersion, termsVersion })
  },
  publish(type: LegalDocType, payload: UpdateLegalDocumentPayload): Promise<LegalDocument> {
    return http.put<LegalDocument>(`/legal/${type}`, payload)
  },
}
