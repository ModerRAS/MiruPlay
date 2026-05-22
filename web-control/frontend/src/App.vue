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
          <el-menu-item index="automation">
            <el-icon><Cloudy /></el-icon>
            <span>自动化</span>
          </el-menu-item>
          <el-menu-item index="metadata">
            <el-icon><Key /></el-icon>
            <span>元数据</span>
          </el-menu-item>
          <el-menu-item index="logs">
            <el-icon><Upload /></el-icon>
            <span>日志上报</span>
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
                      <span class="muted break-text">{{ displayPath(sourceLocation(source)) }}</span>
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
                  <template v-if="sourceForm.type === 'LOCAL'">
                    <div class="local-picker-field">
                      <div class="local-picker-value">
                        <strong>{{ sourceForm.displayName || folderName(sourceForm.location) }}</strong>
                        <span class="muted break-text">{{ sourceForm.location || '尚未选择文件夹' }}</span>
                      </div>
                      <el-button :icon="FolderOpened" @click="openLocalPicker">
                        选择文件夹
                      </el-button>
                    </div>
                  </template>
                  <el-input
                    v-else
                    v-model="sourceForm.location"
                    :placeholder="locationPlaceholder"
                  />
                </el-form-item>
                <div v-if="sourceForm.type !== 'LOCAL'" class="form-grid">
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

          <section v-show="activeView === 'automation'" class="automation-layout">
            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>CloudDrive2</strong>
                  <el-tag :type="automation.tokenConfigured ? 'success' : 'info'">
                    {{ automation.tokenConfigured ? '已授权' : '未授权' }}
                  </el-tag>
                </div>
              </template>

              <el-skeleton v-if="loading.automation" animated :rows="6" />
              <el-form v-else label-position="top" class="automation-form" @submit.prevent>
                <div class="switch-row">
                  <el-switch
                    v-model="cloudForm.enabled"
                    active-text="定时执行"
                    inactive-text="仅手动"
                  />
                  <span class="muted">
                    上次执行：{{ formatDateTime(cloudForm.lastRunAt) || '尚未执行' }}
                  </span>
                </div>

                <el-form-item label="CloudDrive2 地址">
                  <el-input v-model="cloudForm.endpointUrl" placeholder="http://host:19798" />
                </el-form-item>

                <div class="form-grid">
                  <el-form-item label="用户名">
                    <el-input v-model="cloudForm.username" autocomplete="username" />
                  </el-form-item>
                  <el-form-item label="密码">
                    <el-input
                      v-model="cloudForm.password"
                      type="password"
                      show-password
                      autocomplete="current-password"
                    />
                  </el-form-item>
                </div>

                <el-form-item label="API Token / Key">
                  <el-input
                    v-model="cloudForm.apiToken"
                    type="password"
                    show-password
                    placeholder="已有 Key 时可直接保存"
                  />
                </el-form-item>

                <div class="form-grid">
                  <el-form-item label="下载目录 A">
                    <el-input v-model="cloudForm.inboxPath" placeholder="/115/Downloads">
                      <template #append>
                        <el-button
                          :icon="FolderOpened"
                          :disabled="!canBrowseCloudDrive"
                          title="选择下载目录"
                          @click="openCloudPicker('inbox')"
                        />
                      </template>
                    </el-input>
                  </el-form-item>
                  <el-form-item label="整理目录 B">
                    <el-input v-model="cloudForm.libraryPath" placeholder="/115/Anime">
                      <template #append>
                        <el-button
                          :icon="FolderOpened"
                          :disabled="!canBrowseCloudDrive"
                          title="选择整理目录"
                          @click="openCloudPicker('library')"
                        />
                      </template>
                    </el-input>
                  </el-form-item>
                </div>

                <div class="form-grid">
                  <el-form-item label="入库后扫描的 WebDAV 媒体源">
                    <el-select v-model="cloudForm.webDavSourceId" clearable placeholder="可暂不扫描">
                      <el-option
                        v-for="source in webDavSources"
                        :key="source.id"
                        :label="source.name"
                        :value="source.id"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="定时间隔（分钟）">
                    <el-input-number
                      v-model="cloudForm.intervalMinutes"
                      :min="5"
                      :step="5"
                      controls-position="right"
                    />
                  </el-form-item>
                </div>

                <el-divider />

                <div class="switch-row">
                  <el-switch
                    v-model="cloudForm.rssProxyEnabled"
                    active-text="RSS 代理已启用"
                    inactive-text="RSS 代理关闭"
                  />
                </div>
                <div v-if="cloudForm.rssProxyEnabled" class="form-grid">
                  <el-form-item label="代理地址">
                    <el-input v-model="cloudForm.rssProxyHost" placeholder="127.0.0.1" />
                  </el-form-item>
                  <el-form-item label="代理端口">
                    <el-input-number
                      v-model="cloudForm.rssProxyPort"
                      :min="1"
                      :max="65535"
                      controls-position="right"
                    />
                  </el-form-item>
                </div>

                <el-alert
                  type="info"
                  :closable="false"
                  show-icon
                  title="执行时只会提交到下载目录 A，并且只整理下载目录 A 内部的视频文件。"
                />

                <div class="form-actions">
                  <el-button :icon="Setting" :loading="loading.automationSave" @click="saveCloudDriveConfig">
                    保存设置
                  </el-button>
                  <el-button :icon="Key" :loading="loading.cloudLogin" @click="loginCloudDrive">
                    用户名登录
                  </el-button>
                  <el-button :icon="Link" :loading="loading.cloudToken" @click="saveCloudDriveToken">
                    保存 Key
                  </el-button>
                  <el-button type="primary" :icon="Refresh" :loading="loading.cloudRun" @click="runCloudDriveNow">
                    立即执行
                  </el-button>
                </div>
              </el-form>
            </el-card>

            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>RSS 订阅</strong>
                  <el-tag>{{ automation.subscriptions.length }} 个</el-tag>
                </div>
              </template>

              <el-form label-position="top" class="rss-form" @submit.prevent>
                <el-form-item label="订阅名称">
                  <el-input v-model="rssForm.name" placeholder="例如：ANi 新番" />
                </el-form-item>
                <el-form-item label="RSS 地址">
                  <el-input v-model="rssForm.url" placeholder="https://example.com/rss.xml" />
                </el-form-item>
                <el-form-item label="标题过滤正则（可选）">
                  <el-input v-model="rssForm.filterRegex" placeholder="1080|简中|内嵌" />
                </el-form-item>
                <div class="form-actions">
                  <el-switch v-model="rssForm.enabled" active-text="新增后启用" />
                  <el-button type="primary" :loading="loading.rssSave" @click="saveRssSubscription">
                    添加订阅
                  </el-button>
                </div>
              </el-form>

              <el-divider />

              <el-empty v-if="!automation.subscriptions.length" description="还没有 RSS 订阅" />
              <div v-else class="rss-list">
                <el-card
                  v-for="subscription in automation.subscriptions"
                  :key="subscription.id"
                  class="rss-item"
                  shadow="never"
                >
                  <div class="rss-main">
                    <el-tag :type="subscription.enabled ? 'success' : 'info'">
                      {{ subscription.enabled ? '启用' : '停用' }}
                    </el-tag>
                    <div>
                      <strong>{{ subscription.name }}</strong>
                      <span class="muted break-text">{{ subscription.url }}</span>
                      <span v-if="subscription.filterRegex" class="muted">
                        过滤：{{ subscription.filterRegex }}
                      </span>
                      <span class="muted">
                        上次检查：{{ formatDateTime(subscription.lastCheckedAt) || '尚未检查' }}
                      </span>
                    </div>
                  </div>
                  <div class="source-actions">
                    <el-button size="small" @click="toggleRssSubscription(subscription)">
                      {{ subscription.enabled ? '停用' : '启用' }}
                    </el-button>
                    <el-button size="small" type="danger" plain @click="deleteRssSubscription(subscription.id)">
                      删除
                    </el-button>
                  </div>
                </el-card>
              </div>
            </el-card>
          </section>

          <section v-show="activeView === 'metadata'" class="metadata-layout">
            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>Bangumi</strong>
                  <el-tag :type="metadataSettings.bangumiTokenConfigured ? 'success' : 'info'">
                    {{ metadataSettings.bangumiTokenConfigured ? 'Token 已保存' : '未保存 Token' }}
                  </el-tag>
                </div>
              </template>

              <el-skeleton v-if="loading.metadata" animated :rows="4" />
              <el-form v-else label-position="top" class="metadata-form" @submit.prevent>
                <el-form-item label="Bangumi Access Token">
                  <el-input
                    v-model="metadataForm.bangumiToken"
                    type="password"
                    show-password
                    autocomplete="new-password"
                    placeholder="用于 Bangumi 收藏和观看进度同步"
                  />
                </el-form-item>

                <el-alert
                  type="info"
                  :closable="false"
                  show-icon
                  title="Token 只保存到电视端加密凭据，WebUI 不回显明文。"
                />

                <div class="form-actions">
                  <el-button :icon="Key" type="primary" :loading="loading.bangumiToken" @click="saveBangumiToken">
                    保存 Token
                  </el-button>
                  <el-button
                    type="danger"
                    plain
                    :disabled="!metadataSettings.bangumiTokenConfigured"
                    :loading="loading.bangumiToken"
                    @click="clearBangumiToken"
                  >
                    清除 Token
                  </el-button>
                </div>
              </el-form>
            </el-card>

            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>元数据状态</strong>
                  <el-tag :type="metadataSettings.bangumiTokenConfigured ? 'success' : 'warning'">
                    {{ metadataSettings.bangumiTokenConfigured ? '可同步' : '仅公开数据' }}
                  </el-tag>
                </div>
              </template>

              <div class="log-status-grid">
                <div class="status-tile">
                  <span>Bangumi Token</span>
                  <strong>{{ metadataSettings.bangumiTokenConfigured ? '已配置' : '未配置' }}</strong>
                </div>
                <div class="status-tile">
                  <span>元数据匹配</span>
                  <strong>可用</strong>
                </div>
                <div class="status-tile">
                  <span>收藏同步</span>
                  <strong>{{ metadataSettings.bangumiTokenConfigured ? '可用' : '待配置' }}</strong>
                </div>
              </div>
            </el-card>
          </section>

          <section v-show="activeView === 'logs'" class="log-layout">
            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>OpenObserve JSON</strong>
                  <el-tag :type="logUpload.tokenConfigured ? 'success' : 'info'">
                    {{ logUpload.tokenConfigured ? 'Token 已保存' : '未保存 Token' }}
                  </el-tag>
                </div>
              </template>

              <el-skeleton v-if="loading.logUpload" animated :rows="6" />
              <el-form v-else label-position="top" class="log-form" @submit.prevent>
                <div class="switch-row">
                  <el-switch
                    v-model="logForm.enabled"
                    active-text="自动上报"
                    inactive-text="仅本地保存"
                  />
                  <span class="muted">
                    待上报：{{ logUpload.status.pendingCount || 0 }} 条
                  </span>
                </div>

                <el-form-item label="OpenObserve API 地址">
                  <el-input
                    v-model="logForm.endpoint"
                    placeholder="https://openobserve.example.com/api/default"
                  />
                  <span v-if="normalizedLogEndpoint" class="endpoint-preview">
                    实际上报：{{ normalizedLogEndpoint }}
                  </span>
                </el-form-item>

                <div class="form-grid">
                  <el-form-item label="Stream">
                    <el-input v-model="logForm.streamName" placeholder="miruplay" />
                  </el-form-item>
                  <el-form-item label="Token">
                    <el-input
                      v-model="logForm.token"
                      type="password"
                      show-password
                      autocomplete="new-password"
                      placeholder="Basic Token 或 user:password"
                    />
                  </el-form-item>
                </div>

                <div class="form-actions">
                  <el-button :icon="Setting" :loading="loading.logUploadSave" @click="saveLogUploadConfig">
                    保存配置
                  </el-button>
                  <el-button :icon="Key" :loading="loading.logUploadToken" @click="saveLogUploadToken">
                    保存 Token
                  </el-button>
                  <el-button
                    type="danger"
                    plain
                    :disabled="!logUpload.tokenConfigured"
                    :loading="loading.logUploadToken"
                    @click="clearLogUploadToken"
                  >
                    清除 Token
                  </el-button>
                  <el-button
                    type="primary"
                    :icon="Upload"
                    :loading="loading.logUploadRun"
                    :disabled="!canRunLogUpload"
                    @click="runLogUploadNow"
                  >
                    立即上报
                  </el-button>
                </div>
              </el-form>
            </el-card>

            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>上报状态</strong>
                  <el-tag :type="logForm.enabled ? 'success' : 'info'">
                    {{ logForm.enabled ? '已启用' : '未启用' }}
                  </el-tag>
                </div>
              </template>

              <div class="log-status-grid">
                <div class="status-tile">
                  <span>待上报</span>
                  <strong>{{ logUpload.status.pendingCount || 0 }}</strong>
                </div>
                <div class="status-tile">
                  <span>上次上报</span>
                  <strong>{{ formatDateTime(logUpload.status.lastUploadAt) || '尚未上报' }}</strong>
                </div>
                <div class="status-tile">
                  <span>当前状态</span>
                  <strong>{{ logUpload.status.isUploading ? '上报中' : '待命' }}</strong>
                </div>
              </div>

              <el-alert
                class="status-alert"
                :type="logUploadStatusType"
                :closable="false"
                show-icon
                :title="logUpload.status.lastUploadStatus || '暂无上报结果'"
              />
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
                <p class="muted break-text">{{ displayPath(playback.uri) || '暂无媒体' }}</p>
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

      <el-dialog
        v-model="localPicker.open"
        title="选择电视端文件夹"
        width="min(760px, 94vw)"
        destroy-on-close
      >
        <div class="folder-browser">
          <div class="folder-toolbar">
            <el-button
              :disabled="!localBrowser.parentPath"
              @click="loadLocalDirectories(localBrowser.parentPath || '')"
            >
              上一级
            </el-button>
            <strong>{{ localBrowser.displayPath || '设备存储' }}</strong>
          </div>

          <el-skeleton v-if="loading.localBrowse" animated :rows="5" />
          <el-empty v-else-if="!localBrowser.entries.length" description="没有可进入的子文件夹" />
          <div v-else class="folder-list">
            <button
              v-for="entry in localBrowser.entries"
              :key="entry.path"
              class="folder-row"
              type="button"
              :disabled="!entry.canRead"
              @click="loadLocalDirectories(entry.path)"
            >
              <el-icon><FolderOpened /></el-icon>
              <span>{{ entry.name }}</span>
            </button>
          </div>
        </div>

        <template #footer>
          <el-button @click="localPicker.open = false">取消</el-button>
          <el-button
            type="primary"
            :disabled="!localBrowser.path"
            @click="selectCurrentLocalDirectory"
          >
            选择当前文件夹
          </el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="cloudPicker.open"
        :title="cloudPicker.target === 'inbox' ? '选择下载目录 A' : '选择整理目录 B'"
        width="min(760px, 94vw)"
        destroy-on-close
      >
        <div class="folder-browser">
          <div class="folder-toolbar">
            <el-button
              :disabled="!cloudPicker.parentPath"
              @click="loadCloudDirectories(cloudPicker.parentPath || '')"
            >
              上一级
            </el-button>
            <strong>{{ cloudPicker.displayPath || 'CloudDrive 根目录' }}</strong>
          </div>

          <el-skeleton v-if="loading.cloudBrowse" animated :rows="5" />
          <el-empty v-else-if="!cloudPicker.entries.length" description="没有可进入的子文件夹" />
          <div v-else class="folder-list">
            <button
              v-for="entry in cloudPicker.entries"
              :key="entry.path"
              class="folder-row"
              type="button"
              :disabled="!entry.canRead"
              @click="loadCloudDirectories(entry.path)"
            >
              <el-icon><FolderOpened /></el-icon>
              <span>{{ entry.name }}</span>
            </button>
          </div>
        </div>

        <template #footer>
          <el-button @click="cloudPicker.open = false">取消</el-button>
          <el-button
            type="primary"
            :disabled="!cloudPicker.path || cloudPicker.path === '/'"
            @click="selectCurrentCloudDirectory"
          >
            选择当前文件夹
          </el-button>
        </template>
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
  Cloudy,
  DArrowLeft,
  DArrowRight,
  Film,
  FolderOpened,
  Key,
  Link,
  Refresh,
  Search,
  Setting,
  SwitchButton,
  Upload,
  VideoPlay
} from '@element-plus/icons-vue'
import { api, formatTime, originalTitleOf, titleOf } from './api'

const activeView = ref('library')
const query = ref('')
const serverInfo = ref(null)
const library = reactive({ continueWatching: [], recentlyAdded: [], allAnime: [] })
const sources = ref([])
const automation = reactive({
  config: null,
  subscriptions: [],
  tokenConfigured: false
})
const logUpload = reactive({
  config: null,
  status: {
    pendingCount: 0,
    isUploading: false,
    lastUploadAt: 0,
    lastUploadStatus: '',
    tokenConfigured: false
  },
  tokenConfigured: false
})
const metadataSettings = reactive({
  bangumiTokenConfigured: false
})
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
  scan: false,
  localBrowse: false,
  cloudBrowse: false,
  automation: false,
  automationSave: false,
  cloudLogin: false,
  cloudToken: false,
  cloudRun: false,
  rssSave: false,
  logUpload: false,
  logUploadSave: false,
  logUploadToken: false,
  logUploadRun: false,
  metadata: false,
  bangumiToken: false
})
const sourceForm = reactive({
  id: 0,
  type: 'LOCAL',
  name: '',
  location: '/storage/emulated/0/Download',
  displayName: 'Download',
  username: '',
  password: ''
})
const cloudForm = reactive({
  endpointUrl: '',
  username: '',
  password: '',
  apiToken: '',
  webDavSourceId: null,
  inboxPath: '',
  libraryPath: '',
  intervalMinutes: 30,
  enabled: false,
  lastRunAt: 0,
  rssProxyEnabled: false,
  rssProxyHost: '',
  rssProxyPort: 1080
})
const rssForm = reactive({
  name: '',
  url: '',
  filterRegex: '',
  enabled: true
})
const logForm = reactive({
  enabled: false,
  endpoint: '',
  streamName: 'miruplay',
  token: ''
})
const metadataForm = reactive({
  bangumiToken: ''
})
const localPicker = reactive({ open: false })
const localBrowser = reactive({
  path: '',
  displayPath: '设备存储',
  parentPath: null,
  entries: []
})
const cloudPicker = reactive({
  open: false,
  target: 'inbox',
  path: '',
  displayPath: 'CloudDrive 根目录',
  parentPath: null,
  entries: []
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
  automation: ['自动化', '管理 RSS 订阅、CloudDrive2 离线下载和整理入库。'],
  metadata: ['元数据', '配置 Bangumi Token，让收藏和观看进度同步不必在电视上输入。'],
  logs: ['日志上报', '配置 OpenObserve JSON，把本地日志从电视端发送出去。'],
  remote: ['遥控器', '播放控制、快进快退和进度拖动。']
}[activeView.value]).reduce((meta, value, index) => {
  if (index === 0) meta.title = value
  if (index === 1) meta.subtitle = value
  return meta
}, {}))

const continueWatching = computed(() =>
  library.continueWatching.filter((item) => item.anime && item.episode)
)

const webDavSources = computed(() => sources.value.filter((source) => source.type === 'WEBDAV'))
const canBrowseCloudDrive = computed(() =>
  Boolean(cloudForm.endpointUrl.trim()) && automation.tokenConfigured
)
const logUploadStatusType = computed(() => {
  const status = logUpload.status.lastUploadStatus || ''
  if (status.includes('失败')) return 'error'
  if (status.includes('请填写')) return 'warning'
  if (status.includes('已上报')) return 'success'
  return 'info'
})
const normalizedLogEndpoint = computed(() => normalizeOpenObserveEndpoint(logForm.endpoint))
const canRunLogUpload = computed(() =>
  Boolean(logForm.enabled && logForm.endpoint.trim() && (logUpload.tokenConfigured || logForm.token.trim()) && !logUpload.status.isUploading)
)

const accessUrl = computed(() => {
  if (!serverInfo.value) return '读取中...'
  const host = serverInfo.value.localIps?.[0] || location.hostname
  return `http://${host}:${serverInfo.value.port}`
})

const locationLabel = computed(() => ({
  LOCAL: '媒体文件夹',
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
  if (view === 'automation') {
    loadSources()
    loadCloudDriveAutomation()
  }
  if (view === 'metadata') loadMetadataSettings()
  if (view === 'logs') loadLogUpload()
  if (view === 'remote') loadPlaybackStatus()
})

onMounted(async () => {
  await Promise.all([loadInfo(), loadLibrary(), loadSources(), loadCloudDriveAutomation(), loadMetadataSettings(), loadLogUpload(), loadPlaybackStatus()])
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

async function loadCloudDriveAutomation() {
  loading.automation = true
  try {
    applyCloudDriveAutomation(await api('/api/cloud-drive'))
  } finally {
    loading.automation = false
  }
}

async function loadLogUpload() {
  loading.logUpload = true
  try {
    applyLogUpload(await api('/api/log-upload'))
  } finally {
    loading.logUpload = false
  }
}

async function loadMetadataSettings() {
  loading.metadata = true
  try {
    applyMetadataSettings(await api('/api/metadata'))
  } finally {
    loading.metadata = false
  }
}

function applyCloudDriveAutomation(data) {
  automation.config = data.config || null
  automation.subscriptions = data.subscriptions || []
  automation.tokenConfigured = Boolean(data.tokenConfigured)
  const config = data.config || {}
  Object.assign(cloudForm, {
    endpointUrl: config.endpointUrl || '',
    username: config.username || '',
    password: '',
    apiToken: '',
    webDavSourceId: config.webDavSourceId || null,
    inboxPath: config.inboxPath || '',
    libraryPath: config.libraryPath || '',
    intervalMinutes: config.intervalMinutes || 30,
    enabled: Boolean(config.enabled),
    lastRunAt: config.lastRunAt || 0,
    rssProxyEnabled: Boolean(config.rssProxyEnabled),
    rssProxyHost: config.rssProxyHost || '',
    rssProxyPort: config.rssProxyPort || 1080
  })
}

function applyLogUpload(data) {
  logUpload.config = data.config || null
  logUpload.status = data.status || logUpload.status
  logUpload.tokenConfigured = Boolean(data.tokenConfigured)
  const config = data.config || {}
  Object.assign(logForm, {
    enabled: Boolean(config.enabled),
    endpoint: config.endpoint || '',
    streamName: config.streamName || 'miruplay'
  })
}

function applyMetadataSettings(data) {
  metadataSettings.bangumiTokenConfigured = Boolean(data.bangumiTokenConfigured)
}

async function loadPlaybackStatus() {
  const data = await api('/api/playback/status')
  Object.assign(playback, data)
}

async function refreshCurrent() {
  if (activeView.value === 'library') await loadLibrary()
  if (activeView.value === 'sources') await loadSources()
  if (activeView.value === 'automation') await Promise.all([loadSources(), loadCloudDriveAutomation()])
  if (activeView.value === 'metadata') await loadMetadataSettings()
  if (activeView.value === 'logs') await loadLogUpload()
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
    displayName: 'Download',
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
    displayName: source.connectionInfo?.displayName || folderName(sourceLocation(source)),
    username: source.connectionInfo?.username || '',
    password: ''
  })
}

function onSourceTypeChange(type) {
  if (type === 'LOCAL') {
    if (!sourceForm.location || sourceForm.location.startsWith('smb://') || sourceForm.location.startsWith('http')) {
      sourceForm.location = '/storage/emulated/0/Download'
      sourceForm.displayName = 'Download'
    }
    sourceForm.username = ''
    sourceForm.password = ''
  }
  if (type === 'SMB' && !sourceForm.location) sourceForm.location = 'smb://'
}

function sourcePayload() {
  return {
    id: Number(sourceForm.id || 0),
    type: sourceForm.type,
    name: sourceForm.name.trim(),
    location: sourceForm.location.trim(),
    displayName: sourceForm.displayName.trim() || folderName(sourceForm.location),
    username: sourceForm.username.trim() || null,
    password: sourceForm.password || null
  }
}

async function openLocalPicker() {
  localPicker.open = true
  const initialPath = sourceForm.location?.startsWith('/') ? sourceForm.location : ''
  await loadLocalDirectories(initialPath)
}

async function loadLocalDirectories(path = '') {
  loading.localBrowse = true
  try {
    const suffix = path ? `?path=${encodeURIComponent(path)}` : ''
    const data = await api(`/api/local-directories${suffix}`)
    localBrowser.path = data.path || ''
    localBrowser.displayPath = data.displayPath || '设备存储'
    localBrowser.parentPath = data.parentPath ?? null
    localBrowser.entries = data.entries || []
  } finally {
    loading.localBrowse = false
  }
}

function selectCurrentLocalDirectory() {
  if (!localBrowser.path) return
  sourceForm.location = localBrowser.path
  sourceForm.displayName = folderName(localBrowser.path)
  if (!sourceForm.name) sourceForm.name = sourceForm.displayName || '本地媒体库'
  localPicker.open = false
}

async function openCloudPicker(target) {
  if (!canBrowseCloudDrive.value) {
    ElMessage.warning('请先填写 CloudDrive2 地址，并登录或保存 API Key')
    return
  }
  cloudPicker.target = target
  cloudPicker.open = true
  const initialPath = target === 'inbox' ? cloudForm.inboxPath : cloudForm.libraryPath
  await loadCloudDirectories(initialPath)
}

async function loadCloudDirectories(path = '') {
  loading.cloudBrowse = true
  try {
    const params = new URLSearchParams()
    if (cloudForm.endpointUrl.trim()) params.set('endpointUrl', cloudForm.endpointUrl.trim())
    if (path) params.set('path', path)
    const query = params.toString()
    const data = await api(`/api/cloud-drive/directories${query ? `?${query}` : ''}`)
    cloudPicker.path = data.path || ''
    cloudPicker.displayPath = data.displayPath || 'CloudDrive 根目录'
    cloudPicker.parentPath = data.parentPath ?? null
    cloudPicker.entries = data.entries || []
  } catch (error) {
    cloudPicker.entries = []
    ElMessage.error(error?.message || '读取 CloudDrive 目录失败')
  } finally {
    loading.cloudBrowse = false
  }
}

function selectCurrentCloudDirectory() {
  if (!cloudPicker.path || cloudPicker.path === '/') return
  const selectedPath = normalizeCloudPath(cloudPicker.path)
  if (cloudPicker.target === 'inbox') {
    cloudForm.inboxPath = selectedPath
  } else {
    cloudForm.libraryPath = selectedPath
  }
  cloudPicker.open = false
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
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
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
  } catch (e) {
    ElMessage.error(e?.message || '测试连接失败')
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
  } catch (e) {
    ElMessage.error(e?.message || '扫描失败')
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
  } catch (e) {
    ElMessage.error(e?.message || '扫描全部失败')
  } finally {
    loading.scan = false
  }
}

function cloudDriveConfigPayload() {
  return {
    endpointUrl: cloudForm.endpointUrl.trim(),
    username: cloudForm.username.trim(),
    webDavSourceId: cloudForm.webDavSourceId || null,
    inboxPath: cloudForm.inboxPath.trim(),
    libraryPath: cloudForm.libraryPath.trim(),
    intervalMinutes: Number(cloudForm.intervalMinutes || 30),
    enabled: Boolean(cloudForm.enabled),
    rssProxyEnabled: Boolean(cloudForm.rssProxyEnabled),
    rssProxyHost: cloudForm.rssProxyHost.trim(),
    rssProxyPort: Number(cloudForm.rssProxyPort || 1080)
  }
}

function validateCloudDriveConfig(payload) {
  if (!payload.endpointUrl || !payload.inboxPath || !payload.libraryPath) {
    ElMessage.warning('请填写 CloudDrive2 地址、下载目录 A 和整理目录 B')
    return false
  }
  if (payload.inboxPath === '/' || payload.libraryPath === '/') {
    ElMessage.warning('下载目录和整理目录不能是根目录')
    return false
  }
  const inbox = normalizeCloudPath(payload.inboxPath)
  const library = normalizeCloudPath(payload.libraryPath)
  if (library === inbox || library.startsWith(`${inbox}/`)) {
    ElMessage.warning('整理目录 B 不能放在下载目录 A 内部')
    return false
  }
  return true
}

async function saveCloudDriveConfig() {
  const payload = cloudDriveConfigPayload()
  if (!validateCloudDriveConfig(payload)) return

  loading.automationSave = true
  try {
    const data = await api('/api/cloud-drive/config', {
      method: 'PUT',
      body: JSON.stringify(payload)
    })
    applyCloudDriveAutomation(data)
    ElMessage.success('CloudDrive 设置已保存')
  } finally {
    loading.automationSave = false
  }
}

async function loginCloudDrive() {
  if (!cloudForm.endpointUrl.trim() || !cloudForm.username.trim() || !cloudForm.password) {
    ElMessage.warning('请填写 CloudDrive2 地址、用户名和密码')
    return
  }

  loading.cloudLogin = true
  try {
    const data = await api('/api/cloud-drive/login', {
      method: 'POST',
      body: JSON.stringify({
        endpointUrl: cloudForm.endpointUrl.trim(),
        username: cloudForm.username.trim(),
        password: cloudForm.password
      })
    })
    applyCloudDriveAutomation(data)
    ElMessage.success('CloudDrive2 登录成功')
  } finally {
    cloudForm.password = ''
    loading.cloudLogin = false
  }
}

async function saveCloudDriveToken() {
  if (!cloudForm.endpointUrl.trim() || !cloudForm.apiToken.trim()) {
    ElMessage.warning('请填写 CloudDrive2 地址和 API Token / Key')
    return
  }

  loading.cloudToken = true
  try {
    const tokenInfo = await api('/api/cloud-drive/token', {
      method: 'POST',
      body: JSON.stringify({
        endpointUrl: cloudForm.endpointUrl.trim(),
        token: cloudForm.apiToken.trim()
      })
    })
    automation.tokenConfigured = true
    cloudForm.apiToken = ''
    ElMessage.success(`Key 已验证，根目录 ${tokenInfo.rootDir || '/'}`)
  } finally {
    loading.cloudToken = false
  }
}

async function runCloudDriveNow() {
  const payload = cloudDriveConfigPayload()
  if (!validateCloudDriveConfig(payload)) return
  await ElMessageBox.confirm(
    '立即执行会提交未处理 RSS 项到下载目录 A，并只整理下载目录 A 内部的视频文件。',
    '立即执行 CloudDrive/RSS',
    {
      confirmButtonText: '执行',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )

  loading.cloudRun = true
  try {
    const result = await api('/api/cloud-drive/run', { method: 'POST' })
    ElMessage.success(`完成：提交 ${result.submitted}，跳过 ${result.skipped}，整理 ${result.organized}，失败 ${result.failed}`)
    await Promise.all([loadCloudDriveAutomation(), loadLibrary()])
  } finally {
    loading.cloudRun = false
  }
}

async function saveRssSubscription() {
  if (!rssForm.url.trim()) {
    ElMessage.warning('请填写 RSS 地址')
    return
  }

  loading.rssSave = true
  try {
    await api('/api/cloud-drive/rss', {
      method: 'POST',
      body: JSON.stringify({
        name: rssForm.name.trim() || rssForm.url.trim(),
        url: rssForm.url.trim(),
        filterRegex: rssForm.filterRegex.trim() || null,
        enabled: Boolean(rssForm.enabled)
      })
    })
    Object.assign(rssForm, { name: '', url: '', filterRegex: '', enabled: true })
    ElMessage.success('RSS 订阅已保存')
    await loadCloudDriveAutomation()
  } finally {
    loading.rssSave = false
  }
}

async function toggleRssSubscription(subscription) {
  const next = { ...subscription, enabled: !subscription.enabled }
  await api(`/api/cloud-drive/rss/${subscription.id}`, {
    method: 'PUT',
    body: JSON.stringify(next)
  })
  ElMessage.success(next.enabled ? 'RSS 已启用' : 'RSS 已停用')
  await loadCloudDriveAutomation()
}

async function deleteRssSubscription(subscriptionId) {
  await ElMessageBox.confirm('确定删除这个 RSS 订阅？', '删除 RSS', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await api(`/api/cloud-drive/rss/${subscriptionId}`, { method: 'DELETE' })
  ElMessage.success('RSS 订阅已删除')
  await loadCloudDriveAutomation()
}

function logUploadConfigPayload() {
  return {
    enabled: Boolean(logForm.enabled),
    endpoint: logForm.endpoint.trim(),
    streamName: logForm.streamName.trim() || 'miruplay'
  }
}

function validateLogUploadConfig(payload) {
  if (payload.enabled && !payload.endpoint) {
    ElMessage.warning('请填写 OpenObserve API 地址')
    return false
  }
  return true
}

function normalizeOpenObserveEndpoint(endpoint) {
  const raw = String(endpoint || '').trim().replace(/\/+$/g, '')
  if (!raw) return ''
  const withScheme = /^https?:\/\//i.test(raw) ? raw : `http://${raw}`
  try {
    const url = new URL(withScheme)
    const path = url.pathname.replace(/\/+$/g, '')
    const stream = logForm.streamName.trim() || 'miruplay'
    let streamPath = path
    if (path.endsWith('/_json')) {
      streamPath = path
    } else if (path.endsWith('/v1/logs')) {
      streamPath = appendOpenObserveStream(path.slice(0, -'/v1/logs'.length), stream)
    } else if (path.endsWith('/v1/log')) {
      streamPath = appendOpenObserveStream(path.slice(0, -'/v1/log'.length), stream)
    } else if (!path || path === '/') {
      streamPath = `/api/default/${stream}`
    } else if (path === '/api') {
      streamPath = `/api/default/${stream}`
    } else if (path.endsWith('/v1')) {
      streamPath = appendOpenObserveStream(path.slice(0, -'/v1'.length), stream)
    } else if (isOpenObserveStreamPath(path)) {
      streamPath = path
    } else if (path.startsWith('/api/')) {
      streamPath = `${path}/${stream}`
    } else {
      streamPath = `${path}/api/default/${stream}`
    }
    url.pathname = streamPath.endsWith('/_json') ? streamPath : `${streamPath.replace(/\/+$/g, '')}/_json`
    url.search = ''
    url.hash = ''
    return url.toString()
  } catch {
    return withScheme
  }
}

function appendOpenObserveStream(basePath, stream) {
  const normalized = String(basePath || '').replace(/\/+$/g, '')
  if (!normalized) return `/api/default/${stream}`
  return isOpenObserveStreamPath(normalized) ? normalized : `${normalized}/${stream}`
}

function isOpenObserveStreamPath(path) {
  const segments = String(path || '').split('/').filter(Boolean)
  return segments.length === 3 && segments[0] === 'api'
}

async function saveLogUploadConfig() {
  const payload = logUploadConfigPayload()
  if (!validateLogUploadConfig(payload)) return

  loading.logUploadSave = true
  try {
    applyLogUpload(await api('/api/log-upload/config', {
      method: 'PUT',
      body: JSON.stringify(payload)
    }))
    ElMessage.success('日志上报配置已保存')
  } finally {
    loading.logUploadSave = false
  }
}

async function saveLogUploadToken() {
  if (!logForm.token.trim()) {
    ElMessage.warning('请填写 OpenObserve Token')
    return
  }

  loading.logUploadToken = true
  try {
    applyLogUpload(await api('/api/log-upload/token', {
      method: 'POST',
      body: JSON.stringify({ token: logForm.token.trim() })
    }))
    logForm.token = ''
    ElMessage.success('OpenObserve Token 已保存')
  } finally {
    loading.logUploadToken = false
  }
}

async function clearLogUploadToken() {
  await ElMessageBox.confirm('确定清除 OpenObserve Token？', '清除 Token', {
    confirmButtonText: '清除',
    cancelButtonText: '取消',
    type: 'warning'
  })

  loading.logUploadToken = true
  try {
    applyLogUpload(await api('/api/log-upload/token', { method: 'DELETE' }))
    ElMessage.success('OpenObserve Token 已清除')
  } finally {
    loading.logUploadToken = false
  }
}

async function runLogUploadNow() {
  const payload = logUploadConfigPayload()
  if (!validateLogUploadConfig(payload)) return
  if (!payload.enabled) {
    ElMessage.warning('请先开启自动上报')
    return
  }
  if (!logUpload.tokenConfigured && !logForm.token.trim()) {
    ElMessage.warning('请先保存 OpenObserve Token')
    return
  }
  if (!logUpload.config || payload.endpoint !== logUpload.config.endpoint || payload.streamName !== logUpload.config.streamName || payload.enabled !== logUpload.config.enabled) {
    await saveLogUploadConfig()
  }
  if (logForm.token.trim()) {
    await saveLogUploadToken()
  }

  loading.logUploadRun = true
  try {
    applyLogUpload(await api('/api/log-upload/run', { method: 'POST' }))
    ElMessage.success(logUpload.status.lastUploadStatus || '日志上报已执行')
  } finally {
    loading.logUploadRun = false
  }
}

async function saveBangumiToken() {
  if (!metadataForm.bangumiToken.trim()) {
    ElMessage.warning('请填写 Bangumi Token')
    return
  }

  loading.bangumiToken = true
  try {
    applyMetadataSettings(await api('/api/metadata/bangumi-token', {
      method: 'POST',
      body: JSON.stringify({ token: metadataForm.bangumiToken.trim() })
    }))
    metadataForm.bangumiToken = ''
    ElMessage.success('Bangumi Token 已保存')
  } finally {
    loading.bangumiToken = false
  }
}

async function clearBangumiToken() {
  await ElMessageBox.confirm('确定清除 Bangumi Token？', '清除 Token', {
    confirmButtonText: '清除',
    cancelButtonText: '取消',
    type: 'warning'
  })

  loading.bangumiToken = true
  try {
    applyMetadataSettings(await api('/api/metadata/bangumi-token', { method: 'DELETE' }))
    metadataForm.bangumiToken = ''
    ElMessage.success('Bangumi Token 已清除')
  } finally {
    loading.bangumiToken = false
  }
}

async function deleteSource(sourceId) {
  try {
    await ElMessageBox.confirm('确定删除这个媒体源？', '删除媒体源', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await api(`/api/sources/${sourceId}`, { method: 'DELETE' })
    ElMessage.success('媒体源已删除')
    await Promise.all([loadSources(), loadLibrary()])
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.message || '删除失败')
    }
  }
}

function sourceLocation(source) {
  return source.connectionInfo?.uri || source.connectionInfo?.url || source.connectionInfo?.path || ''
}

function displayPath(path) {
  return safeDecodePath(path)
}

function folderName(path) {
  if (!path) return ''
  return safeDecodePath(path.split('/').filter(Boolean).pop() || path)
}

function safeDecodePath(path) {
  if (!path) return ''
  try {
    return decodeURIComponent(path)
  } catch {
    return path
  }
}

function normalizeCloudPath(path) {
  const value = String(path || '').trim().replace(/\\/g, '/').replace(/\/+$/g, '')
  if (!value) return ''
  return value.startsWith('/') ? value : `/${value}`
}

function formatDateTime(timestamp) {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
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
