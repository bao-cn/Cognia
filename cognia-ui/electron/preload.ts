import { contextBridge } from 'electron'

contextBridge.exposeInMainWorld('electronAPI', {
  isElectron: true,
  platform: process.platform,
  arch: process.arch,
  electronVersion: process.versions.electron,
})
