<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { permissionApi, roleApi } from '@/api'
import type { PermissionItem, RoleItem, RolePayload } from '@/types/api'
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  IconPencil,
  IconPlus,
  IconRefresh,
  IconSearch,
  IconShield,
  IconTrash,
} from '@tabler/icons-vue'

const userStore = useUserStore()

const loading = ref(false)
const roles = ref<RoleItem[]>([])
const permissions = ref<PermissionItem[]>([])
const permissionError = ref('')
const keyword = ref('')

const canUpdate = computed(() => userStore.hasPermission('role:update'))
const canDelete = computed(() => userStore.hasPermission('role:delete'))
const canAssignPermission = computed(() => userStore.hasPermission('role:assign-permission'))

const filteredRoles = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  if (!search) return roles.value
  return roles.value.filter(role =>
    role.name.toLowerCase().includes(search)
    || role.code.toLowerCase().includes(search)
    || (role.description ?? '').toLowerCase().includes(search),
  )
})

const dialogOpen = ref(false)
const editing = ref<RoleItem | null>(null)
const saving = ref(false)
const formError = ref('')
const form = reactive({
  code: '',
  name: '',
  description: '',
  sort: '0',
  status: 1,
  permissionIds: [] as string[],
})

const deleteOpen = ref(false)
const deleteTarget = ref<RoleItem | null>(null)
const deleting = ref(false)

const isEdit = computed(() => Boolean(editing.value))
const isAdminRole = computed(() => editing.value?.code === 'ADMIN')
const totalPermissions = computed(() => permissions.value.length)

const permissionGroups = computed(() => {
  const labels: Record<string, string> = {
    console: '控制台',
    user: '用户管理',
    role: '角色管理',
    permission: '权限管理',
  }
  const groups = new Map<string, PermissionItem[]>()
  for (const permission of permissions.value) {
    const key = permission.code.split(':')[0] || 'other'
    const list = groups.get(key) ?? []
    list.push(permission)
    groups.set(key, list)
  }
  return Array.from(groups.entries()).map(([key, items]) => ({
    key,
    label: labels[key] ?? key,
    items,
  }))
})

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function fetchRoles(): Promise<void> {
  loading.value = true
  try {
    roles.value = await roleApi.list()
  } catch (error) {
    toast.error(messageOf(error))
  } finally {
    loading.value = false
  }
}

async function fetchPermissions(): Promise<void> {
  if (!canAssignPermission.value) return
  try {
    permissions.value = await permissionApi.list()
    permissionError.value = ''
  } catch (error) {
    permissions.value = []
    permissionError.value = messageOf(error)
  }
}

function resetForm(): void {
  form.code = ''
  form.name = ''
  form.description = ''
  form.sort = '0'
  form.status = 1
  form.permissionIds = []
  formError.value = ''
}

function openCreate(): void {
  editing.value = null
  resetForm()
  dialogOpen.value = true
}

function openEdit(role: RoleItem): void {
  editing.value = role
  resetForm()
  form.code = role.code
  form.name = role.name
  form.description = role.description ?? ''
  form.sort = String(role.sort)
  form.status = role.status
  form.permissionIds = [...role.permissionIds]
  dialogOpen.value = true
}

function togglePermission(permissionId: string, checked: boolean | 'indeterminate'): void {
  if (checked === true) {
    if (!form.permissionIds.includes(permissionId)) {
      form.permissionIds = [...form.permissionIds, permissionId]
    }
  } else {
    form.permissionIds = form.permissionIds.filter(id => id !== permissionId)
  }
}

function validate(): string | null {
  if (!/^[A-Z][A-Z0-9_]{1,31}$/.test(form.code.trim().toUpperCase())) {
    return '角色编码需以大写字母开头，只能包含大写字母、数字、下划线，长度 2-32'
  }
  if (!form.name.trim()) {
    return '角色名称不能为空'
  }
  if (form.sort !== '' && Number(form.sort) < 0) {
    return '排序值不能为负数'
  }
  return null
}

async function saveRole(): Promise<void> {
  const validation = validate()
  if (validation) {
    formError.value = validation
    return
  }
  const payload: RolePayload = {
    code: form.code.trim().toUpperCase(),
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    sort: Number(form.sort) || 0,
    status: form.status,
    permissionIds: canAssignPermission.value ? form.permissionIds : undefined,
  }
  saving.value = true
  formError.value = ''
  try {
    if (editing.value) {
      await roleApi.update(editing.value.id, payload)
      toast.success('角色已更新')
    } else {
      await roleApi.create(payload)
      toast.success('角色已创建')
    }
    dialogOpen.value = false
    await fetchRoles()
  } catch (error) {
    formError.value = messageOf(error)
  } finally {
    saving.value = false
  }
}

function askDelete(role: RoleItem): void {
  deleteTarget.value = role
  deleteOpen.value = true
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await roleApi.remove(deleteTarget.value.id)
    toast.success('角色已删除')
    deleteOpen.value = false
    await fetchRoles()
  } catch (error) {
    toast.error(messageOf(error))
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  void Promise.all([fetchRoles(), fetchPermissions()])
})
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-col gap-1">
      <h1 class="flex items-center gap-2 text-xl font-semibold">
        <IconShield class="size-5 text-muted-foreground" />
        角色管理
      </h1>
      <p class="text-sm text-muted-foreground">
        角色是权限的集合。用户通过角色获得 API 与页面访问权限。
      </p>
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <div class="flex w-full max-w-xs items-center gap-2">
        <Input v-model="keyword" placeholder="搜索角色名称 / 编码" />
        <Button variant="outline" size="icon" aria-label="搜索">
          <IconSearch />
        </Button>
      </div>
      <Button variant="ghost" size="icon" aria-label="刷新" @click="fetchRoles">
        <IconRefresh />
      </Button>
      <div class="flex-1"></div>
      <Badge variant="outline" class="h-8 px-3">共 {{ filteredRoles.length }} 个角色</Badge>
      <Button v-permission="'role:create'" @click="openCreate">
        <IconPlus class="mr-1" />
        新建角色
      </Button>
    </div>

    <Alert v-if="permissionError" variant="destructive">
      <AlertDescription>权限列表加载失败：{{ permissionError }}</AlertDescription>
    </Alert>

    <div class="overflow-hidden rounded-xl border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="min-w-56">角色</TableHead>
            <TableHead class="min-w-48">描述</TableHead>
            <TableHead class="w-24">权限数</TableHead>
            <TableHead class="w-24">用户数</TableHead>
            <TableHead class="w-24">状态</TableHead>
            <TableHead class="w-28 text-right">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="loading">
            <TableCell colspan="6" class="h-24 text-center">
              <Spinner class="mx-auto" />
            </TableCell>
          </TableRow>
          <TableRow v-else-if="filteredRoles.length === 0">
            <TableCell colspan="6" class="h-24 text-center text-sm text-muted-foreground">
              暂无符合条件的角色
            </TableCell>
          </TableRow>
          <template v-else>
            <TableRow v-for="role in filteredRoles" :key="role.id">
              <TableCell>
                <div class="flex flex-col gap-1">
                  <div class="flex items-center gap-2">
                    <span class="font-medium">{{ role.name }}</span>
                    <Badge v-if="role.builtin" variant="outline" class="text-xs">内置</Badge>
                  </div>
                  <span class="font-mono text-xs text-muted-foreground">{{ role.code }}</span>
                </div>
              </TableCell>
              <TableCell class="max-w-80 text-sm text-muted-foreground">
                {{ role.description || '—' }}
              </TableCell>
              <TableCell>
                <Badge variant="secondary">{{ role.permissionIds.length }}</Badge>
              </TableCell>
              <TableCell>{{ role.userCount }}</TableCell>
              <TableCell>
                <Badge :variant="role.status === 1 ? 'default' : 'secondary'">
                  {{ role.status === 1 ? '启用' : '禁用' }}
                </Badge>
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
                  <Button
                    v-if="canUpdate"
                    variant="ghost"
                    size="icon"
                    aria-label="编辑"
                    @click="openEdit(role)"
                  >
                    <IconPencil />
                  </Button>
                  <Button
                    v-if="canDelete"
                    variant="ghost"
                    size="icon"
                    class="text-destructive hover:text-destructive"
                    aria-label="删除"
                    :disabled="role.builtin"
                    @click="askDelete(role)"
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

    <Dialog v-model:open="dialogOpen">
      <DialogContent class="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{{ isEdit ? '编辑角色' : '新建角色' }}</DialogTitle>
          <DialogDescription>
            角色编码用于权限判断，创建后建议保持不变。
          </DialogDescription>
        </DialogHeader>

        <Alert v-if="formError" variant="destructive">
          <AlertDescription>{{ formError }}</AlertDescription>
        </Alert>

        <div class="grid gap-4">
          <div class="grid grid-cols-2 gap-4">
            <div class="grid gap-2">
              <Label for="role-code">角色编码</Label>
              <Input
                id="role-code"
                v-model="form.code"
                :disabled="isEdit && editing?.builtin"
                class="font-mono uppercase"
                placeholder="AUDITOR"
                @input="form.code = form.code.toUpperCase()"
              />
            </div>
            <div class="grid gap-2">
              <Label for="role-name">角色名称</Label>
              <Input id="role-name" v-model="form.name" placeholder="审计员" />
            </div>
          </div>
          <div class="grid gap-2">
            <Label for="role-description">描述</Label>
            <Input id="role-description" v-model="form.description" placeholder="该角色的职责说明" />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="grid gap-2">
              <Label for="role-sort">排序</Label>
              <Input
                id="role-sort"
                :model-value="form.sort"
                type="number"
                min="0"
                @update:model-value="(value: string | number) => (form.sort = String(value))"
              />
            </div>
            <div class="grid gap-2">
              <Label>状态</Label>
              <div class="flex h-9 items-center gap-2">
                <Switch
                  :model-value="form.status === 1"
                  @update:model-value="(value: boolean) => (form.status = value ? 1 : 0)"
                />
                <span class="text-sm text-muted-foreground">
                  {{ form.status === 1 ? '启用' : '禁用' }}
                </span>
              </div>
            </div>
          </div>

          <div v-if="canAssignPermission" class="grid gap-2">
            <div class="flex items-center justify-between">
              <Label>权限</Label>
              <span class="text-xs text-muted-foreground">
                已选 {{ form.permissionIds.length }} / {{ totalPermissions }}
              </span>
            </div>
            <p v-if="isAdminRole" class="text-xs text-amber-600">
              ADMIN 为内置超级角色，启动时会自动同步全部权限。
            </p>
            <div class="flex flex-col gap-3 rounded-md border p-3">
              <div v-for="group in permissionGroups" :key="group.key" class="flex flex-col gap-2">
                <span class="text-xs font-semibold text-muted-foreground">{{ group.label }}</span>
                <div class="grid gap-2 sm:grid-cols-2">
                  <label
                    v-for="permission in group.items"
                    :key="permission.id"
                    class="flex cursor-pointer items-start gap-2 text-sm"
                  >
                    <Checkbox
                      class="mt-0.5"
                      :model-value="form.permissionIds.includes(permission.id)"
                      :disabled="isAdminRole"
                      @update:model-value="(value: boolean | 'indeterminate') => togglePermission(permission.id, value)"
                    />
                    <span class="flex flex-col">
                      <span>{{ permission.name }}</span>
                      <span class="font-mono text-xs text-muted-foreground">{{ permission.code }}</span>
                    </span>
                  </label>
                </div>
              </div>
              <span v-if="permissionGroups.length === 0" class="text-xs text-muted-foreground">
                暂无可分配的权限
              </span>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" :disabled="saving" @click="dialogOpen = false">取消</Button>
          <Button :disabled="saving" @click="saveRole">
            <Spinner v-if="saving" class="mr-2" />
            {{ isEdit ? '保存' : '创建' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="deleteOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>删除角色</DialogTitle>
          <DialogDescription>
            确认删除角色 <span class="font-medium text-foreground">{{ deleteTarget?.name }}</span> 吗？
            角色下仍有用户时无法删除。
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
  </div>
</template>
