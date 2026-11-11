<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { roleApi, userApi } from '@/api'
import type { RoleItem, UserProfile } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Checkbox } from '@/components/ui/checkbox'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  IconChevronLeft,
  IconChevronRight,
  IconKey,
  IconPencil,
  IconPlus,
  IconRefresh,
  IconSearch,
  IconTrash,
  IconUsers,
} from '@tabler/icons-vue'

const userStore = useUserStore()

const loading = ref(false)
const rows = ref<UserProfile[]>([])
const total = ref(0)
const pages = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const statusFilter = ref('all')
const roleFilter = ref('all')
const roles = ref<RoleItem[]>([])

const canUpdate = computed(() => userStore.hasPermission('user:update'))
const canDelete = computed(() => userStore.hasPermission('user:delete'))
const canAssignRoles = computed(() => userStore.hasPermission('user:assign-role'))
const canResetPassword = computed(() => userStore.hasPermission('user:reset-password'))
const currentUserId = computed(() => userStore.profile?.id)

const dialogOpen = ref(false)
const editing = ref<UserProfile | null>(null)
const saving = ref(false)
const formError = ref('')
const form = reactive({
  username: '',
  nickname: '',
  email: '',
  password: '',
  phone: '',
  status: 1,
  roleIds: [] as string[],
})

const deleteOpen = ref(false)
const deleteTarget = ref<UserProfile | null>(null)
const deleting = ref(false)

const passwordOpen = ref(false)
const passwordTarget = ref<UserProfile | null>(null)
const newPassword = ref('')
const passwordError = ref('')
const resettingPassword = ref(false)

const isEdit = computed(() => Boolean(editing.value))
const rangeStart = computed(() => (total.value === 0 ? 0 : (page.value - 1) * size.value + 1))
const rangeEnd = computed(() => Math.min(page.value * size.value, total.value))

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

function initials(user: UserProfile): string {
  return (user.nickname || user.username).slice(0, 2).toUpperCase()
}

async function fetchUsers(): Promise<void> {
  loading.value = true
  try {
    const result = await userApi.list({
      page: page.value,
      size: size.value,
      keyword: keyword.value,
      status: statusFilter.value === 'all' ? undefined : Number(statusFilter.value),
      roleId: roleFilter.value === 'all' ? undefined : roleFilter.value,
    })
    rows.value = result.records
    total.value = result.total
    pages.value = result.pages
  } catch (error) {
    toast.error(messageOf(error))
  } finally {
    loading.value = false
  }
}

async function fetchRoles(): Promise<void> {
  try {
    roles.value = await roleApi.list()
  } catch {
    roles.value = []
  }
}

function handleSearch(): void {
  page.value = 1
  void fetchUsers()
}

function resetFilters(): void {
  keyword.value = ''
  statusFilter.value = 'all'
  roleFilter.value = 'all'
  page.value = 1
  void fetchUsers()
}

function goToPage(next: number): void {
  if (next < 1 || next > Math.max(1, pages.value) || next === page.value) return
  page.value = next
  void fetchUsers()
}

function resetForm(): void {
  form.username = ''
  form.nickname = ''
  form.email = ''
  form.password = ''
  form.phone = ''
  form.status = 1
  form.roleIds = []
  formError.value = ''
}

function openCreate(): void {
  editing.value = null
  resetForm()
  if (canAssignRoles.value) {
    const defaultRole = roles.value.find(role => role.code === 'USER')
    if (defaultRole) form.roleIds = [defaultRole.id]
  }
  dialogOpen.value = true
}

function openEdit(user: UserProfile): void {
  editing.value = user
  resetForm()
  form.username = user.username
  form.nickname = user.nickname ?? ''
  form.email = user.email
  form.phone = user.phone ?? ''
  form.status = user.status
  form.roleIds = user.roles.map(role => role.id)
  dialogOpen.value = true
}

function toggleRole(roleId: string, checked: boolean | 'indeterminate'): void {
  if (checked === true) {
    if (!form.roleIds.includes(roleId)) form.roleIds = [...form.roleIds, roleId]
  } else {
    form.roleIds = form.roleIds.filter(id => id !== roleId)
  }
}

function validateForm(): string | null {
  if (!isEdit.value && !/^[a-zA-Z][a-zA-Z0-9_]{2,31}$/.test(form.username.trim())) {
    return '用户名需以字母开头，只能包含字母、数字、下划线，长度 3-32'
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    return '邮箱格式不正确'
  }
  if (!isEdit.value && !/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(form.password)) {
    return '密码长度需为 8-32 位，且同时包含字母和数字'
  }
  return null
}

async function saveUser(): Promise<void> {
  const validation = validateForm()
  if (validation) {
    formError.value = validation
    return
  }
  formError.value = ''
  saving.value = true
  try {
    if (editing.value) {
      const target = editing.value
      await userApi.update(target.id, {
        nickname: form.nickname.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        status: form.status,
      })
      if (canAssignRoles.value) {
        const originalRoleIds = [...target.roles.map(role => role.id)].sort().join(',')
        const nextRoleIds = [...form.roleIds].sort().join(',')
        if (originalRoleIds !== nextRoleIds) {
          await userApi.assignRoles(target.id, form.roleIds)
        }
      }
      toast.success('用户信息已更新')
      if (editing.value.id === currentUserId.value) {
        await userStore.fetchProfile()
      }
    } else {
      await userApi.create({
        username: form.username.trim(),
        nickname: form.nickname.trim() || undefined,
        email: form.email.trim(),
        password: form.password,
        phone: form.phone.trim() || undefined,
        status: form.status,
        roleIds: form.roleIds.length ? form.roleIds : undefined,
      })
      toast.success('用户已创建')
    }
    dialogOpen.value = false
    await fetchUsers()
  } catch (error) {
    formError.value = messageOf(error)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(user: UserProfile, enabled: boolean): Promise<void> {
  const previous = user.status
  const next = enabled ? 1 : 0
  user.status = next
  try {
    await userApi.updateStatus(user.id, next)
    toast.success(enabled ? `已启用 ${user.username}` : `已禁用 ${user.username}`)
  } catch (error) {
    user.status = previous
    toast.error(messageOf(error))
  }
}

function askDelete(user: UserProfile): void {
  deleteTarget.value = user
  deleteOpen.value = true
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await userApi.remove(deleteTarget.value.id)
    toast.success('用户已删除')
    deleteOpen.value = false
    if (rows.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await fetchUsers()
  } catch (error) {
    toast.error(messageOf(error))
  } finally {
    deleting.value = false
  }
}

function askResetPassword(user: UserProfile): void {
  passwordTarget.value = user
  newPassword.value = ''
  passwordError.value = ''
  passwordOpen.value = true
}

async function confirmResetPassword(): Promise<void> {
  if (!passwordTarget.value) return
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(newPassword.value)) {
    passwordError.value = '密码长度需为 8-32 位，且同时包含字母和数字'
    return
  }
  resettingPassword.value = true
  try {
    await userApi.resetPassword(passwordTarget.value.id, newPassword.value)
    toast.success(`已重置 ${passwordTarget.value.username} 的密码`)
    passwordOpen.value = false
  } catch (error) {
    passwordError.value = messageOf(error)
  } finally {
    resettingPassword.value = false
  }
}

watch([statusFilter, roleFilter], () => {
  page.value = 1
  void fetchUsers()
})

onMounted(() => {
  void Promise.all([fetchRoles(), fetchUsers()])
})
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-col gap-1">
      <h1 class="flex items-center gap-2 text-xl font-semibold">
        <IconUsers class="size-5 text-muted-foreground" />
        用户管理
      </h1>
      <p class="text-sm text-muted-foreground">
        管理平台账户、状态与角色分配。角色决定用户可访问的 API 与页面。
      </p>
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <div class="flex w-full max-w-xs items-center gap-2">
        <Input
          v-model="keyword"
          placeholder="搜索用户名 / 昵称 / 邮箱"
          @keyup.enter="handleSearch"
        />
        <Button variant="outline" size="icon" aria-label="搜索" @click="handleSearch">
          <IconSearch />
        </Button>
      </div>
      <Select v-model="statusFilter">
        <SelectTrigger class="w-32">
          <SelectValue placeholder="状态" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">全部状态</SelectItem>
          <SelectItem value="1">启用</SelectItem>
          <SelectItem value="0">禁用</SelectItem>
        </SelectContent>
      </Select>
      <Select v-model="roleFilter">
        <SelectTrigger class="w-40">
          <SelectValue placeholder="角色" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">全部角色</SelectItem>
          <SelectItem v-for="role in roles" :key="role.id" :value="role.id">
            {{ role.name }}
          </SelectItem>
        </SelectContent>
      </Select>
      <Button variant="ghost" size="icon" aria-label="重置筛选" @click="resetFilters">
        <IconRefresh />
      </Button>
      <div class="flex-1"></div>
      <Button v-permission="'user:create'" @click="openCreate">
        <IconPlus class="mr-1" />
        新建用户
      </Button>
    </div>

    <div class="overflow-hidden rounded-xl border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="min-w-56">用户</TableHead>
            <TableHead>角色</TableHead>
            <TableHead class="w-28">状态</TableHead>
            <TableHead class="w-44">最后登录</TableHead>
            <TableHead class="w-36 text-right">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="loading">
            <TableCell colspan="5" class="h-24 text-center">
              <Spinner class="mx-auto" />
            </TableCell>
          </TableRow>
          <TableRow v-else-if="rows.length === 0">
            <TableCell colspan="5" class="h-24 text-center text-sm text-muted-foreground">
              暂无符合条件的用户
            </TableCell>
          </TableRow>
          <template v-else>
            <TableRow v-for="user in rows" :key="user.id">
              <TableCell>
                <div class="flex items-center gap-3">
                  <div class="flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold">
                    {{ initials(user) }}
                  </div>
                  <div class="flex min-w-0 flex-col">
                    <span class="truncate font-medium">{{ user.nickname || user.username }}</span>
                    <span class="truncate text-xs text-muted-foreground">
                      {{ user.username }} · {{ user.email }}
                    </span>
                  </div>
                </div>
              </TableCell>
              <TableCell>
                <div class="flex flex-wrap gap-1">
                  <Badge
                    v-for="role in user.roles"
                    :key="role.id"
                    :variant="role.code === 'ADMIN' ? 'default' : 'secondary'"
                  >
                    {{ role.name }}
                  </Badge>
                  <span v-if="user.roles.length === 0" class="text-xs text-muted-foreground">未分配</span>
                </div>
              </TableCell>
              <TableCell>
                <div class="flex items-center gap-2">
                  <Switch
                    :model-value="user.status === 1"
                    :disabled="user.id === currentUserId || !canUpdate"
                    @update:model-value="(value: boolean) => toggleStatus(user, value)"
                  />
                  <span class="text-xs text-muted-foreground">
                    {{ user.status === 1 ? '启用' : '禁用' }}
                  </span>
                </div>
              </TableCell>
              <TableCell class="text-xs text-muted-foreground">
                {{ user.lastLoginTime || '从未登录' }}
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
                  <Button
                    v-if="canUpdate"
                    variant="ghost"
                    size="icon"
                    aria-label="编辑"
                    @click="openEdit(user)"
                  >
                    <IconPencil />
                  </Button>
                  <Button
                    v-if="canResetPassword"
                    variant="ghost"
                    size="icon"
                    aria-label="重置密码"
                    @click="askResetPassword(user)"
                  >
                    <IconKey />
                  </Button>
                  <Button
                    v-if="canDelete"
                    variant="ghost"
                    size="icon"
                    class="text-destructive hover:text-destructive"
                    aria-label="删除"
                    :disabled="user.id === currentUserId"
                    @click="askDelete(user)"
                  >
                    <IconTrash />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </template>
        </TableBody>
      </Table>
    </div>

    <div class="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
      <span>共 {{ total }} 条，显示 {{ rangeStart }}-{{ rangeEnd }}</span>
      <div class="flex items-center gap-2">
        <Button variant="outline" size="sm" :disabled="page <= 1" @click="goToPage(page - 1)">
          <IconChevronLeft />
          上一页
        </Button>
        <span>{{ page }} / {{ Math.max(1, pages) }}</span>
        <Button variant="outline" size="sm" :disabled="page >= pages" @click="goToPage(page + 1)">
          下一页
          <IconChevronRight />
        </Button>
      </div>
    </div>

    <Dialog v-model:open="dialogOpen">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ isEdit ? '编辑用户' : '新建用户' }}</DialogTitle>
          <DialogDescription>
            {{ isEdit ? '修改账户资料、状态与角色。' : '创建一个新账户并分配初始角色。' }}
          </DialogDescription>
        </DialogHeader>

        <Alert v-if="formError" variant="destructive">
          <AlertDescription>{{ formError }}</AlertDescription>
        </Alert>

        <div class="grid gap-4">
          <div class="grid gap-2">
            <Label for="user-username">用户名</Label>
            <Input
              id="user-username"
              v-model="form.username"
              :disabled="isEdit"
              placeholder="gabriel"
            />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="grid gap-2">
              <Label for="user-nickname">昵称</Label>
              <Input id="user-nickname" v-model="form.nickname" placeholder="Gabriel" />
            </div>
            <div class="grid gap-2">
              <Label for="user-phone">手机号</Label>
              <Input id="user-phone" v-model="form.phone" placeholder="13800138000" />
            </div>
          </div>
          <div class="grid gap-2">
            <Label for="user-email">邮箱</Label>
            <Input id="user-email" v-model="form.email" type="email" placeholder="m@example.com" />
          </div>
          <div v-if="!isEdit" class="grid gap-2">
            <Label for="user-password">初始密码</Label>
            <Input
              id="user-password"
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              placeholder="至少 8 位，包含字母和数字"
            />
          </div>
          <div class="grid gap-2">
            <Label>状态</Label>
            <div class="flex items-center gap-2">
              <Switch
                :model-value="form.status === 1"
                @update:model-value="(value: boolean) => (form.status = value ? 1 : 0)"
              />
              <span class="text-sm text-muted-foreground">
                {{ form.status === 1 ? '启用' : '禁用' }}
              </span>
            </div>
          </div>
          <div v-if="canAssignRoles" class="grid gap-2">
            <Label>角色</Label>
            <div class="flex flex-wrap gap-4 rounded-md border p-3">
              <label
                v-for="role in roles"
                :key="role.id"
                class="flex cursor-pointer items-center gap-2 text-sm"
              >
                <Checkbox
                  :model-value="form.roleIds.includes(role.id)"
                  @update:model-value="(value: boolean | 'indeterminate') => toggleRole(role.id, value)"
                />
                <span>{{ role.name }}</span>
                <Badge variant="outline" class="font-mono text-xs">{{ role.code }}</Badge>
              </label>
              <span v-if="roles.length === 0" class="text-xs text-muted-foreground">
                暂无可用角色
              </span>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" :disabled="saving" @click="dialogOpen = false">取消</Button>
          <Button :disabled="saving" @click="saveUser">
            <Spinner v-if="saving" class="mr-2" />
            {{ isEdit ? '保存' : '创建' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="deleteOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>删除用户</DialogTitle>
          <DialogDescription>
            确认删除用户 <span class="font-medium text-foreground">{{ deleteTarget?.username }}</span> 吗？
            该用户会被强制退出登录，且此操作不可恢复。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" :disabled="deleting" @click="deleteOpen = false">取消</Button>
          <Button variant="destructive" :disabled="deleting" @click="confirmDelete">
            <Spinner v-if="deleting" class="mr-2" />
            确认删除
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="passwordOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>重置密码</DialogTitle>
          <DialogDescription>
            为 <span class="font-medium text-foreground">{{ passwordTarget?.username }}</span>
            设置新密码，保存后该用户的全部登录状态会被撤销。
          </DialogDescription>
        </DialogHeader>
        <div class="grid gap-2">
          <Label for="reset-password">新密码</Label>
          <Input
            id="reset-password"
            v-model="newPassword"
            type="password"
            autocomplete="new-password"
            placeholder="至少 8 位，包含字母和数字"
          />
          <p v-if="passwordError" class="text-sm text-destructive">{{ passwordError }}</p>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="resettingPassword" @click="passwordOpen = false">取消</Button>
          <Button :disabled="resettingPassword" @click="confirmResetPassword">
            <Spinner v-if="resettingPassword" class="mr-2" />
            重置密码
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
