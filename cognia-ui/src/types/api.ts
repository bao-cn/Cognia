export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

export interface RoleSummary {
  id: string
  code: string
  name: string
}

export interface UserProfile {
  id: string
  username: string
  nickname?: string
  email: string
  phone?: string
  avatar?: string
  status: number
  roles: RoleSummary[]
  permissions?: string[]
  lastLoginTime?: string
  lastLoginIp?: string
  createTime?: string
  remark?: string
}

export interface LoginPayload {
  account: string
  password: string
  turnstileToken?: string
  rememberMe?: boolean
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  refreshExpiresIn: number
  user: UserProfile
}

export interface RegisterPayload {
  username: string
  nickname?: string
  email: string
  password: string
  code?: string
}

export interface SendCodeResult {
  message: string
  expiresInSeconds: number
  resendAfterSeconds: number
  devCode?: string
}

export interface CreateUserPayload {
  username: string
  nickname?: string
  email: string
  password: string
  phone?: string
  status?: number
  roleIds?: string[]
}

export interface UpdateUserPayload {
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  status?: number
  remark?: string
}

export interface UserQuery {
  page?: number
  size?: number
  keyword?: string
  status?: number
  roleId?: string
}

export interface RoleItem {
  id: string
  code: string
  name: string
  description?: string
  sort: number
  status: number
  builtin: boolean
  permissionIds: string[]
  userCount: number
  createTime?: string
}

export interface RolePayload {
  code: string
  name: string
  description?: string
  sort?: number
  status?: number
  permissionIds?: string[]
}

export interface PermissionItem {
  id: string
  code: string
  name: string
  type: string
  resource?: string
  method?: string
  parentId?: string
  sort: number
}

// ---------------------------------------------------------------------------
// Site settings
// ---------------------------------------------------------------------------

export type LanguageCode = 'zhCn' | 'enUs'

export interface PublicSiteSettings {
  siteName: string
  logoUrl: string
  siteDescription?: string
  keywords: string[]
  routeSuffix: string
  defaultLanguage: LanguageCode
  timezone: string
  allowedUploadTypes: string[]
  registrationEnabled: boolean
  registrationEmailVerification: boolean
  registrationEmailWhitelistEnabled: boolean
  registerEmailCaptchaEnabled: boolean
  loginCaptchaEnabled: boolean
  appleLoginEnabled: boolean
  googleLoginEnabled: boolean
  turnstileConfigured: boolean
  turnstileSiteKey?: string
  teamCreationLimit: number
  teamJoinLimit: number
}

export interface AdminSiteSettings {
  logoUrl?: string
  siteName: string
  siteDescription?: string
  keywords: string[]
  routeSuffix: string
  defaultLanguage: LanguageCode
  timezone: string
  allowedUploadTypes: string[]
  registrationEnabled: boolean
  registrationEmailVerification: boolean
  registerEmailCaptchaEnabled: boolean
  loginCaptchaEnabled: boolean
  registrationEmailWhitelistEnabled: boolean
  registrationEmailWhitelist: string[]
  appleLoginEnabled: boolean
  googleLoginEnabled: boolean
  turnstileSiteKey?: string
  turnstileSecretKeyConfigured: boolean
  smtpEnabled: boolean
  smtpHost?: string
  smtpPort: number
  smtpUsername?: string
  smtpPasswordConfigured: boolean
  smtpFrom?: string
  smtpSsl: boolean
  smtpStarttls: boolean
  smtpAuth: boolean
  teamCreationLimit: number
  teamJoinLimit: number
  maxFolderDepth: number
  recycleRetentionDays: number
  updatedAt?: string
}

export interface UpdateSiteSettingsPayload {
  logoUrl?: string
  siteName: string
  siteDescription?: string
  keywords?: string[]
  routeSuffix?: string
  defaultLanguage: LanguageCode
  timezone: string
  allowedUploadTypes?: string[]
  registrationEnabled?: boolean
  registrationEmailVerification?: boolean
  registerEmailCaptchaEnabled?: boolean
  loginCaptchaEnabled?: boolean
  registrationEmailWhitelistEnabled?: boolean
  registrationEmailWhitelist?: string[]
  appleLoginEnabled?: boolean
  googleLoginEnabled?: boolean
  turnstileSiteKey?: string
  turnstileSecretKey?: string
  clearTurnstileSecretKey?: boolean
  smtpEnabled?: boolean
  smtpHost?: string
  smtpPort?: number
  smtpUsername?: string
  smtpPassword?: string
  smtpFrom?: string
  smtpSsl?: boolean
  smtpStarttls?: boolean
  smtpAuth?: boolean
  clearSmtpPassword?: boolean
  teamCreationLimit?: number
  teamJoinLimit?: number
  maxFolderDepth?: number
  recycleRetentionDays?: number
}

// ---------------------------------------------------------------------------
// Teams
// ---------------------------------------------------------------------------

export interface TeamMember {
  userId: string
  username: string
  nickname: string
  avatar: string
  role: 'OWNER' | 'MANAGER' | 'EDITOR' | 'MEMBER' | string
  joinedAt?: string
}

export interface TeamLink {
  label: string
  url: string
}

export interface TeamChannel {
  id: string
  name: string
  description?: string
  isPublic: boolean
  pinned: boolean
  pinnedOrder: number
  createdBy: string
  creatorName: string
  createTime?: string
}

export interface AdminTeam {
  id: string
  name: string
  slug: string
  description?: string
  logoUrl?: string
  ownerId?: string
  ownerName?: string
  ownerUsername?: string
  memberCount: number
  channelCount: number
  createTime?: string
}

export interface TeamSummary {
  id: string
  name: string
  slug: string
  description?: string
  logoUrl?: string
  ownerId: string
  ownerName: string
  memberCount: number
  role?: 'OWNER' | 'MANAGER' | 'EDITOR' | 'MEMBER' | string | null
  joinedAt?: string | null
  createTime?: string
}

export interface TeamDetail {
  id: string
  name: string
  slug: string
  description?: string
  logoUrl?: string
  readme?: string
  links: TeamLink[]
  joinRequestsEnabled: boolean
  defaultMemberRole: 'MANAGER' | 'EDITOR' | 'MEMBER' | string
  owner?: TeamMember | null
  memberCount: number
  members: TeamMember[]
  channels: TeamChannel[]
  member: boolean
  isOwner: boolean
  viewerRole: 'OWNER' | 'MANAGER' | 'EDITOR' | 'MEMBER' | string
  canManageSettings: boolean
  canCreateChannel: boolean
  canPinChannel: boolean
  canDeleteAnyChannel: boolean
  createTime?: string
}

export interface TeamInvitation {
  id: string
  teamId: string
  teamName: string
  teamDescription?: string
  teamLogoUrl?: string
  inviterId: string
  inviterName: string
  inviteeId: string
  inviteeName: string
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | string
  createTime?: string
}

export interface TeamUserSearch {
  id: string
  username: string
  nickname: string
  avatar: string
  member: boolean
  invited: boolean
}

export interface TeamLimits {
  createdCount: number
  creationLimit: number
  joinedCount: number
  joinLimit: number
}

export interface CreateTeamPayload {
  name: string
  slug?: string
  description?: string
  logoUrl?: string
}

// ---------------------------------------------------------------------------
// Legal documents / consent
// ---------------------------------------------------------------------------

export type LegalDocType = 'PRIVACY' | 'TERMS' | 'OPEN_SOURCE'

export interface LegalDocument {
  id: string
  type: LegalDocType
  title: string
  content: string
  version: number
  publishedAt?: string
  updatedAt?: string
}

export interface ConsentStatus {
  documents: LegalDocument[]
  consents: Record<string, number>
  requiresConsent: boolean
}

export interface UpdateLegalDocumentPayload {
  title: string
  content: string
}

// ---------------------------------------------------------------------------
// Mail templates
// ---------------------------------------------------------------------------

export interface MailTemplate {
  code: string
  name: string
  description?: string
  subject: string
  content: string
  placeholders: string[]
  updatedAt?: string
}

export interface UpdateMailTemplatePayload {
  subject: string
  content: string
}

export interface MailTemplatePreview {
  subject: string
  html: string
  text: string
}

// ---------------------------------------------------------------------------
// Desktop download assets
// ---------------------------------------------------------------------------

export interface DownloadAsset {
  code: string
  name: string
  platform: 'macos' | 'windows' | 'linux' | string
  arch: string
  packageType?: string
  version: string
  downloadUrl?: string
  sha256?: string
  fileSize?: number
  enabled: boolean
  updatedAt?: string
}

export interface UpdateDownloadAssetPayload {
  name: string
  version: string
  downloadUrl?: string
  sha256?: string
  fileSize?: number
  enabled: boolean
}

// ---------------------------------------------------------------------------
// S3-compatible object storage
// ---------------------------------------------------------------------------

export interface AdminStorageSettings {
  enabled: boolean
  configured: boolean
  endpoint?: string
  region: string
  bucket?: string
  accessKey?: string
  secretKeyConfigured: boolean
  pathStyleAccess: boolean
  publicBaseUrl?: string
  objectPrefix?: string
  maxFileSizeMb: number
  presignedUrlExpirySeconds: number
  tempRetentionDays?: number
  updatedAt?: string
}

export interface UpdateStorageSettingsPayload {
  enabled: boolean
  endpoint?: string
  region: string
  bucket?: string
  accessKey?: string
  secretKey?: string
  clearSecretKey?: boolean
  pathStyleAccess: boolean
  publicBaseUrl?: string
  objectPrefix?: string
  maxFileSizeMb: number
  presignedUrlExpirySeconds: number
  tempRetentionDays?: number
}

export interface StorageConnectionTestResult {
  success: boolean
  bucketExists: boolean
  canWrite: boolean
  canRead: boolean
  canDelete: boolean
  latencyMs: number
  endpoint?: string
  region: string
  bucket?: string
  message: string
  testedAt: string
}

export interface StoredObject {
  key: string
  url?: string
  size: number
  contentType: string
  uploadedAt: string
}

export interface StoragePresignedUrl {
  key: string
  url: string
  expiresInSeconds: number
  expiresAt: string
}

// ---------------------------------------------------------------------------
// Audit log, notifications and preset scheduled tasks
// ---------------------------------------------------------------------------

export interface AuditLogEntry {
  id: number
  userId?: number
  username?: string
  module: string
  action: string
  description?: string
  resourceType?: string
  resourceId?: string
  method?: string
  path?: string
  ip?: string
  userAgent?: string
  payload?: string
  status: 'SUCCESS' | 'FAILED' | string
  errorMessage?: string
  durationMs?: number
  createTime: string
}

export interface AppNotification {
  id: number
  type: string
  title: string
  content?: string
  payload?: Record<string, unknown>
  read: boolean
  createTime: string
}

export interface ScheduledTask {
  id: number
  code: string
  name: string
  description?: string
  enabled: boolean
  cronExpression: string
  thresholdMinutes?: number
  retentionDays?: number
  olderThanHours?: number
  batchSize?: number
  lastRunAt?: string
  lastStatus?: string
  lastMessage?: string
  lastDurationMs?: number
}

export interface UpdateScheduledTaskPayload {
  enabled?: boolean
  cronExpression?: string
  thresholdMinutes?: number
  retentionDays?: number
  olderThanHours?: number
  batchSize?: number
}

export interface TaskRunResult {
  code: string
  status: string
  message: string
  durationMs: number
  ranAt: string
}

export interface ProfileLink {
  label: string
  url: string
}

export interface OwnerTeamRef {
  id: string
  name: string
  slug: string
  logoUrl?: string
  role: string
  memberCount: number
}

export interface ChannelCard {
  id: string
  name: string
  slug: string
  description?: string
  isPublic: boolean
  pinned: boolean
  pinnedOrder: number
  documentCount: number
  updateTime: string
  featured?: boolean
  webSearchEnabled?: boolean
}

export interface DiscoverChannel extends ChannelCard {
  ownerType: 'USER' | 'TEAM'
  ownerId: string
  ownerName: string
  ownerSlug: string
  subscriberCount: number
  subscribed: boolean
  notifySubscribed: boolean
}

export interface ChatCapabilities {
  webSearchEnabled: boolean
  allowUserWebSearch: boolean
  webSearchAvailable: boolean
}

export interface OwnerProfile {
  type: 'USER' | 'TEAM'
  id: string
  username: string
  nickname: string
  avatar?: string
  gender?: string
  bio?: string
  company?: string
  hometown?: string
  timezone?: string
  email?: string
  readme?: string
  links: ProfileLink[]
  badges: string[]
  teams: OwnerTeamRef[]
  channels: ChannelCard[]
  pinnedChannels: ChannelCard[]
  memberCount: number
  viewerRole?: string
  self: boolean
  canEdit: boolean
  createTime: string
}

export interface UpdateOwnerProfilePayload {
  nickname?: string
  gender?: string
  bio?: string
  company?: string
  hometown?: string
  timezone?: string
  readme?: string
  links?: ProfileLink[]
}

export interface ChannelFolder {
  id: string
  parentId?: string
  name: string
  depth: number
}

export interface ChannelDocument {
  id: string
  name: string
  folderId?: string
  size: number
  contentType?: string
  status: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED' | 'DELETED'
  errorMessage?: string
  retryCount: number
  title?: string
  documentType?: string
  expirationTime?: string
  createTime?: string
  processedAt?: string
}

export interface ChannelDetail {
  channel: ChannelCard & {
    ownerType: 'USER' | 'TEAM'
    ownerId: string
    ownerName?: string
    readmeKey?: string
    agentKey?: string
    agentName?: string
    agentDescription?: string
    agentTags?: string
  }
  folders: ChannelFolder[]
  documents: ChannelDocument[]
  canWrite: boolean
  canManage: boolean
  canNotify: boolean
}

export interface CreateChannelPayload {
  ownerType: 'USER' | 'TEAM'
  ownerId?: string
  name: string
  slug: string
  description?: string
  isPublic: boolean
}

export interface UpdateChannelPayload {
  name: string
  description?: string
  isPublic: boolean
  webSearchEnabled?: boolean
}

export interface RecycleBinEntry {
  id: string
  documentId?: string
  ownerType: 'USER' | 'TEAM'
  ownerId: string
  channelId?: string
  name: string
  size: number
  contentType?: string
  deletedBy?: string
  deleteTime: string
  retentionDays: number
  expiresAt?: string
}

export interface ModelProvider {
  id: number
  name: string
  vendor?: string
  baseUrl: string
  apiKeyConfigured: boolean
  enabled: boolean
  modelCount: number
  createTime?: string
  updatedAt?: string
}

export interface SaveModelProviderPayload {
  name: string
  vendor?: string
  baseUrl: string
  apiKey?: string
  clearApiKey?: boolean
  enabled?: boolean
}

export interface RemoteModel {
  id: string
  ownedBy?: string
}

export type ModelType = 'CHAT' | 'EMBEDDING' | 'RERANK'

export interface ModelConfig {
  id: number
  providerId: number
  providerName: string
  modelId: string
  displayName: string
  modelType: ModelType
  enabled: boolean
  visible: boolean
  supportsTools: boolean
  thinkingEnabled: boolean
  supportsVision?: boolean
  reasoningEffort?: string
  extraBody?: string
  dimension?: number
  contextWindow?: number
  updatedAt?: string
}

export interface SaveModelConfigPayload {
  providerId: number
  modelId: string
  modelType: ModelType
  displayName?: string
  enabled?: boolean
  visible?: boolean
  supportsTools?: boolean
  thinkingEnabled?: boolean
  supportsVision?: boolean
  reasoningEffort?: string
  extraBody?: string
  dimension?: number
  contextWindow?: number
}

export interface PublicChatModel {
  id: number
  provider: string
  model: string
  name: string
  supportsTools: boolean
  thinking: boolean
  supportsVision?: boolean
}

export interface RagSettings {
  chatModelId?: number
  embeddingModelId?: number
  rerankEnabled: boolean
  rerankModelId?: number
  chunkSize: number
  chunkOverlap: number
  retrievalTopK: number
  retrievalTopN: number
  webSearchEndpoint?: string
  webSearchApiKeyConfigured: boolean
  webSearchEnabled: boolean
  allowUserWebSearch: boolean
  semanticCacheEnabled: boolean
  retrievalCacheEnabled: boolean
  semanticCacheThreshold: number
  semanticCacheTtlMinutes: number
  semanticCacheMaxEntries: number
  retrievalCacheTtlMinutes: number
  retrievalCacheMaxEntries: number
  titleGenerationEnabled: boolean
  contextCompressionThreshold: number
  compressionPrompt?: string
  updatedAt?: string
}

export interface UpdateRagSettingsPayload {
  chatModelId?: number
  embeddingModelId?: number
  rerankEnabled?: boolean
  rerankModelId?: number
  chunkSize?: number
  chunkOverlap?: number
  retrievalTopK?: number
  retrievalTopN?: number
  webSearchEndpoint?: string
  webSearchApiKey?: string
  clearWebSearchApiKey?: boolean
  webSearchEnabled?: boolean
  allowUserWebSearch?: boolean
  semanticCacheEnabled?: boolean
  retrievalCacheEnabled?: boolean
  semanticCacheThreshold?: number
  semanticCacheTtlMinutes?: number
  semanticCacheMaxEntries?: number
  retrievalCacheTtlMinutes?: number
  retrievalCacheMaxEntries?: number
  titleGenerationEnabled?: boolean
  contextCompressionThreshold?: number
  compressionPrompt?: string
}

export interface PromptSettings {
  systemPrompt: string
  ragPrompt?: string
  expiredPrompt?: string
  toolPrompt?: string
  updatedAt?: string
}

export interface UpdatePromptSettingsPayload {
  systemPrompt?: string
  ragPrompt?: string
  expiredPrompt?: string
  toolPrompt?: string
}

export interface ChatConversation {
  id: string
  title: string
  modelId?: number
  channelIds?: number[]
  createTime?: string
  updateTime?: string
}

export interface ChatConversationMessage {
  role: 'user' | 'assistant'
  content: string
  reasoning?: string
  createTime?: string
}

export interface ChatConversationDetail extends ChatConversation {
  messages: ChatConversationMessage[]
}

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
