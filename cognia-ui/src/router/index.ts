import { createRouter, createWebHashHistory, createWebHistory, type RouteLocationRaw, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useSiteStore } from '@/stores/site'
import { isElectronClient } from '@/lib/client-env'

const routes: Array<RouteRecordRaw> = [
  {
    name: 'Home',
    path: '/',
    redirect: '/chat',
    component: () => import('@/views/Home.vue'),
    children: [
      {
        name: 'Favorites',
        path: 'favorites',
        component: () => import('@/pages/Favorites.vue'),
        meta: {
          title: '我的收藏',
        },
      },
      {
        name: 'SettingsTeams',
        path: 'settings/teams',
        component: () => import('@/pages/Teams.vue'),
        meta: {
          title: '我的团队',
        },
      },
      {
        name: 'Settings',
        path: 'settings',
        component: () => import('@/pages/Settings.vue'),
        meta: {
          title: '设置',
        },
      },
      {
        name: 'Discover',
        path: 'discover',
        component: () => import('@/pages/Discover.vue'),
        meta: { title: '发现广场' },
      },
      {
        name: 'MyChannels',
        path: 'channels',
        component: () => import('@/pages/MyChannels.vue'),
        meta: { title: '我的频道' },
      },
      {
        name: 'Archive',
        path: 'archive',
        component: () => import('@/pages/Archive.vue'),
        meta: { title: '归档对话' },
      },
      {
        name: 'ChatTemporary',
        path: 'chat/temporary',
        component: () => import('@/pages/Chat.vue'),
        meta: { title: '临时对话' },
      },
      {
        name: 'Chat',
        path: 'chat',
        component: () => import('@/pages/Chat.vue'),
        meta: {
          title: '新对话',
        },
      },
      {
        name: 'ChatConversation',
        path: 'chat/:conversationId',
        component: () => import('@/pages/Chat.vue'),
        meta: {
          title: '对话',
        },
      },
      {
        name: 'LegacyTeams',
        path: 'teams',
        redirect: '/settings/teams',
      },
      {
        name: 'LegacyTeamDetail',
        path: 'teams/:pathMatch(.*)*',
        redirect: '/settings/teams',
      },
      {
        name: 'ChannelSettings',
        path: ':owner/:channelSlug/settings',
        component: () => import('@/pages/ChannelSettings.vue'),
        meta: { title: '频道设置', public: true },
      },
      {
        name: 'ChannelDetail',
        path: ':owner/:channelSlug',
        component: () => import('@/pages/ChannelDetail.vue'),
        meta: {
          title: '频道',
        },
      },
      {
        name: 'OwnerProfile',
        path: ':identifier',
        component: () => import('@/pages/OwnerProfile.vue'),
        meta: {
          title: '主页',
          public: true,
        },
      },
    ],
  },
  {
    name: 'OAuthCallback',
    path: '/oauth/callback',
    component: () => import('@/pages/OAuthCallback.vue'),
    meta: { title: '登录回调', public: true },
  },
  {
    name: 'Sign',
    path: '/sign',
    redirect: '/sign/',
    component: () => import('@/views/Sign.vue'),
    meta: {
      title: '登录',
      public: true,
    },
    children: [
      {
        name: 'SignIn',
        path: '',
        component: () => import('@/pages/SignIn.vue'),
        meta: {
          title: '登录',
          public: true,
        },
      },
      {
        name: 'SignUp',
        path: 'sign-up',
        component: () => import('@/pages/SignUp.vue'),
        meta: {
          title: '注册',
          public: true,
        },
      },
    ],
  },
  {
    name: 'Download',
    path: '/download',
    component: () => import('@/pages/Download.vue'),
    meta: {
      title: 'Cognia｜让知识在频道里生长',
      titleSuffix: false,
      public: true,
      standalone: true,
    },
  },
  {
    name: 'Console',
    path: '/console',
    redirect: '/console/dashboard',
    component: () => import('@/views/Admin.vue'),
    meta: {
      title: '控制台',
      permissions: ['console:access'],
    },
    children: [
      {
        name: 'ConsoleDashboard',
        path: 'dashboard',
        component: () => import('@/pages/console/AdminDashboard.vue'),
        meta: {
          title: '运营看板',
          permissions: ['dashboard:read', 'user:read', 'settings:read'],
        },
      },
      {
        name: 'ConsoleDepartment',
        path: 'org/tree',
        component: () => import('@/pages/console/Department.vue'),
        meta: {
          title: '部门管理',
          permissions: ['console:access'],
        },
      },
      {
        name: 'ConsoleUser',
        path: 'org/user',
        component: () => import('@/pages/console/User.vue'),
        meta: {
          title: '用户管理',
          permissions: ['user:read'],
        },
      },
      {
        name: 'ConsoleRole',
        path: 'org/role',
        component: () => import('@/pages/console/Role.vue'),
        meta: {
          title: '角色管理',
          permissions: ['role:read'],
        },
      },
      {
        name: 'ConsoleTeam',
        path: 'org/team',
        component: () => import('@/pages/console/Team.vue'),
        meta: {
          title: '团队管理',
          permissions: ['team:read', 'user:read'],
        },
      },
      {
        name: 'ConsoleSettings',
        path: 'setting',
        component: () => import('@/pages/console/System.vue'),
        meta: {
          title: '系统设置',
          permissions: ['settings:read'],
        },
      },
      {
        name: 'ConsoleOAuth',
        path: 'system/oauth',
        component: () => import('@/pages/console/OAuthConfig.vue'),
        meta: { title: '登录方式', permissions: ['settings:read'] },
      },
      {
        name: 'ConsoleObjectStorage',
        path: 'system/object-storage',
        component: () => import('@/pages/console/ObjectStorage.vue'),
        meta: {
          title: '对象存储',
          permissions: ['storage:read', 'settings:read'],
        },
      },

      {
        name: 'ConsoleModelConfig',
        path: 'system/model',
        component: () => import('@/pages/console/ModelConfig.vue'),
        meta: {
          title: '模型配置',
          permissions: ['model:read', 'settings:read'],
        },
      },
      {
        name: 'ConsoleToolConfig',
        path: 'system/tool',
        component: () => import('@/pages/console/ToolConfig.vue'),
        meta: {
          title: '工具配置',
          permissions: ['rag:read', 'settings:read'],
        },
      },
      {
        name: 'ConsoleRetrieval',
        path: 'system/retrieval',
        component: () => import('@/pages/console/Retrieval.vue'),
        meta: {
          title: '检索参数配置',
          permissions: ['rag:read', 'settings:read'],
        },
      },
      {
        name: 'ConsolePrompt',
        path: 'system/prompt',
        component: () => import('@/pages/console/PromptConfig.vue'),
        meta: {
          title: 'Prompt 配置',
          permissions: ['rag:read', 'settings:read'],
        },
      },
      {
        name: 'ConsoleDevTrace',
        path: 'system/dev',
        component: () => import('@/pages/console/DevTrace.vue'),
        meta: {
          title: '开发调试',
          permissions: ['trace:read', 'settings:read'],
        },
      },
      {
        name: 'ConsoleRecycleBin',
        path: 'kb/recycle',
        component: () => import('@/pages/console/RecycleBin.vue'),
        meta: {
          title: '文档回收站',
        },
      },
      {
        name: 'ConsoleAudit',
        path: 'security/audit',
        component: () => import('@/pages/console/AuditLog.vue'),
        meta: {
          title: '审计日志',
          permissions: ['audit:read', 'settings:read'],
        },
      },
      {
        name: 'ConsoleTask',
        path: 'task',
        component: () => import('@/pages/console/TaskSchedule.vue'),
        meta: {
          title: '计划任务',
          permissions: ['task:read', 'settings:read'],
        },
      },
      {
        // Legacy path kept for old bookmarks.
        name: 'ConsoleDepartmentLegacy',
        path: 'department',
        redirect: '/console/org/tree',
        meta: {
          permissions: ['console:access'],
        },
      },
    ],
  },
  {
    name: 'LegalPrivacy',
    path: '/legal/privacy',
    component: () => import('@/pages/Legal.vue'),
    meta: {
      title: '隐私政策',
      public: true,
      standalone: true,
      docType: 'PRIVACY',
    },
  },
  {
    name: 'LegalTerms',
    path: '/legal/terms',
    component: () => import('@/pages/Legal.vue'),
    meta: {
      title: '用户协议',
      public: true,
      standalone: true,
      docType: 'TERMS',
    },
  },
  {
    name: 'LegalLicenses',
    path: '/legal/licenses',
    component: () => import('@/pages/Legal.vue'),
    meta: {
      title: '开源许可',
      public: true,
      standalone: true,
      docType: 'OPEN_SOURCE',
    },
  },
  {
    name: 'Forbidden',
    path: '/403',
    component: () => import('@/pages/Forbidden.vue'),
    meta: {
      title: '无访问权限',
    },
  },
  {
    name: 'NotFound',
    path: '/:pathMatch(.*)*',
    component: () => import('@/pages/NotFound.vue'),
    meta: {
      title: '页面不存在',
      public: true,
    },
  },
]

// Electron loads the renderer from file:// in production. A hash history keeps
// deep links and refreshes working there, while the browser keeps clean URLs.
const history = isElectronClient() || (typeof window !== 'undefined' && window.location.protocol === 'file:')
  ? createWebHashHistory()
  : createWebHistory('/')

const router = createRouter({
  history,
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

function safeRedirect(value: unknown, fallback = '/chat'): string {
  if (typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')) {
    return value
  }
  return fallback
}

router.beforeEach(async (to): Promise<boolean | RouteLocationRaw> => {
  const siteStore = useSiteStore()
  if (!siteStore.loaded) {
    await siteStore.load()
  }

  const suffix = siteStore.routeSuffix ?? ''
  document.title = to.meta.title
    ? `${to.meta.title}${to.meta.titleSuffix === false ? '' : suffix}`
    : `${siteStore.siteName}${suffix}`

  const userStore = useUserStore()
  if (!userStore.initialized) {
    await userStore.restoreSession()
  }

  if (to.meta.public) {
    if (userStore.isLoggedIn && (to.name === 'SignIn' || to.name === 'SignUp')) {
      return safeRedirect(to.query.redirect)
    }
    return true
  }

  if (!userStore.isLoggedIn) {
    return {
      name: 'SignIn',
      query: to.fullPath !== '/' ? { redirect: to.fullPath } : undefined,
    }
  }

  if (to.meta.roles?.length && !to.meta.roles.some(role => userStore.hasRole(role))) {
    return { name: 'Forbidden' }
  }

  if (to.meta.permissions?.length
    && !to.meta.permissions.some(permission => userStore.hasPermission(permission))) {
    return { name: 'Forbidden' }
  }

  return true
})

export default router
