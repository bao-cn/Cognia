import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
    roles?: string[]
    permissions?: string[]
    docType?: 'PRIVACY' | 'TERMS' | 'OPEN_SOURCE'
    standalone?: boolean
  }
}
