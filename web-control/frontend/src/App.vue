<template>
  <el-config-provider :locale="zhCn">
    <div class="app-shell">
      <aside class="side-nav">
        <div class="brand">
          <div class="brand-mark">M</div>
          <div class="brand-copy">
            <strong>MiruPlay</strong>
            <span>{{ serverInfo?.deviceName || 'Web Control' }}</span>
          </div>
        </div>

        <el-menu
          class="nav-menu"
          :default-active="activeView"
          @select="activeView = $event"
        >
          <el-menu-item index="library">
            <el-icon><Film /></el-icon>
            <span>片库</span>
          </el-menu-item>
          <el-menu-item index="sources">
            <el-icon><FolderOpened /></el-icon>
            <span>媒体源</span>
          </el-menu-item>
          <el-menu-item index="remote">
            <el-icon><SwitchButton /></el-icon>
            <span>遥控器</span>
          </el-menu-item>
        </el-menu>

        <el-card class="access-card" shadow="never">
          <span>访问地址</span>
          <el-tooltip :content="accessUrl" placement="top">
            <strong>{{ accessUrl }}</strong>
          </el-tooltip>
        </el-card>
      </aside>

      <section class="main-pane">
        <header class="page-header">
          <div>
            <h1>{{ viewMeta.title }}</h1>
            <p>{{ viewMeta.subtitle }}</p>
          </div>
          <div class="header-actions">
            <el-input
              v-if="activeView === 'library'"
              v-model="query"
              clearable
              placeholder="搜索番剧"
              :prefix-icon="Search"
              @input="debouncedLoadLibrary"
            />
            <el-button :icon="Refresh" circle @click="refreshCurrent" />
          </div>
        </header>

        <main>
          <section v-show="activeView === 'library'" class="view-stack">
            <el-skeleton v-if="loading.library" animated :rows="8" />
            <template v-else>
              <section v-if="continueWatching.length" class="content-section">
                <div class="section-title">
                  <h2>继续观看</h2>
                </div>
                <div class="continue-grid">
                  <el-card
                    v-for="item in continueWatching"
                    :key="item.progressEpisodeId"
                    class="continue-card"
                    shadow="hover"
                    @click="playEpisode(item.episode.id, item.positionMs)"
                  >
                    <strong>{{ titleOf(item.anime) }}</strong>
                    <span v-if="originalTitleOf(item.anime)" class="muted one-line">
                      {{ originalTitleOf(item.anime) }}
                    </span>
                    <span class="muted one-line">
                      {{ episodeLabel(item.episode) }} · {{ formatTime(item.positionMs) }}
                    </span>
                    <el-progress
                      :percentage="progressPercent(item)"
                      :show-text="false"
                      :stroke-width="5"
                    />
                  </el-card>
                </div>
              </section>

              <section class="content-section">
                <div class="section-title">
                  <h2>所有番剧</h2>
                  <span>{{ library.allAnime.length }} 部</span>
                </div>
                <el-empty
                  v-if="!library.allAnime.length"
                  description="还没有扫描到番剧，先去媒体源添加并扫描。"
                />
                <div v-else class="poster-grid">
                  <el-card
                    v-for="anime in library.allAnime"
                    :key="anime.id"
                    class="poster-card"
                    shadow="hover"
                    @click="openAnime(anime.id)"
                  >
                    <div class="poster-art">
                      <img v-if="anime.posterUrl" :src="anime.posterUrl" alt="" />
                      <span v-else>{{ firstChar(titleOf(anime)) }}</span>
                    </div>
                    <div class="poster-copy">
                      <strong>{{ titleOf(anime) }}</strong>
                      <span v-if="originalTitleOf(anime)" class="muted">
                        {{ originalTitleOf(anime) }}
                      </span>
                      <small>{{ anime.episodeCount || 0 }} 集</small>
                    </div>
                  </el-card>
                </div>
              </section>
            </template>
          </section>

          <section v-show="activeView === 'sources'" class="source-layout">
            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>媒体源</strong>
                  <el-button size="small" :loading="loading.scan" @click="scanAll">
                    扫描全部
                  </el-button>
                </div>
              </template>

              <el-skeleton v-if="loading.sources" animated :rows="5" />
              <el-empty v-else-if="!sources.length" description="还没有媒体源" />
              <div v-else class="source-list">
                <el-card
                  v-for="source in sources"
                  :key="source.id"
                  class="source-item"
                  shadow="never"
                >
                  <div class="source-main">
                    <el-tag :type="source.isConnected ? 'success' : 'warning'" effect="dark">
                      {{ source.type }}
                    </el-tag>
                    <div>
                      <strong>{{ source.name }}</strong>
                      <span class="muted break-text">{{ sourceLocation(source) }}</span>
                    </div>
                  </div>
                  <div class="source-actions">
                    <el-button size="small" @click="editSource(source)">编辑</el-button>
                    <el-button size="small" :loading="loading.scan" @click="scanSource(source.id)">
                      扫描
                    </el-button>
                    <el-button size="small" type="danger" plain @click="deleteSource(source.id)">
                      删除
                    </el-button>
                  </div>
                </el-card>
              </div>
            </el-card>

            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>{{ sourceForm.id ? '编辑源' : '添加源' }}</strong>
                  <el-button size="small" text @click="resetSourceForm">新建</el-button>
                </div>
              </template>

              <el-form label-position="top" class="source-form" @submit.prevent>
                <el-form-item label="类型">
                  <el-select v-model="sourceForm.type" @change="onSourceTypeChange">
                    <el-option label="本地" value="LOCAL" />
                    <el-option label="WebDAV" value="WEBDAV" />
                    <el-option label="SMB" value="SMB" />
                  </el-select>
                </el-form-item>
                <el-form-item label="显示名称">
                  <el-input v-model="sourceForm.name" placeholder="例如：NAS 动画库" />
                </el-form-item>
                <el-form-item :label="locationLabel">
                  <el-input v-model="sourceForm.location" :placeholder="locationPlaceholder" />
                </el-form-item>
                <div class="form-grid">
                  <el-form-item label="用户名">
                    <el-input v-model="sourceForm.username" autocomplete="username" />
                  </el-form-item>
                  <el-form-item label="密码">
                    <el-input
                      v-model="sourceForm.password"
                      type="password"
                      show-password
                      autocomplete="current-password"
                      placeholder="留空则保留原密码"
                    />
                  </el-form-item>
                </div>
                <div class="form-actions">
                  <el-button :loading="loading.test" @click="testSource">测试连接</el-button>
                  <el-button type="primary" :loading="loading.save" @click="saveSource">
                    保存源
                  </el-button>
                </div>
              </el-form>
            </el-card>
          </section>

          <section v-show="activeView === 'remote'" class="remote-layout">
            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>正在播放</strong>
                  <el-tag :type="playback.isPlaying ? 'success' : 'info'">
                    {{ playback.state || 'Idle' }}
                  </el-tag>
                </div>
              </template>
              <div class="now-playing">
                <h2>{{ playback.mediaSourceId || '未播放' }}</h2>
                <p class="muted break-text">{{ playback.uri || '暂无媒体' }}</p>
                <el-slider
                  v-model="seekValue"
                  :max="1000"
                  :disabled="!playback.durationMs"
                  @change="seekPlayback"
                />
                <div class="time-row">
                  <span>{{ formatTime(playback.positionMs) }}</span>
                  <span>{{ formatTime(playback.durationMs) }}</span>
                </div>
              </div>
            </el-card>

            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>控制</strong>
                </div>
              </template>
              <div class="remote-buttons">
                <el-button :icon="DArrowLeft" @click="playbackCommand('skip_backward')">
                  -10s
                </el-button>
                <el-button type="primary" :icon="VideoPlay" @click="playbackCommand('toggle')">
                  播放/暂停
                </el-button>
                <el-button :icon="DArrowRight" @click="playbackCommand('skip_forward')">
                  +30s
                </el-button>
                <el-button :icon="CircleClose" @click="playbackCommand('stop')">
                  停止
                </el-button>
              </div>
              <el-segmented
                v-model="speed"
                class="speed-segmented"
                :options="speedOptions"
                @change="setSpeed"
              />
            </el-card>
          </section>
        </main>
      </section>

      <el-dialog
        v-model="detailOpen"
        class="anime-dialog"
        width="min(920px, 94vw)"
        destroy-on-close
      >
        <template #header>
          <div class="dialog-title">
            <h2>{{ titleOf(selectedAnime.anime) }}</h2>
            <p v-if="originalTitleOf(selectedAnime.anime)" class="muted">
              {{ originalTitleOf(selectedAnime.anime) }}
            </p>
          </div>
        </template>
        <p v-if="selectedAnime.anime?.summary" class="summary">
          {{ selectedAnime.anime.summary }}
        </p>
        <el-empty v-if="!selectedAnime.episodes.length" description="暂无剧集" />
        <div v-else class="episode-list">
          <el-card
            v-for="item in selectedAnime.episodes"
            :key="item.episode.id"
            shadow="never"
            class="episode-card"
          >
            <div>
              <strong>第 {{ item.episode.episodeNumber }} 集 · {{ episodeLabel(item.episode) }}</strong>
              <span class="muted">
                {{ item.progressMs ? `已看到 ${formatTime(item.progressMs)}` : '未观看' }}
              </span>
            </div>
            <el-button type="primary" @click="playEpisode(item.episode.id, item.progressMs)">
              播放
            </el-button>
          </el-card>
        </div>
      </el-dialog>
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleClose,
  DArrowLeft,
  DArrowRight,
  Film,
  FolderOpened,
  Refresh,
  Search,
  SwitchButton,
  VideoPlay
} from '@element-plus/icons-vue'
import { api, formatTime, originalTitleOf, titleOf } from './api'

const activeView = ref('library')
const query = ref('')
const serverInfo = ref(null)
const library = reactive({ continueWatching: [], recentlyAdded: [], allAnime: [] })
const sources = ref([])
const playback = reactive({
  state: 'Idle',
  uri: '',
  mediaSourceId: '',
  positionMs: 0,
  durationMs: 0,
  isPlaying: false,
  error: null
})
const loading = reactive({
  library: false,
  sources: false,
  save: false,
  test: false,
  scan: false
})
const sourceForm = reactive({
  id: 0,
  type: 'LOCAL',
  name: '',
  location: '/storage/emulated/0/Download',
  username: '',
  password: ''
})
const selectedAnime = reactive({ anime: null, episodes: [] })
const detailOpen = ref(false)
const speed = ref(1)
const seekValue = ref(0)
const speedOptions = [
  { label: '0.75x', value: 0.75 },
  { label: '1x', value: 1 },
  { label: '1.25x', value: 1.25 },
  { label: '1.5x', value: 1.5 },
  { label: '2x', value: 2 }
]

let searchTimer = 0
let statusTimer = 0

const viewMeta = computed(() => ({
  library: ['片库', '浏览番剧、选择剧集并投到电视端播放。'],
  sources: ['媒体源', '用电脑或手机键盘添加、编辑和扫描媒体源。'],
  remote: ['遥控器', '播放控制、快进快退和进度拖动。']
}[activeView.value]).reduce((meta, value, index) => {
  if (index === 0) meta.title = value
  if (index === 1) meta.subtitle = value
  return meta
}, {}))

const continueWatching = computed(() =>
  library.continueWatching.filter((item) => item.anime && item.episode)
)

const accessUrl = computed(() => {
  if (!serverInfo.value) return '读取中...'
  const host = serverInfo.value.localIps?.[0] || location.hostname
  return `http://${host}:${serverInfo.value.port}`
})

const locationLabel = computed(() => ({
  LOCAL: '文件夹路径',
  WEBDAV: 'WebDAV 地址',
  SMB: 'SMB 地址'
}[sourceForm.type] || '位置'))

const locationPlaceholder = computed(() => ({
  LOCAL: '/storage/emulated/0/Download',
  WEBDAV: 'http://host:5000/dav',
  SMB: 'smb://host/share'
}[sourceForm.type] || ''))

watch(
  () => [playback.positionMs, playback.durationMs],
  () => {
    seekValue.value = playback.durationMs > 0
      ? Math.round((playback.positionMs / playback.durationMs) * 1000)
      : 0
  }
)

watch(activeView, (view) => {
  if (view === 'sources') loadSources()
  if (view === 'remote') loadPlaybackStatus()
})

onMounted(async () => {
  await Promise.all([loadInfo(), loadLibrary(), loadSources(), loadPlaybackStatus()])
  statusTimer = window.setInterval(loadPlaybackStatus, 2000)
})

onBeforeUnmount(() => {
  window.clearInterval(statusTimer)
  window.clearTimeout(searchTimer)
})

async function loadInfo() {
  serverInfo.value = await api('/api/info')
}

async function loadLibrary() {
  loading.library = true
  try {
    const suffix = query.value.trim() ? `?query=${encodeURIComponent(query.value.trim())}` : ''
    const data = await api(`/api/library${suffix}`)
    library.continueWatching = data.continueWatching || []
    library.recentlyAdded = data.recentlyAdded || []
    library.allAnime = data.allAnime || []
  } finally {
    loading.library = false
  }
}

function debouncedLoadLibrary() {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(loadLibrary, 250)
}

async function loadSources() {
  loading.sources = true
  try {
    sources.value = await api('/api/sources')
  } finally {
    loading.sources = false
  }
}

async function loadPlaybackStatus() {
  const data = await api('/api/playback/status')
  Object.assign(playback, data)
}

async function refreshCurrent() {
  if (activeView.value === 'library') await loadLibrary()
  if (activeView.value === 'sources') await loadSources()
  if (activeView.value === 'remote') await loadPlaybackStatus()
}

async function openAnime(animeId) {
  const detail = await api(`/api/anime/${encodeURIComponent(animeId)}`)
  selectedAnime.anime = detail.anime
  selectedAnime.episodes = detail.episodes || []
  detailOpen.value = true
}

async function playEpisode(episodeId, startPositionMs = 0) {
  await api('/api/playback/play', {
    method: 'POST',
    body: JSON.stringify({ episodeId, startPositionMs })
  })
  detailOpen.value = false
  activeView.value = 'remote'
  ElMessage.success('已发送到电视端播放')
  await loadPlaybackStatus()
}

async function playbackCommand(command, extra = {}) {
  const data = await api('/api/playback/command', {
    method: 'POST',
    body: JSON.stringify({ command, ...extra })
  })
  Object.assign(playback, data)
}

function seekPlayback(value) {
  if (!playback.durationMs) return
  playbackCommand('seek', {
    positionMs: Math.round((value / 1000) * playback.durationMs)
  })
}

function setSpeed(value) {
  playbackCommand('speed', { speed: value })
}

function resetSourceForm() {
  Object.assign(sourceForm, {
    id: 0,
    type: 'LOCAL',
    name: '',
    location: '/storage/emulated/0/Download',
    username: '',
    password: ''
  })
}

function editSource(source) {
  Object.assign(sourceForm, {
    id: source.id,
    type: source.type,
    name: source.name,
    location: sourceLocation(source),
    username: source.connectionInfo?.username || '',
    password: ''
  })
}

function onSourceTypeChange(type) {
  if (type === 'LOCAL' && !sourceForm.location) sourceForm.location = '/storage/emulated/0/Download'
  if (type === 'SMB' && !sourceForm.location) sourceForm.location = 'smb://'
}

function sourcePayload() {
  return {
    id: Number(sourceForm.id || 0),
    type: sourceForm.type,
    name: sourceForm.name.trim(),
    location: sourceForm.location.trim(),
    username: sourceForm.username.trim() || null,
    password: sourceForm.password || null
  }
}

async function saveSource() {
  const payload = sourcePayload()
  if (!payload.name || !payload.location) {
    ElMessage.warning('请填写名称和位置')
    return
  }

  loading.save = true
  try {
    const isEdit = Boolean(payload.id)
    const saved = await api(isEdit ? `/api/sources/${payload.id}` : '/api/sources', {
      method: isEdit ? 'PUT' : 'POST',
      body: JSON.stringify(payload)
    })
    ElMessage.success(`${saved.name} 已保存`)
    resetSourceForm()
    await Promise.all([loadSources(), loadLibrary()])
  } finally {
    loading.save = false
  }
}

async function testSource() {
  const payload = sourcePayload()
  if (!payload.location) {
    ElMessage.warning('请先填写位置')
    return
  }

  loading.test = true
  try {
    const result = await api('/api/sources/test', {
      method: 'POST',
      body: JSON.stringify(payload)
    })
    if (result.connected) {
      ElMessage.success(result.message)
    } else {
      ElMessage.warning(result.message)
    }
  } finally {
    loading.test = false
  }
}

async function scanSource(sourceId) {
  loading.scan = true
  try {
    const result = await api(`/api/sources/${sourceId}/scan`, { method: 'POST' })
    ElMessage.success(`扫描完成：${result.episodesFound} 个文件`)
    await loadLibrary()
  } finally {
    loading.scan = false
  }
}

async function scanAll() {
  loading.scan = true
  try {
    const results = await api('/api/sources/scan-all', { method: 'POST' })
    const count = results.reduce((sum, item) => sum + item.episodesFound, 0)
    ElMessage.success(`扫描完成：${count} 个文件`)
    await loadLibrary()
  } finally {
    loading.scan = false
  }
}

async function deleteSource(sourceId) {
  await ElMessageBox.confirm('确定删除这个媒体源？', '删除媒体源', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await api(`/api/sources/${sourceId}`, { method: 'DELETE' })
  ElMessage.success('媒体源已删除')
  await Promise.all([loadSources(), loadLibrary()])
}

function sourceLocation(source) {
  return source.connectionInfo?.url || source.connectionInfo?.path || ''
}

function episodeLabel(episode) {
  return episode?.title || episode?.fileName || `第 ${episode?.episodeNumber || '?'} 集`
}

function progressPercent(item) {
  const duration = item.episode?.duration || 0
  if (!duration) return 0
  return Math.min(100, Math.round((item.positionMs / duration) * 100))
}

function firstChar(text) {
  return (text || 'M').trim().slice(0, 1).toUpperCase()
}
</script>
