export { authApi } from './auth'
export { userApi } from './user'
export { permissionApi, roleApi } from './role'
export { settingsApi } from './settings'
export { legalApi } from './legal'
export { teamApi } from './team'
export { storageApi } from './storage'
export { discoverApi } from './discover'
export { recycleApi } from './recycle'
export { auditApi } from './audit'
export { taskApi } from './task'
export { notificationApi } from './notification'
export { profileApi } from './profile'
export { channelApi } from './channel'
export { modelApi } from './model'
export { ragApi } from './rag'
export { promptApi } from './prompt'
export { chatApi } from './chat'
export { searchApi } from './search'
export { ApiError, http, httpClient } from './http'

import { authApi } from './auth'
import { userApi } from './user'
import { permissionApi, roleApi } from './role'
import { settingsApi } from './settings'
import { legalApi } from './legal'
import { teamApi } from './team'
import { storageApi } from './storage'
import { recycleApi } from './recycle'
import { auditApi } from './audit'
import { taskApi } from './task'
import { notificationApi } from './notification'
import { profileApi } from './profile'
import { channelApi } from './channel'
import { modelApi } from './model'
import { ragApi } from './rag'
import { promptApi } from './prompt'
import { chatApi } from './chat'
import { searchApi } from './search'

import { discoverApi } from './discover'

export const api = {
  auth: authApi,
  user: userApi,
  role: roleApi,
  permission: permissionApi,
  settings: settingsApi,
  legal: legalApi,
  teams: teamApi,
  storage: storageApi,
  recycle: recycleApi,
  audit: auditApi,
  task: taskApi,
  notification: notificationApi,
  profile: profileApi,
  channel: channelApi,
  model: modelApi,
  rag: ragApi,
  prompt: promptApi,
  chat: chatApi,
  search: searchApi,
  discover: discoverApi,
}
