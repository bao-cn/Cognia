<template>
  <div class="mx-auto flex w-full max-w-6xl flex-col gap-5 p-4 pb-12 md:p-8">
    <Button variant="ghost" size="sm" class="w-fit rounded-full text-muted-foreground" @click="router.push('/teams')">
      <IconArrowLeft class="mr-1.5 size-4" />
      返回我的团队
    </Button>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-2xl border border-dashed">
      <Spinner class="size-6" />
    </div>
    <div v-else-if="error" class="flex flex-col items-center gap-3 rounded-2xl border border-dashed px-6 py-16 text-center">
      <IconUsers class="size-8 text-muted-foreground" />
      <p class="text-sm font-medium">{{ error }}</p>
      <Button size="sm" class="rounded-full" @click="router.push('/teams')">返回我的团队</Button>
    </div>

    <template v-else-if="team">
      <!-- Team header: no gradient, team ID, created time, owner or invite button. -->
      <section class="flex flex-col gap-4 rounded-2xl border bg-card p-5 md:flex-row md:items-start md:p-6">
        <img :src="team.logoUrl || '/logo.png'" :alt="team.name" class="size-20 rounded-2xl object-cover ring-1 ring-border md:size-24" />
        <div class="min-w-0 flex-1">
          <h1 class="text-2xl font-semibold tracking-tight md:text-3xl">{{ team.name }}</h1>
          <p class="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">{{ team.description || '这个团队还没有介绍。' }}</p>
          <div class="mt-4 flex flex-wrap items-center gap-4">
            <div class="flex items-center gap-2">
              <div class="flex -space-x-2">
                <Avatar v-for="member in team.members.slice(0, 5)" :key="member.userId" class="size-7 border-2 border-background">
                  <AvatarImage :src="member.avatar" :alt="member.nickname" class="object-cover" />
                  <AvatarFallback class="text-[9px]">{{ initials(member) }}</AvatarFallback>
                </Avatar>
              </div>
              <span class="text-xs text-muted-foreground">{{ team.memberCount }} 位成员</span>
            </div>
            <div v-if="team.links.length" class="flex flex-wrap items-center gap-2">
              <a
                v-for="link in team.links"
                :key="link.label + link.url"
                :href="link.url"
                target="_blank"
                rel="noreferrer"
                class="inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs text-muted-foreground transition hover:bg-muted hover:text-foreground"
              >
                <IconLink class="size-3" />
                {{ link.label }}
              </a>
            </div>
          </div>
        </div>
      </section>

      <div class="flex gap-1 overflow-x-auto border-b">
        <button
          v-for="tab in visibleTabs"
          :key="tab.value"
          type="button"
          class="relative shrink-0 px-4 py-2.5 text-sm transition"
          :class="activeTab === tab.value ? 'font-medium text-foreground' : 'text-muted-foreground hover:text-foreground'"
          @click="activeTab = tab.value"
        >
          <component :is="tab.icon" class="mr-1.5 inline size-4" />
          {{ tab.label }}
          <span v-if="activeTab === tab.value" class="absolute inset-x-3 -bottom-px h-0.5 rounded-full bg-primary" />
        </button>
      </div>

      <!-- 介绍 -->
      <div v-if="activeTab === 'overview'" class="flex flex-col gap-5">
        <section v-if="team.readme" class="rounded-2xl border bg-card p-5 md:p-6">
          <div class="flex items-center gap-2 border-b pb-3">
            <IconBook class="size-4 text-muted-foreground" />
            <h2 class="text-sm font-semibold">团队 README</h2>
          </div>
          <div class="mt-4 text-sm leading-7"><Markdown :text="team.readme" /></div>
        </section>

        <section v-if="pinnedChannels.length" class="rounded-2xl border bg-card p-5">
          <div class="flex items-center justify-between">
            <h2 class="flex items-center gap-2 text-sm font-semibold"><IconPin class="size-4 text-muted-foreground" />置顶频道</h2>
            <Badge variant="outline" class="rounded-full text-[10px]">{{ pinnedChannels.length }} / 6</Badge>
          </div>
          <div class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <button v-for="channel in pinnedChannels" :key="channel.id" type="button" class="flex items-center gap-3 rounded-xl border bg-background p-3 text-left transition hover:bg-muted/50" @click="openChannel(channel)">
              <div class="flex size-9 items-center justify-center rounded-xl bg-muted"><IconHash class="size-4 text-muted-foreground" /></div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium">{{ channel.name }}</p>
                <p class="mt-0.5 truncate text-xs text-muted-foreground">{{ channel.description || '暂无介绍' }}</p>
              </div>
            </button>
          </div>
        </section>

        <section v-if="popularChannels.length" class="rounded-2xl border bg-card p-5">
          <div class="flex items-center justify-between">
            <h2 class="flex items-center gap-2 text-sm font-semibold"><IconFlame class="size-4 text-muted-foreground" />热门公开频道</h2>
            <Badge variant="outline" class="rounded-full text-[10px]">{{ popularChannels.length }} / 10</Badge>
          </div>
          <div class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <button v-for="channel in popularChannels" :key="channel.id" type="button" class="flex items-center gap-3 rounded-xl border bg-background p-3 text-left transition hover:bg-muted/50" @click="openChannel(channel)">
              <div class="flex size-9 items-center justify-center rounded-xl bg-muted"><IconHash class="size-4 text-muted-foreground" /></div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium">{{ channel.name }}</p>
                <p class="mt-0.5 truncate text-xs text-muted-foreground">{{ channel.description || '暂无介绍' }}</p>
              </div>
            </button>
          </div>
        </section>
      </div>

      <!-- 频道 -->
      <div v-else-if="activeTab === 'channels'" class="flex flex-col gap-4">
        <div class="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
          <div>
            <h2 class="text-lg font-semibold">团队频道</h2>
            <p class="mt-1 text-xs text-muted-foreground">团队成员可以看到全部频道；非成员只能看到公开频道。</p>
          </div>
          <Button v-if="team.canCreateChannel" class="rounded-full" @click="createChannel">
            <IconHash class="mr-1.5 size-4" />
            新建频道
          </Button>
        </div>
        <div v-if="!kbChannels.length" class="flex flex-col items-center gap-2 rounded-2xl border border-dashed px-4 py-14 text-center">
          <IconHash class="size-8 text-muted-foreground" />
          <p class="text-sm font-medium">还没有频道</p>
          <p class="text-xs text-muted-foreground">频道由团队所有者或编辑创建，创建后团队成员可以上传文档。</p>
        </div>
        <div v-else class="grid gap-3 md:grid-cols-2">
          <article v-for="channel in kbChannels" :key="channel.id"
            class="flex cursor-pointer flex-col gap-3 rounded-2xl border bg-card p-4 transition hover:border-primary/40 hover:shadow-sm"
            @click="openChannel(channel)">
            <div class="flex items-start gap-3">
              <div class="flex size-9 shrink-0 items-center justify-center rounded-xl bg-muted"><IconHash class="size-4 text-muted-foreground" /></div>
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <h3 class="truncate text-sm font-semibold">{{ channel.name }}</h3>
                  <Badge variant="outline" class="rounded-full text-[10px]">{{ channel.isPublic ? '公开' : '私有' }}</Badge>
                  <Badge v-if="channel.pinned" variant="secondary" class="rounded-full text-[10px]">置顶 {{ channel.pinnedOrder }}</Badge>
                </div>
                <p class="mt-1 line-clamp-2 text-xs leading-5 text-muted-foreground">{{ channel.description || '暂无介绍' }}</p>
                <p class="mt-2 text-[10px] text-muted-foreground">{{ channel.documentCount }} 个文档</p>
              </div>
            </div>
          </article>
        </div>
      </div>

      <!-- 成员（只读） -->
      <div v-else-if="activeTab === 'people'" class="flex flex-col gap-4">
        <div>
          <h2 class="text-lg font-semibold">团队成员</h2>
          <p class="mt-1 text-xs text-muted-foreground">这里展示团队的全部成员。成员权限管理在设置中进行。</p>
        </div>
        <div class="overflow-hidden rounded-2xl border bg-card">
          <div v-for="member in team.members" :key="member.userId" class="flex items-center gap-3 border-b p-4 last:border-b-0">
            <Avatar class="size-10 rounded-xl">
              <AvatarImage :src="member.avatar" :alt="member.nickname" class="object-cover" />
              <AvatarFallback class="rounded-xl">{{ initials(member) }}</AvatarFallback>
            </Avatar>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium">{{ member.nickname }}</p>
              <p class="truncate text-xs text-muted-foreground">@{{ member.username }}</p>
            </div>
            <Badge variant="secondary" class="rounded-full">{{ roleLabel(member.role) }}</Badge>
          </div>
        </div>
      </div>

      <!-- 设置（仅创建者） -->
      <div v-else-if="activeTab === 'settings' && team.isOwner" class="grid gap-5 lg:grid-cols-[180px_minmax(0,1fr)]">
        <aside class="flex h-fit flex-col gap-1 rounded-2xl border bg-card p-2">
          <button type="button" class="rounded-lg px-3 py-2 text-left text-sm transition" :class="settingsSection === 'basic' ? 'bg-muted font-medium' : 'text-muted-foreground hover:bg-muted/60'" @click="settingsSection = 'basic'">基础设置</button>
          <button type="button" class="rounded-lg px-3 py-2 text-left text-sm transition" :class="settingsSection === 'members' ? 'bg-muted font-medium' : 'text-muted-foreground hover:bg-muted/60'" @click="settingsSection = 'members'">成员管理</button>
        </aside>

        <div v-if="settingsSection === 'basic'" class="flex flex-col gap-4">
          <section class="rounded-2xl border bg-card p-5">
            <h2 class="text-sm font-semibold">基础设置</h2>
            <div class="mt-4 grid gap-4">
              <div class="grid gap-2">
                <Label>团队名称</Label>
                <Input v-model="basicForm.name" maxlength="64" />
              </div>
              <div class="grid gap-2">
                <Label>团队介绍</Label>
                <Textarea v-model="basicForm.description" :rows="3" maxlength="512" />
              </div>
              <div class="grid gap-2">
                <Label>团队 Logo</Label>
                <div class="flex items-center gap-3">
                  <img :src="basicForm.logoUrl || '/logo.png'" alt="团队 Logo" class="size-14 rounded-xl object-cover ring-1 ring-border" />
                  <label class="inline-flex h-9 cursor-pointer items-center rounded-full border px-3 text-xs transition hover:bg-muted">
                    <IconUpload class="mr-1.5 size-3.5" />
                    上传并压缩为 WebP
                    <input type="file" accept="image/*" class="hidden" @change="handleLogoUpload" />
                  </label>
                </div>
                <p class="text-xs text-muted-foreground">图片会在浏览器中压缩为 WebP，最长边 256px。</p>
              </div>
              <div class="grid gap-2">
                <Label>自定义链接（最多 4 个）</Label>
                <div v-for="(link, index) in basicForm.links" :key="index" class="grid gap-2 sm:grid-cols-[120px_minmax(0,1fr)]">
                  <Input v-model="link.label" maxlength="24" placeholder="名称" />
                  <Input v-model="link.url" maxlength="512" placeholder="https://..." />
                </div>
              </div>
              <div class="flex items-center justify-between rounded-xl border p-3">
                <div>
                  <p class="text-sm font-medium">开启加入团队申请</p>
                  <p class="mt-0.5 text-xs text-muted-foreground">保存该开关；申请审核流程将在后续版本接入。</p>
                </div>
                <input v-model="basicForm.joinRequestsEnabled" type="checkbox" class="size-4 rounded border-border" />
              </div>
            </div>
            <div class="mt-4 flex justify-end">
              <Button class="rounded-full" :disabled="basicSaving" @click="saveBasic">
                <Spinner v-if="basicSaving" class="mr-2" />
                保存基础设置
              </Button>
            </div>
            <p v-if="basicError" class="mt-2 text-xs text-destructive">{{ basicError }}</p>
          </section>

          <section class="rounded-2xl border bg-card p-5">
            <h2 class="text-sm font-semibold">Readme 配置</h2>
            <Textarea v-model="readmeDraft" :rows="10" maxlength="20000" class="mt-3 font-mono text-sm leading-6" placeholder="# 团队介绍&#10;&#10;写下团队目标、协作方式和知识规范……" />
            <div class="mt-3 flex items-center justify-between">
              <p class="text-xs text-muted-foreground">{{ readmeDraft.length }} / 20000</p>
              <Button class="rounded-full" :disabled="readmeSaving" @click="saveReadme">
                <Spinner v-if="readmeSaving" class="mr-2" />
                保存 Readme
              </Button>
            </div>
            <div v-if="readmeDraft.trim()" class="mt-4 rounded-xl border bg-muted/20 p-4 text-sm"><Markdown :text="readmeDraft" /></div>
          </section>

          <section class="rounded-2xl border border-destructive/30 bg-card p-5">
            <h2 class="text-sm font-semibold text-destructive">危险操作</h2>
            <div class="mt-4 flex flex-col gap-3">
              <div class="flex flex-wrap items-center justify-between gap-2 rounded-xl border p-3">
                <div><p class="text-sm font-medium">转让管理权</p><p class="text-xs text-muted-foreground">将团队创建者转让给另一位成员。</p></div>
                <div class="flex items-center gap-2">
                  <select v-model="transferTarget" class="h-9 rounded-lg border bg-background px-2 text-xs">
                    <option value="">选择成员</option>
                    <option v-for="member in transferCandidates" :key="member.userId" :value="member.userId">{{ member.nickname }}</option>
                  </select>
                  <Button variant="outline" size="sm" class="rounded-full" :disabled="!transferTarget" @click="transferOpen = true">转让</Button>
                </div>
              </div>
              <div class="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-destructive/30 p-3">
                <div><p class="text-sm font-medium text-destructive">删除团队</p><p class="text-xs text-muted-foreground">删除团队、成员、频道与邀请，无法恢复。</p></div>
                <Button variant="destructive" size="sm" class="rounded-full" @click="deleteOpen = true">删除团队</Button>
              </div>
            </div>
          </section>
        </div>

        <div v-else-if="settingsSection === 'members'" class="flex flex-col gap-4">
          <section class="rounded-2xl border bg-card p-5">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div><h2 class="text-sm font-semibold">成员管理</h2><p class="mt-1 text-xs text-muted-foreground">设置成员权限、移除成员或邀请新成员。</p></div>
              <Button variant="outline" size="sm" class="rounded-full" @click="openInviteDialog"><IconUserPlus class="mr-1.5 size-4" />邀请成员</Button>
            </div>
            <div class="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border p-3">
              <div><p class="text-sm font-medium">新成员默认权限</p><p class="text-xs text-muted-foreground">接受邀请后自动获得此角色。</p></div>
              <div class="flex items-center gap-2">
                <select v-model="defaultRole" class="h-9 rounded-lg border bg-background px-2 text-xs">
                  <option value="MANAGER">管理者</option><option value="EDITOR">编辑</option><option value="MEMBER">成员</option>
                </select>
                <Button variant="outline" size="sm" class="rounded-full" :disabled="defaultRoleSaving" @click="saveDefaultRole">保存</Button>
              </div>
            </div>
            <div class="mt-4 overflow-hidden rounded-xl border">
              <div v-for="member in team.members" :key="member.userId" class="flex flex-wrap items-center gap-3 border-b p-3 last:border-b-0">
                <Avatar class="size-9 rounded-xl"><AvatarImage :src="member.avatar" :alt="member.nickname" class="object-cover" /><AvatarFallback class="rounded-xl">{{ initials(member) }}</AvatarFallback></Avatar>
                <div class="min-w-0 flex-1"><p class="truncate text-sm font-medium">{{ member.nickname }}</p><p class="truncate text-xs text-muted-foreground">@{{ member.username }}</p></div>
                <template v-if="member.role !== 'OWNER'">
                  <select class="h-8 rounded-lg border bg-background px-2 text-xs" :value="member.role" :disabled="roleSaving === member.userId" @change="changeRole(member, ($event.target as HTMLSelectElement).value)">
                    <option value="MANAGER">管理者</option><option value="EDITOR">编辑</option><option value="MEMBER">成员</option>
                  </select>
                  <Button variant="ghost" size="sm" class="rounded-full text-xs text-destructive hover:text-destructive" @click="removeTarget = member">移除</Button>
                </template>
                <Badge v-else variant="secondary" class="rounded-full">创建者</Badge>
              </div>
            </div>
            <div class="mt-3 grid gap-2 text-xs md:grid-cols-3">
              <div class="rounded-lg border p-3"><p class="font-medium">管理者</p><p class="mt-1 text-muted-foreground">创建、修改、删除频道</p></div>
              <div class="rounded-lg border p-3"><p class="font-medium">编辑</p><p class="mt-1 text-muted-foreground">创建、修改频道；删除仅限自己创建的频道</p></div>
              <div class="rounded-lg border p-3"><p class="font-medium">成员</p><p class="mt-1 text-muted-foreground">查看团队与频道</p></div>
            </div>
          </section>
        </div>
      </div>

      <Dialog v-model:open="inviteOpen">
        <DialogContent class="sm:max-w-lg">
          <DialogHeader><DialogTitle>邀请成员</DialogTitle><DialogDescription>搜索用户并发送邀请，对方可以在「我的团队」中接受或拒绝。</DialogDescription></DialogHeader>
          <div class="flex items-center gap-2">
            <Input v-model="inviteKeyword" class="h-9" placeholder="搜索用户名或昵称" @keyup.enter="searchUsers" />
            <Button variant="outline" size="icon" class="size-9 shrink-0" :disabled="inviteSearching" @click="searchUsers"><Spinner v-if="inviteSearching" class="size-4" /><IconSearch v-else class="size-4" /></Button>
          </div>
          <div class="max-h-80 overflow-y-auto rounded-xl border">
            <div v-if="inviteSearching" class="flex h-32 items-center justify-center"><Spinner class="size-5" /></div>
            <div v-else-if="!inviteResults.length" class="flex h-32 flex-col items-center justify-center gap-1 text-center"><p class="text-sm font-medium">没有找到可邀请的用户</p><p class="text-xs text-muted-foreground">试试输入完整用户名或昵称。</p></div>
            <div v-else class="flex flex-col divide-y">
              <div v-for="user in inviteResults" :key="user.id" class="flex items-center gap-3 p-3">
                <Avatar class="size-9 rounded-xl"><AvatarImage :src="user.avatar" :alt="user.nickname" class="object-cover" /><AvatarFallback class="rounded-xl">{{ user.nickname.slice(0, 2).toUpperCase() }}</AvatarFallback></Avatar>
                <div class="min-w-0 flex-1"><p class="truncate text-sm font-medium">{{ user.nickname }}</p><p class="truncate text-xs text-muted-foreground">@{{ user.username }}</p></div>
                <Badge v-if="user.member" variant="secondary" class="rounded-full text-[10px]">已加入</Badge>
                <Badge v-else-if="user.invited" variant="outline" class="rounded-full text-[10px]">已邀请</Badge>
                <Button v-else size="sm" class="rounded-full" :disabled="invitingId === user.id" @click="sendInvite(user)"><Spinner v-if="invitingId === user.id" class="mr-1.5 size-3.5" />邀请</Button>
              </div>
            </div>
          </div>
          <p v-if="inviteError" class="rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ inviteError }}</p>
          <DialogFooter><Button variant="outline" class="rounded-full" @click="inviteOpen = false">关闭</Button></DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog v-model:open="transferOpen">
        <DialogContent class="sm:max-w-sm">
          <DialogHeader><DialogTitle>转让管理权</DialogTitle><DialogDescription>确认将该团队转让给 {{ transferCandidates.find(m => m.userId === transferTarget)?.nickname || '所选成员' }}？转让后你将成为管理者。</DialogDescription></DialogHeader>
          <DialogFooter><Button variant="outline" class="rounded-full" @click="transferOpen = false">取消</Button><Button class="rounded-full" :disabled="transferring" @click="confirmTransfer"><Spinner v-if="transferring" class="mr-2" />确认转让</Button></DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog v-model:open="deleteOpen">
        <DialogContent class="sm:max-w-sm">
          <DialogHeader><DialogTitle>删除团队</DialogTitle><DialogDescription>确定删除「{{ team.name }}」？团队、成员、频道和邀请都会被删除，无法恢复。</DialogDescription></DialogHeader>
          <DialogFooter><Button variant="outline" class="rounded-full" @click="deleteOpen = false">取消</Button><Button variant="destructive" class="rounded-full" :disabled="deletingTeam" @click="confirmDelete"><Spinner v-if="deletingTeam" class="mr-2" />删除团队</Button></DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog :open="Boolean(removeTarget)" @update:open="value => { if (!value) removeTarget = null }">
        <DialogContent class="sm:max-w-sm">
          <DialogHeader><DialogTitle>移除成员</DialogTitle><DialogDescription>确定将 {{ removeTarget?.nickname }} 移出团队吗？</DialogDescription></DialogHeader>
          <DialogFooter><Button variant="outline" class="rounded-full" @click="removeTarget = null">取消</Button><Button variant="destructive" class="rounded-full" :disabled="removing" @click="confirmRemove"><Spinner v-if="removing" class="mr-2" />移除成员</Button></DialogFooter>
        </DialogContent>
      </Dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import {
  IconArrowLeft, IconBook, IconFlame, IconHash, IconLink, IconPin,
  IconSearch, IconSettings, IconUpload, IconUserPlus, IconUsers,
} from '@tabler/icons-vue'
import { channelApi, teamApi } from '@/api'
import type { ChannelCard, TeamDetail, TeamLink, TeamMember, TeamUserSearch } from '@/types/api'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import Markdown from '@/components/Markdown.vue'

const route = useRoute()
const router = useRouter()
const teamId = String(route.params.id || '')
const team = ref<TeamDetail | null>(null)
const kbChannels = ref<ChannelCard[]>([])
const loading = ref(true)
const error = ref('')
const activeTab = ref('overview')
const settingsSection = ref<'basic' | 'members'>('basic')

const inviteOpen = ref(false)
const inviteKeyword = ref('')
const inviteResults = ref<TeamUserSearch[]>([])
const inviteSearching = ref(false)
const invitingId = ref<string | null>(null)
const inviteError = ref('')

const basicForm = reactive({
  name: '',
  description: '',
  logoUrl: '',
  links: Array.from({ length: 4 }, () => ({ label: '', url: '' })) as TeamLink[],
  joinRequestsEnabled: false,
})
const basicSaving = ref(false)
const basicError = ref('')
const readmeDraft = ref('')
const readmeSaving = ref(false)
const defaultRole = ref('MEMBER')
const defaultRoleSaving = ref(false)
const roleSaving = ref<string | null>(null)
const removeTarget = ref<TeamMember | null>(null)
const removing = ref(false)
const transferTarget = ref('')
const transferOpen = ref(false)
const transferring = ref(false)
const deleteOpen = ref(false)
const deletingTeam = ref(false)

const visibleTabs = computed(() => {
  const tabs = [
    { value: 'overview', label: '介绍', icon: IconBook },
    { value: 'channels', label: '频道', icon: IconHash },
    { value: 'people', label: '成员', icon: IconUsers },
  ]
  if (team.value?.isOwner) tabs.push({ value: 'settings', label: '设置', icon: IconSettings })
  return tabs
})
const pinnedChannels = computed(() => kbChannels.value.filter(channel => channel.pinned).slice(0, 6))
const popularChannels = computed(() => kbChannels.value
  .filter(channel => channel.isPublic && !channel.pinned)
  .slice(0, 10))

function openChannel(channel: ChannelCard): void {
  void router.push(`/${team.value?.slug}/${channel.slug}`)
}

function createChannel(): void {
  void router.push(`/${team.value?.slug}`)
}
const transferCandidates = computed(() => (team.value?.members ?? []).filter(member => member.role !== 'OWNER'))

function roleLabel(role?: string): string {
  switch (role) {
    case 'OWNER': return '创建者'
    case 'MANAGER': return '管理者'
    case 'EDITOR': return '编辑'
    default: return '成员'
  }
}

function initials(member: TeamMember): string {
  return (member.nickname || member.username || '?').slice(0, 2).toUpperCase()
}

function applyTeam(value: TeamDetail): void {
  team.value = value
  basicForm.name = value.name
  basicForm.description = value.description || ''
  basicForm.logoUrl = value.logoUrl || ''
  basicForm.joinRequestsEnabled = value.joinRequestsEnabled
  basicForm.links = Array.from({ length: 4 }, (_, index) => ({
    label: value.links[index]?.label || '',
    url: value.links[index]?.url || '',
  }))
  readmeDraft.value = value.readme || ''
  defaultRole.value = value.defaultMemberRole || 'MEMBER'
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const detail = await teamApi.detail(teamId)
    applyTeam(detail)
    try {
      kbChannels.value = await channelApi.list('TEAM', detail.id)
    } catch {
      kbChannels.value = []
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '团队加载失败'
  } finally {
    loading.value = false
  }
}

async function handleLogoUpload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    toast.error('图片不能超过 5MB')
    return
  }
  try {
    const dataUrl = await compressToWebp(file)
    basicForm.logoUrl = dataUrl
    toast.success('图片已压缩为 WebP，保存后生效')
  } catch {
    toast.error('图片处理失败')
  } finally {
    input.value = ''
  }
}

function compressToWebp(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const image = new Image()
      image.onload = () => {
        const max = 256
        const scale = Math.min(1, max / Math.max(image.width, image.height))
        const canvas = document.createElement('canvas')
        canvas.width = Math.max(1, Math.round(image.width * scale))
        canvas.height = Math.max(1, Math.round(image.height * scale))
        const context = canvas.getContext('2d')
        if (!context) {
          reject(new Error('no canvas'))
          return
        }
        context.drawImage(image, 0, 0, canvas.width, canvas.height)
        resolve(canvas.toDataURL('image/webp', 0.82))
      }
      image.onerror = reject
      image.src = String(reader.result)
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

function payload() {
  return {
    name: basicForm.name.trim(),
    description: basicForm.description.trim() || undefined,
    logoUrl: basicForm.logoUrl || undefined,
    links: basicForm.links.filter(link => link.label.trim() && link.url.trim()).map(link => ({
      label: link.label.trim(),
      url: link.url.trim(),
    })),
    joinRequestsEnabled: basicForm.joinRequestsEnabled,
    defaultMemberRole: defaultRole.value,
  }
}

async function saveBasic(): Promise<void> {
  if (!basicForm.name.trim()) {
    basicError.value = '团队名称不能为空'
    return
  }
  basicSaving.value = true
  basicError.value = ''
  try {
    applyTeam(await teamApi.updateTeam(teamId, payload()))
    toast.success('团队设置已保存')
  } catch (err) {
    basicError.value = err instanceof Error ? err.message : '保存失败'
  } finally {
    basicSaving.value = false
  }
}

async function saveReadme(): Promise<void> {
  readmeSaving.value = true
  try {
    applyTeam(await teamApi.updateReadme(teamId, readmeDraft.value))
    toast.success('Readme 已保存')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Readme 保存失败')
  } finally {
    readmeSaving.value = false
  }
}

async function saveDefaultRole(): Promise<void> {
  defaultRoleSaving.value = true
  try {
    applyTeam(await teamApi.updateTeam(teamId, payload()))
    toast.success('默认成员权限已保存')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    defaultRoleSaving.value = false
  }
}

function openInviteDialog(): void {
  inviteOpen.value = true
  inviteKeyword.value = ''
  inviteResults.value = []
  inviteError.value = ''
  void searchUsers()
}

async function searchUsers(): Promise<void> {
  inviteSearching.value = true
  inviteError.value = ''
  try {
    inviteResults.value = await teamApi.searchInvitees(teamId, inviteKeyword.value.trim() || undefined)
  } catch (err) {
    inviteError.value = err instanceof Error ? err.message : '用户搜索失败'
  } finally {
    inviteSearching.value = false
  }
}

async function sendInvite(user: TeamUserSearch): Promise<void> {
  invitingId.value = user.id
  inviteError.value = ''
  try {
    await teamApi.invite(teamId, user.id)
    user.invited = true
    toast.success(`已向 ${user.nickname} 发送邀请`)
  } catch (err) {
    inviteError.value = err instanceof Error ? err.message : '发送邀请失败'
  } finally {
    invitingId.value = null
  }
}

async function changeRole(member: TeamMember, role: string): Promise<void> {
  roleSaving.value = member.userId
  try {
    applyTeam(await teamApi.updateMemberRole(teamId, member.userId, role))
    toast.success('成员权限已更新')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '成员权限更新失败')
  } finally {
    roleSaving.value = null
  }
}

async function confirmRemove(): Promise<void> {
  if (!removeTarget.value) return
  removing.value = true
  try {
    await teamApi.removeMember(teamId, removeTarget.value.userId)
    toast.success('成员已移除')
    removeTarget.value = null
    const detail = await teamApi.detail(teamId)
    applyTeam(detail)
    try {
      kbChannels.value = await channelApi.list('TEAM', detail.id)
    } catch {
      kbChannels.value = []
    }
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '移除成员失败')
  } finally {
    removing.value = false
  }
}

async function confirmTransfer(): Promise<void> {
  if (!transferTarget.value) return
  transferring.value = true
  try {
    applyTeam(await teamApi.transferOwnership(teamId, transferTarget.value))
    transferOpen.value = false
    transferTarget.value = ''
    activeTab.value = 'overview'
    toast.success('团队管理权已转让')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '转让失败')
  } finally {
    transferring.value = false
  }
}

async function confirmDelete(): Promise<void> {
  deletingTeam.value = true
  try {
    await teamApi.deleteTeam(teamId)
    toast.success('团队已删除')
    await router.replace('/teams')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '删除团队失败')
  } finally {
    deletingTeam.value = false
  }
}

onMounted(load)
</script>
