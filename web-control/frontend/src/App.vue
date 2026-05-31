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
          <el-menu-item index="proxy">
            <el-icon><Setting /></el-icon>
            <span>代理配置</span>
          </el-menu-item>
          <el-menu-item index="metadata">
            <el-icon><Key /></el-icon>
            <span>元数据</span>
          </el-menu-item>
          <el-menu-item index="logs">
            <el-icon><Upload /></el-icon>
            <span>日志</span>
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
                  <el-tag :type="cloudCredentialStatusType">
                    {{ cloudCredentialStatusLabel }}
                  </el-tag>
                </div>
              </template>

              <el-skeleton v-if="loading.automation" animated :rows="6" />
              <el-form v-else label-position="top" class="automation-form" @submit.prevent>
                <div class="auth-flow">
                  <el-tag effect="plain" type="info">设置</el-tag>
                  <el-icon><DArrowRight /></el-icon>
                  <el-tag :type="cloudCredentialStatusType" effect="plain">授权</el-tag>
                  <el-icon><DArrowRight /></el-icon>
                  <el-tag effect="plain" type="info">执行</el-tag>
                </div>
                <div class="credential-status">
                  <el-tag :type="automation.passwordConfigured ? 'success' : 'info'" effect="light">
                    {{ automation.passwordConfigured ? '用户名密码已保存' : '用户名密码未保存' }}
                  </el-tag>
                  <el-tag :type="automation.tokenConfigured ? 'success' : 'info'" effect="light">
                    {{ automation.tokenConfigured ? 'Key 已保存' : 'Key 未保存' }}
                  </el-tag>
                </div>
                <div class="switch-row">
                  <el-switch
                    v-model="cloudForm.enabled"
                    active-text="定时执行"
                    inactive-text="仅手动"
                  />
                  <span class="muted">
                    上次执行：{{ formatDateTime(cloudForm.lastRunAt) || '尚未执行' }}
                  </span>
                  <span v-if="cloudRunStatus.running" class="muted">
                    正在执行：{{ formatDateTime(cloudRunStatus.startedAt) }}
                  </span>
                  <span v-else-if="cloudRunStatus.status === 'SUCCEEDED' && cloudRunStatus.summary" class="muted">
                    上次结果：{{ cloudRunSummaryText(cloudRunStatus.summary) }}
                  </span>
                  <span v-else-if="cloudRunStatus.status === 'FAILED'" class="danger-text">
                    执行失败：{{ cloudRunStatus.error || '未知错误' }}
                  </span>
                </div>

                <el-form-item label="CloudDrive2 地址">
                  <el-input v-model="cloudForm.endpointUrl" :placeholder="DEFAULT_CLOUD_DRIVE_ENDPOINT_URL" />
                </el-form-item>

                <el-form-item label="入库模式">
                  <el-radio-group v-model="cloudForm.libraryMode">
                    <el-radio-button value="ORGANIZED_LIBRARY">整理入库</el-radio-button>
                    <el-radio-button value="SINGLE_DIRECTORY">单目录入库</el-radio-button>
                  </el-radio-group>
                </el-form-item>

                <div class="form-grid">
                  <el-form-item label="用户名">
                    <el-input v-model="cloudForm.username" autocomplete="username" />
                  </el-form-item>
                  <el-form-item label="密码（登录后保存）">
                    <el-input
                      v-model="cloudForm.password"
                      type="password"
                      show-password
                      autocomplete="current-password"
                    />
                  </el-form-item>
                </div>

                <el-form-item label="API Token / Key（可替代密码登录）">
                  <el-input
                    v-model="cloudForm.apiToken"
                    type="password"
                    show-password
                    placeholder="已有 Key 时可直接保存"
                  />
                </el-form-item>

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
                <el-form-item v-if="cloudForm.libraryMode === 'ORGANIZED_LIBRARY'" label="整理目录 B">
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
                <div class="form-actions">
                  <el-button :icon="Setting" :loading="loading.automationSave" @click="saveCloudDriveConfig">
                    保存设置
                  </el-button>
                  <el-button :icon="Key" :loading="loading.cloudLogin" @click="loginCloudDrive">
                    登录并保存密码
                  </el-button>
                  <el-button :icon="Link" :loading="loading.cloudToken" @click="saveCloudDriveToken">
                    验证并保存 Key
                  </el-button>
                  <el-button type="primary" :icon="Refresh" :loading="loading.cloudRun" @click="runCloudDriveNow">
                    保存并立即执行
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

          <section v-show="activeView === 'proxy'" class="proxy-layout">
            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>出站代理</strong>
                  <el-tag :type="proxyStatusType">
                    {{ proxyForm.enabled ? '已启用' : '未启用' }}
                  </el-tag>
                </div>
              </template>

              <el-skeleton v-if="loading.proxy" animated :rows="4" />
              <el-form v-else label-position="top" class="proxy-form" @submit.prevent>
                <div class="switch-row">
                  <el-switch
                    v-model="proxyForm.enabled"
                    active-text="代理已启用"
                    inactive-text="代理关闭"
                  />
                  <span class="muted">Bangumi API、Archive 下载和 RSS 请求共用此 HTTP 代理。</span>
                </div>

                <div class="form-grid">
                  <el-form-item label="代理地址">
                    <el-input v-model="proxyForm.host" placeholder="203.0.113.20" />
                  </el-form-item>
                  <el-form-item label="代理端口">
                    <el-input-number
                      v-model="proxyForm.port"
                      :min="1"
                      :max="65535"
                      controls-position="right"
                    />
                  </el-form-item>
                </div>

                <div class="form-actions">
                  <el-button
                    type="primary"
                    :icon="Setting"
                    :loading="loading.proxySave"
                    :disabled="proxyForm.enabled && !proxyForm.host.trim()"
                    @click="saveProxyConfig"
                  >
                    保存代理
                  </el-button>
                  <el-button :icon="Refresh" :loading="loading.proxy" @click="loadProxyConfig">
                    刷新
                  </el-button>
                </div>
              </el-form>
            </el-card>

            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>当前状态</strong>
                  <el-tag>{{ proxySummary }}</el-tag>
                </div>
              </template>
              <div class="log-status-grid">
                <div class="status-tile">
                  <span>代理</span>
                  <strong>{{ proxyForm.enabled ? '启用' : '关闭' }}</strong>
                </div>
                <div class="status-tile">
                  <span>地址</span>
                  <strong>{{ proxyForm.host || '未填写' }}</strong>
                </div>
                <div class="status-tile">
                  <span>端口</span>
                  <strong>{{ proxyForm.port || 1080 }}</strong>
                </div>
                <div class="status-tile">
                  <span>作用范围</span>
                  <strong>Bangumi / Archive / RSS</strong>
                </div>
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
                  <span>离线搜索</span>
                  <strong>{{ bangumiArchive.hasSubjectData ? '可用' : '未下载' }}</strong>
                </div>
                <div class="status-tile">
                  <span>收藏同步</span>
                  <strong>{{ metadataSettings.bangumiTokenConfigured ? '可用' : '待配置' }}</strong>
                </div>
              </div>
            </el-card>

            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="card-header">
                  <strong>Bangumi Archive</strong>
                  <el-tag :type="bangumiArchiveStatusType">
                    {{ bangumiArchiveStatusLabel }}
                  </el-tag>
                </div>
              </template>

              <el-skeleton v-if="loading.metadata" animated :rows="3" />
              <div v-else class="metadata-form">
                <div class="log-status-grid">
                  <div class="status-tile">
                    <span>索引文件</span>
                    <strong>{{ bangumiArchive.hasSubjectData ? '已下载' : '未下载' }}</strong>
                  </div>
                  <div class="status-tile">
                    <span>文件大小</span>
                    <strong>{{ formatBytes(bangumiArchive.subjectFileSizeBytes) }}</strong>
                  </div>
                  <div class="status-tile">
                    <span>版本</span>
                    <strong>{{ bangumiArchive.latestName || '未知' }}</strong>
                  </div>
                  <div class="status-tile">
                    <span>更新时间</span>
                    <strong>{{ formatIsoDateTime(bangumiArchive.latestUpdatedAt || bangumiArchive.latestCreatedAt) || '未知' }}</strong>
                  </div>
                  <div class="status-tile">
                    <span>自动更新</span>
                    <strong>{{ bangumiArchiveAutoUpdateText }}</strong>
                  </div>
                </div>

                <div v-if="bangumiArchive.isDownloading" class="archive-progress">
                  <el-progress
                    :percentage="bangumiArchiveProgress"
                    :indeterminate="bangumiArchiveProgress === 0"
                  />
                  <span>{{ bangumiArchiveProgressText }}</span>
                </div>

                <div v-if="loading.bangumiArchiveUpload" class="archive-progress">
                  <el-progress
                    :percentage="bangumiArchiveUploadProgress"
                    :indeterminate="bangumiArchiveUploadProgress === 0"
                  />
                  <span>{{ bangumiArchiveUploadProgressText }}</span>
                </div>

                <el-alert
                  v-if="bangumiArchive.lastError"
                  type="warning"
                  :closable="false"
                  show-icon
                  :title="bangumiArchive.lastError"
                />

                <div class="archive-upload-row">
                  <input
                    ref="bangumiArchiveFileInput"
                    class="hidden-file-input"
                    type="file"
                    accept=".zip,.jsonlines,.jsonl,application/zip,application/x-zip-compressed,application/json"
                    @change="onBangumiArchiveFileSelected"
                  />
                  <div class="archive-upload-copy">
                    <strong>{{ bangumiArchiveUploadName || '手动上传 Archive' }}</strong>
                    <span class="muted">
                      {{ bangumiArchiveUploadSize ? formatBytes(bangumiArchiveUploadSize) : '支持 dump zip 或 subject.jsonlines' }}
                    </span>
                  </div>
                  <el-button
                    :icon="FolderOpened"
                    :disabled="bangumiArchive.isDownloading || loading.bangumiArchiveUpload"
                    @click="chooseBangumiArchiveFile"
                  >
                    选择文件
                  </el-button>
                </div>

                <div class="form-actions">
                  <el-button
                    :icon="Download"
                    type="primary"
                    :disabled="!bangumiArchive.available || bangumiArchive.isDownloading || loading.bangumiArchiveUpload"
                    :loading="loading.bangumiArchive || bangumiArchive.isDownloading"
                    @click="downloadBangumiArchive"
                  >
                    {{ bangumiArchive.isDownloading ? '下载中' : (bangumiArchive.hasSubjectData ? '更新 Archive' : '下载 Archive') }}
                  </el-button>
                  <el-button :icon="Refresh" :loading="loading.bangumiArchive" @click="loadBangumiArchiveStatus">
                    刷新状态
                  </el-button>
                  <el-button
                    :icon="Upload"
                    :disabled="!bangumiArchive.available || !bangumiArchiveUploadFile || bangumiArchive.isDownloading || loading.bangumiArchiveUpload"
                    :loading="loading.bangumiArchiveUpload"
                    @click="uploadBangumiArchive"
                  >
                    上传 Archive
                  </el-button>
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
                  <el-button :icon="Setting" :loading="loading.logUploadSave || loading.logUploadToken" @click="saveLogUploadSettings">
                    保存设置
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

            <el-card shadow="never" class="panel-card log-view-card">
              <template #header>
                <div class="card-header">
                  <strong>本地日志</strong>
                  <el-tag type="info">{{ localLogs.totalCount || 0 }} 条</el-tag>
                </div>
              </template>

              <div class="log-view-toolbar">
                <el-segmented
                  v-model="localLogs.limit"
                  :options="logLimitOptions"
                  @change="loadLocalLogs"
                />
                <div class="form-actions compact-actions">
                  <el-select
                    v-model="logDownloadRange"
                    class="log-range-select"
                    placeholder="下载范围"
                  >
                    <el-option
                      v-for="option in logDownloadRangeOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                  <el-button :icon="Refresh" :loading="loading.localLogs" @click="loadLocalLogs">
                    刷新
                  </el-button>
                  <el-button
                    type="primary"
                    :icon="Download"
                    :loading="loading.logDownload"
                    :disabled="!localLogs.totalCount"
                    @click="downloadLocalLogs"
                  >
                    下载
                  </el-button>
                </div>
              </div>

              <el-alert
                v-if="localLogs.truncatedCount > 0"
                class="status-alert"
                type="info"
                :closable="false"
                show-icon
                :title="`当前显示最近 ${localLogs.returnedCount} 条，另有 ${localLogs.truncatedCount} 条较早记录可下载查看。`"
              />

              <el-skeleton v-if="loading.localLogs" animated :rows="6" />
              <el-empty v-else-if="!localLogs.records.length" description="暂无本地日志" />
              <el-scrollbar v-else class="log-record-scroll" max-height="34rem">
                <div class="log-record-list">
                  <article
                    v-for="(record, index) in localLogs.records"
                    :key="record.id || `${record.timestampMs}-${index}`"
                    class="log-record"
                  >
                    <div class="log-record-header">
                      <div class="log-record-meta">
                        <el-tag size="small" :type="logLevelTagType(record.level)">
                          {{ record.level || 'INFO' }}
                        </el-tag>
                        <strong>{{ record.tag || 'MiruPlay' }}</strong>
                      </div>
                      <span class="muted">{{ formatLogDateTime(record.timestampMs) }}</span>
                    </div>
                    <pre class="log-message">{{ record.message }}</pre>
                    <details v-if="hasLogDetails(record)" class="log-detail">
                      <summary>详情</summary>
                      <pre v-if="record.throwableClass || record.throwableMessage">{{ formatThrowable(record) }}</pre>
                      <pre v-if="record.stackTrace">{{ record.stackTrace }}</pre>
                      <pre v-if="Object.keys(record.attributes || {}).length">{{ formatLogAttributes(record.attributes) }}</pre>
                    </details>
                  </article>
                </div>
              </el-scrollbar>
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
  Download,
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
  tokenConfigured: false,
  passwordConfigured: false
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
const localLogs = reactive({
  totalCount: 0,
  returnedCount: 0,
  truncatedCount: 0,
  records: [],
  limit: 200
})
const metadataSettings = reactive({
  bangumiTokenConfigured: false
})
const bangumiArchive = reactive({
  available: false,
  hasSubjectData: false,
  latestName: '',
  latestCreatedAt: '',
  latestUpdatedAt: '',
  subjectFileSizeBytes: 0,
  autoUpdateEnabled: true,
  autoUpdateIntervalDays: 7,
  isDownloading: false,
  downloadedBytes: 0,
  totalBytes: 0,
  lastError: ''
})
const bangumiArchiveFileInput = ref(null)
const bangumiArchiveUploadFile = ref(null)
const bangumiArchiveUploadBytes = ref(0)
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
  localLogs: false,
  logDownload: false,
  metadata: false,
  bangumiToken: false,
  bangumiArchive: false,
  bangumiArchiveUpload: false,
  proxy: false,
  proxySave: false
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
const DEFAULT_CLOUD_DRIVE_ENDPOINT_URL = 'http://localhost:19798'
const MAX_BANGUMI_ARCHIVE_UPLOAD_BYTES = 2 * 1024 * 1024 * 1024
const cloudForm = reactive({
  endpointUrl: DEFAULT_CLOUD_DRIVE_ENDPOINT_URL,
  username: '',
  password: '',
  apiToken: '',
  webDavSourceId: null,
  inboxPath: '',
  libraryPath: '',
  libraryMode: 'ORGANIZED_LIBRARY',
  intervalMinutes: 30,
  enabled: false,
  lastRunAt: 0,
  rssProxyEnabled: false,
  rssProxyHost: '',
  rssProxyPort: 1080
})
const cloudRunStatus = reactive({
  status: 'IDLE',
  running: false,
  startedAt: 0,
  finishedAt: 0,
  summary: null,
  error: null
})
const proxyForm = reactive({
  enabled: false,
  host: '',
  port: 1080
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
const logDownloadRange = ref('1h')
const speedOptions = [
  { label: '0.75x', value: 0.75 },
  { label: '1x', value: 1 },
  { label: '1.25x', value: 1.25 },
  { label: '1.5x', value: 1.5 },
  { label: '2x', value: 2 }
]
const logLimitOptions = [
  { label: '100', value: 100 },
  { label: '200', value: 200 },
  { label: '500', value: 500 },
  { label: '1000', value: 1000 }
]
const logDownloadRangeOptions = [
  { label: '最近 15 分钟', value: '15m', millis: 15 * 60 * 1000 },
  { label: '最近 1 小时', value: '1h', millis: 60 * 60 * 1000 },
  { label: '最近 24 小时', value: '24h', millis: 24 * 60 * 60 * 1000 },
  { label: '全部', value: 'all', millis: 0 }
]

let searchTimer = 0
let statusTimer = 0
let archiveTimer = 0
let cloudRunTimer = 0

const viewMeta = computed(() => ({
  library: ['片库', '浏览番剧、选择剧集并投到电视端播放。'],
  sources: ['媒体源', '用电脑或手机键盘添加、编辑和扫描媒体源。'],
  automation: ['自动化', '管理 RSS 订阅、CloudDrive2 离线下载和整理入库。'],
  proxy: ['代理配置', '设置 Bangumi、Archive 下载和 RSS 请求共用的出站 HTTP 代理。'],
  metadata: ['元数据', '配置 Bangumi Token，让收藏和观看进度同步不必在电视上输入。'],
  logs: ['日志', '查看、下载本地日志，也可以配置 OpenObserve JSON 上报。'],
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
const cloudCredentialStatusLabel = computed(() => {
  if (automation.tokenConfigured && automation.passwordConfigured) return 'Key 与密码已保存'
  if (automation.tokenConfigured) return 'Key 已保存'
  if (automation.passwordConfigured) return '密码已保存'
  return '未授权'
})
const cloudCredentialStatusType = computed(() => (
  automation.tokenConfigured || automation.passwordConfigured ? 'success' : 'info'
))
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
const bangumiArchiveStatusType = computed(() => {
  if (!bangumiArchive.available || bangumiArchive.lastError) return 'warning'
  if (loading.bangumiArchiveUpload) return 'info'
  if (bangumiArchive.isDownloading) return 'info'
  return bangumiArchive.hasSubjectData ? 'success' : 'info'
})
const bangumiArchiveStatusLabel = computed(() => {
  if (!bangumiArchive.available) return '不可用'
  if (loading.bangumiArchiveUpload) return '上传中'
  if (bangumiArchive.isDownloading) return '下载中'
  return bangumiArchive.hasSubjectData ? '离线搜索可用' : '未下载'
})
const bangumiArchiveProgress = computed(() => {
  if (!bangumiArchive.totalBytes || bangumiArchive.totalBytes <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((bangumiArchive.downloadedBytes / bangumiArchive.totalBytes) * 100)))
})
const bangumiArchiveProgressText = computed(() => {
  const downloaded = formatBytes(bangumiArchive.downloadedBytes)
  const total = formatBytes(bangumiArchive.totalBytes)
  return bangumiArchive.totalBytes > 0 ? `${downloaded} / ${total}` : downloaded
})
const bangumiArchiveAutoUpdateText = computed(() =>
  bangumiArchive.autoUpdateEnabled ? `每 ${bangumiArchive.autoUpdateIntervalDays || 7} 天` : '未启用'
)
const bangumiArchiveUploadName = computed(() => bangumiArchiveUploadFile.value?.name || '')
const bangumiArchiveUploadSize = computed(() => Number(bangumiArchiveUploadFile.value?.size || 0))
const bangumiArchiveUploadProgress = computed(() => {
  const total = bangumiArchiveUploadSize.value
  if (!total || total <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((bangumiArchiveUploadBytes.value / total) * 100)))
})
const bangumiArchiveUploadProgressText = computed(() => {
  const uploaded = formatBytes(bangumiArchiveUploadBytes.value)
  const total = formatBytes(bangumiArchiveUploadSize.value)
  return bangumiArchiveUploadSize.value > 0 ? `${uploaded} / ${total}` : uploaded
})
const proxyStatusType = computed(() => {
  if (proxyForm.enabled && !proxyForm.host.trim()) return 'warning'
  return proxyForm.enabled ? 'success' : 'info'
})
const proxySummary = computed(() => {
  if (!proxyForm.enabled) return '未启用'
  const host = proxyForm.host.trim()
  return host ? `${host}:${Number(proxyForm.port || 1080)}` : '待填写'
})

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
    loadCloudDriveRunStatus()
  }
  if (view === 'proxy') loadProxyConfig()
  if (view === 'metadata') loadMetadataSettings()
  if (view === 'logs') {
    loadLogUpload()
    loadLocalLogs()
  }
  if (view === 'remote') loadPlaybackStatus()
})

onMounted(async () => {
  await Promise.all([loadInfo(), loadLibrary(), loadSources(), loadCloudDriveAutomation(), loadCloudDriveRunStatus(), loadProxyConfig(), loadMetadataSettings(), loadLogUpload(), loadLocalLogs(), loadPlaybackStatus()])
  statusTimer = window.setInterval(loadPlaybackStatus, 2000)
  archiveTimer = window.setInterval(() => {
    if (activeView.value === 'metadata' && bangumiArchive.isDownloading) {
      loadBangumiArchiveStatus(false)
    }
  }, 2000)
})

onBeforeUnmount(() => {
  window.clearInterval(statusTimer)
  window.clearInterval(archiveTimer)
  window.clearTimeout(searchTimer)
  window.clearTimeout(cloudRunTimer)
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

async function loadCloudDriveRunStatus(options = {}) {
  try {
    const status = await api('/api/cloud-drive/run')
    applyCloudDriveRunStatus(status, options)
  } catch {
    loading.cloudRun = false
  }
}

async function loadProxyConfig() {
  loading.proxy = true
  try {
    applyProxyConfig(await api('/api/proxy'))
  } finally {
    loading.proxy = false
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

async function loadLocalLogs() {
  loading.localLogs = true
  try {
    const data = await api(`/api/logs?limit=${encodeURIComponent(localLogs.limit)}`)
    applyLocalLogs(data)
  } finally {
    loading.localLogs = false
  }
}

async function loadMetadataSettings() {
  loading.metadata = true
  try {
    const [settings, archive] = await Promise.all([
      api('/api/metadata'),
      api('/api/metadata/bangumi-archive')
    ])
    applyMetadataSettings(settings)
    applyBangumiArchive(archive)
  } finally {
    loading.metadata = false
  }
}

async function loadBangumiArchiveStatus(showLoading = true) {
  if (showLoading) loading.bangumiArchive = true
  try {
    applyBangumiArchive(await api('/api/metadata/bangumi-archive'))
  } finally {
    if (showLoading) loading.bangumiArchive = false
  }
}

function applyCloudDriveAutomation(data) {
  automation.config = data.config || null
  automation.subscriptions = data.subscriptions || []
  automation.tokenConfigured = Boolean(data.tokenConfigured)
  automation.passwordConfigured = Boolean(data.passwordConfigured)
  const config = data.config || {}
  Object.assign(cloudForm, {
    endpointUrl: config.endpointUrl || DEFAULT_CLOUD_DRIVE_ENDPOINT_URL,
    username: config.username || '',
    password: '',
    apiToken: '',
    webDavSourceId: config.webDavSourceId || null,
    inboxPath: config.inboxPath || '',
    libraryPath: config.libraryPath || '',
    libraryMode: config.libraryMode || 'ORGANIZED_LIBRARY',
    intervalMinutes: config.intervalMinutes || 30,
    enabled: Boolean(config.enabled),
    lastRunAt: config.lastRunAt || 0,
    rssProxyEnabled: Boolean(config.rssProxyEnabled),
    rssProxyHost: config.rssProxyHost || '',
    rssProxyPort: config.rssProxyPort || 1080
  })
  applyProxyConfig({
    enabled: Boolean(config.rssProxyEnabled),
    host: config.rssProxyHost || '',
    port: config.rssProxyPort || 1080
  })
}

function applyCloudDriveRunStatus(data, options = {}) {
  const next = data || {}
  const wasRunning = cloudRunStatus.running
  Object.assign(cloudRunStatus, {
    status: next.status || 'IDLE',
    running: Boolean(next.running),
    startedAt: next.startedAt || 0,
    finishedAt: next.finishedAt || 0,
    summary: next.summary || null,
    error: next.error || null
  })
  loading.cloudRun = cloudRunStatus.running

  if (cloudRunStatus.running) {
    scheduleCloudDriveRunPoll()
    return
  }

  window.clearTimeout(cloudRunTimer)
  if (options.notify && wasRunning) {
    if (cloudRunStatus.status === 'SUCCEEDED') {
      ElMessage.success(`完成：${cloudRunSummaryText(cloudRunStatus.summary)}`)
      void Promise.all([loadCloudDriveAutomation(), loadLibrary(), loadSources()])
    } else if (cloudRunStatus.status === 'FAILED') {
      ElMessage.error(cloudRunStatus.error || 'CloudDrive/RSS 执行失败')
    }
  }
}

function scheduleCloudDriveRunPoll() {
  window.clearTimeout(cloudRunTimer)
  cloudRunTimer = window.setTimeout(() => {
    loadCloudDriveRunStatus({ notify: true })
  }, 3000)
}

function cloudRunSummaryText(summary) {
  const data = summary || {}
  return `提交 ${data.submitted || 0}，跳过 ${data.skipped || 0}，失败 ${data.failed || 0}，整理 ${data.organized || 0}，索引 ${data.indexed || 0}`
}

function applyProxyConfig(data) {
  const next = data || {}
  Object.assign(proxyForm, {
    enabled: Boolean(next.enabled),
    host: next.host || '',
    port: next.port || 1080
  })
  Object.assign(cloudForm, {
    rssProxyEnabled: Boolean(next.enabled),
    rssProxyHost: next.host || '',
    rssProxyPort: next.port || 1080
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

function applyLocalLogs(data) {
  Object.assign(localLogs, {
    totalCount: Number(data?.totalCount || 0),
    returnedCount: Number(data?.returnedCount || 0),
    truncatedCount: Number(data?.truncatedCount || 0),
    records: Array.isArray(data?.records) ? data.records : []
  })
}

function applyMetadataSettings(data) {
  metadataSettings.bangumiTokenConfigured = Boolean(data.bangumiTokenConfigured)
}

function applyBangumiArchive(data) {
  Object.assign(bangumiArchive, {
    available: Boolean(data?.available),
    hasSubjectData: Boolean(data?.hasSubjectData),
    latestName: data?.latestName || '',
    latestCreatedAt: data?.latestCreatedAt || '',
    latestUpdatedAt: data?.latestUpdatedAt || '',
    subjectFileSizeBytes: Number(data?.subjectFileSizeBytes || 0),
    autoUpdateEnabled: data?.autoUpdateEnabled !== false,
    autoUpdateIntervalDays: Number(data?.autoUpdateIntervalDays || 7),
    isDownloading: Boolean(data?.isDownloading),
    downloadedBytes: Number(data?.downloadedBytes || 0),
    totalBytes: Number(data?.totalBytes || 0),
    lastError: data?.lastError || ''
  })
}

async function loadPlaybackStatus() {
  const data = await api('/api/playback/status')
  Object.assign(playback, data)
}

async function refreshCurrent() {
  if (activeView.value === 'library') await loadLibrary()
  if (activeView.value === 'sources') await loadSources()
  if (activeView.value === 'automation') await Promise.all([loadSources(), loadCloudDriveAutomation()])
  if (activeView.value === 'proxy') await loadProxyConfig()
  if (activeView.value === 'metadata') await loadMetadataSettings()
  if (activeView.value === 'logs') await Promise.all([loadLogUpload(), loadLocalLogs()])
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
    if (result?.error) {
      ElMessage.warning(`扫描失败：${result.error}`)
    } else {
      ElMessage.success(`扫描完成：${result.episodesFound} 个文件`)
    }
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
    const failedItems = results.filter(item => item?.error)
    if (failedItems.length > 0) {
      const failedSourceNames = failedItems
        .map(item => item?.animeName || `源 ${item?.sourceId ?? ''}`)
        .slice(0, 3)
        .join('、')
      const moreSuffix = failedItems.length > 3 ? ` 等 ${failedItems.length} 个源` : ''
      ElMessage.warning(`扫描部分完成：${count} 个文件，${failedItems.length} 个源失败（${failedSourceNames}${moreSuffix}）`)
    } else {
      ElMessage.success(`扫描完成：${count} 个文件`)
    }
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
    libraryMode: cloudForm.libraryMode,
    intervalMinutes: Number(cloudForm.intervalMinutes || 30),
    enabled: Boolean(cloudForm.enabled),
    rssProxyEnabled: Boolean(proxyForm.enabled),
    rssProxyHost: proxyForm.host.trim(),
    rssProxyPort: Number(proxyForm.port || 1080)
  }
}

function validateCloudDriveConfig(payload) {
  const organized = payload.libraryMode !== 'SINGLE_DIRECTORY'
  if (!payload.endpointUrl || !payload.inboxPath || (organized && !payload.libraryPath)) {
    ElMessage.warning('请填写 CloudDrive2 地址和下载目录 A')
    return false
  }
  if (payload.inboxPath === '/' || (organized && payload.libraryPath === '/')) {
    ElMessage.warning('下载目录和整理目录不能是根目录')
    return false
  }
  if (organized) {
    const inbox = normalizeCloudPath(payload.inboxPath)
    const library = normalizeCloudPath(payload.libraryPath)
    if (library === inbox || library.startsWith(`${inbox}/`)) {
      ElMessage.warning('整理目录 B 不能放在下载目录 A 内部')
      return false
    }
  }
  return true
}

async function saveCloudDriveConfig() {
  const payload = cloudDriveConfigPayload()
  if (!validateCloudDriveConfig(payload)) return

  loading.automationSave = true
  try {
    await persistCloudDriveConfig(payload)
    ElMessage.success('CloudDrive 设置已保存')
  } finally {
    loading.automationSave = false
  }
}

async function persistCloudDriveConfig(payload) {
  const data = await api('/api/cloud-drive/config', {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
  applyCloudDriveAutomation(data)
  return data
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
  const passwordForLogin = cloudForm.password
  if (passwordForLogin && !payload.username) {
    ElMessage.warning('请先填写 CloudDrive2 用户名')
    return
  }
  if (!automation.tokenConfigured && !automation.passwordConfigured && !passwordForLogin) {
    ElMessage.warning('请先登录并保存密码，或验证并保存 Key')
    return
  }
  const modeLabel = payload.libraryMode === 'SINGLE_DIRECTORY' ? '单目录入库' : '整理入库'
  await ElMessageBox.confirm(
    `将先保存当前设置，再提交未处理 RSS 项到下载目录 A，并按当前${modeLabel}模式处理。`,
    '保存并立即执行 CloudDrive/RSS',
    {
      confirmButtonText: '执行',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )

  loading.cloudRun = true
  try {
    await persistCloudDriveConfig(payload)
    if (passwordForLogin) {
      const data = await api('/api/cloud-drive/login', {
        method: 'POST',
        body: JSON.stringify({
          endpointUrl: payload.endpointUrl,
          username: payload.username,
          password: passwordForLogin
        })
      })
      applyCloudDriveAutomation(data)
    }
    const status = await api('/api/cloud-drive/run', { method: 'POST' })
    applyCloudDriveRunStatus(status)
    if (status.running) {
      ElMessage.success('CloudDrive/RSS 已开始执行')
    } else if (status.status === 'SUCCEEDED') {
      ElMessage.success(`完成：${cloudRunSummaryText(status.summary)}`)
      await Promise.all([loadCloudDriveAutomation(), loadLibrary(), loadSources()])
    } else if (status.status === 'FAILED') {
      ElMessage.error(status.error || 'CloudDrive/RSS 执行失败')
    }
  } catch (error) {
    loading.cloudRun = false
    throw error
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

async function saveLogUploadConfig(showSuccess = true) {
  const payload = logUploadConfigPayload()
  if (!validateLogUploadConfig(payload)) return false

  loading.logUploadSave = true
  try {
    applyLogUpload(await api('/api/log-upload/config', {
      method: 'PUT',
      body: JSON.stringify(payload)
    }))
    if (showSuccess) {
      ElMessage.success('日志上报配置已保存')
    }
    return true
  } finally {
    loading.logUploadSave = false
  }
}

async function saveLogUploadToken(showSuccess = true) {
  if (!logForm.token.trim()) {
    ElMessage.warning('请填写 OpenObserve Token')
    return false
  }

  loading.logUploadToken = true
  try {
    applyLogUpload(await api('/api/log-upload/token', {
      method: 'POST',
      body: JSON.stringify({ token: logForm.token.trim() })
    }))
    logForm.token = ''
    if (showSuccess) {
      ElMessage.success('OpenObserve Token 已保存')
    }
    return true
  } finally {
    loading.logUploadToken = false
  }
}

async function saveLogUploadSettings(showSuccess = true) {
  const hasTokenInput = Boolean(logForm.token.trim())
  if (!(await saveLogUploadConfig(false))) return false
  if (hasTokenInput && !(await saveLogUploadToken(false))) return false
  if (showSuccess) {
    ElMessage.success(hasTokenInput ? '日志上报设置和 Token 已保存' : '日志上报设置已保存')
  }
  return true
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
    ElMessage.warning('请先填写 OpenObserve Token')
    return
  }
  if (!logUpload.config || payload.endpoint !== logUpload.config.endpoint || payload.streamName !== logUpload.config.streamName || payload.enabled !== logUpload.config.enabled || logForm.token.trim()) {
    if (!(await saveLogUploadSettings(false))) return
  }

  loading.logUploadRun = true
  try {
    applyLogUpload(await api('/api/log-upload/run', { method: 'POST' }))
    await loadLocalLogs()
    ElMessage.success(logUpload.status.lastUploadStatus || '日志上报已执行')
  } finally {
    loading.logUploadRun = false
  }
}

async function downloadLocalLogs() {
  loading.logDownload = true
  try {
    const rangeMs = selectedLogDownloadRangeMs()
    const downloadUrl = rangeMs ? `/api/logs/download?rangeMs=${encodeURIComponent(rangeMs)}` : '/api/logs/download'
    const response = await fetch(downloadUrl)
    if (!response.ok) {
      let message = `HTTP ${response.status}`
      try {
        const envelope = await response.json()
        message = envelope.error || message
      } catch {
        // Keep the HTTP status fallback.
      }
      throw new Error(message)
    }
    const blob = await response.blob()
    const filename = downloadFilename(response.headers.get('Content-Disposition')) || 'miruplay-logs.jsonl'
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(objectUrl)
    ElMessage.success('日志已下载')
  } catch (error) {
    ElMessage.error(error?.message || '下载日志失败')
  } finally {
    loading.logDownload = false
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

function proxyConfigPayload() {
  return {
    enabled: Boolean(proxyForm.enabled),
    host: proxyForm.host.trim(),
    port: Number(proxyForm.port || 1080)
  }
}

function validateProxyConfig(payload) {
  if (payload.enabled && !payload.host) {
    ElMessage.warning('请填写代理地址')
    return false
  }
  return true
}

async function saveProxyConfig() {
  const payload = proxyConfigPayload()
  if (!validateProxyConfig(payload)) return

  loading.proxySave = true
  try {
    applyProxyConfig(await api('/api/proxy', {
      method: 'PUT',
      body: JSON.stringify(payload)
    }))
    ElMessage.success('代理配置已保存')
  } finally {
    loading.proxySave = false
  }
}

async function downloadBangumiArchive() {
  loading.bangumiArchive = true
  try {
    applyBangumiArchive(await api('/api/metadata/bangumi-archive/download', { method: 'POST' }))
    ElMessage.success(bangumiArchive.isDownloading ? 'Bangumi Archive 下载已开始' : 'Bangumi Archive 已更新')
  } finally {
    loading.bangumiArchive = false
  }
}

function chooseBangumiArchiveFile() {
  bangumiArchiveFileInput.value?.click()
}

function onBangumiArchiveFileSelected(event) {
  const file = event?.target?.files?.[0] || null
  bangumiArchiveUploadFile.value = file
  bangumiArchiveUploadBytes.value = 0
}

async function uploadBangumiArchive() {
  const file = bangumiArchiveUploadFile.value
  if (!file) {
    ElMessage.warning('请选择 Bangumi Archive 文件')
    return
  }
  if (file.size <= 0) {
    ElMessage.warning('上传文件为空')
    return
  }
  if (file.size > MAX_BANGUMI_ARCHIVE_UPLOAD_BYTES) {
    ElMessage.warning(`上传文件过大，最大支持 ${formatBytes(MAX_BANGUMI_ARCHIVE_UPLOAD_BYTES)}`)
    return
  }

  loading.bangumiArchiveUpload = true
  bangumiArchiveUploadBytes.value = 0
  try {
    const data = await uploadBangumiArchiveRequest(file)
    applyBangumiArchive(data)
    bangumiArchiveUploadFile.value = null
    if (bangumiArchiveFileInput.value) bangumiArchiveFileInput.value.value = ''
    if (bangumiArchive.lastError) {
      ElMessage.warning(bangumiArchive.lastError)
    } else {
      ElMessage.success('Bangumi Archive 已导入')
    }
  } catch (error) {
    ElMessage.error(error?.message || '上传失败')
  } finally {
    loading.bangumiArchiveUpload = false
  }
}

function uploadBangumiArchiveRequest(file) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    const filename = encodeURIComponent(file.name || 'bangumi-archive')
    xhr.open('POST', `/api/metadata/bangumi-archive/upload?filename=${filename}`)
    xhr.responseType = 'json'
    xhr.setRequestHeader('Content-Type', 'application/octet-stream')
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        bangumiArchiveUploadBytes.value = event.loaded
      }
    }
    xhr.onload = () => {
      const envelope = xhr.response || safeJsonParse(xhr.responseText)
      if (xhr.status < 200 || xhr.status >= 300 || !envelope?.ok) {
        reject(new Error(envelope?.error || `HTTP ${xhr.status}`))
        return
      }
      resolve(envelope.data)
    }
    xhr.onerror = () => reject(new Error('上传失败'))
    xhr.send(file)
  })
}

function safeJsonParse(text) {
  try {
    return JSON.parse(text || '{}')
  } catch {
    return null
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

function formatLogDateTime(timestamp) {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function formatIsoDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatBytes(bytes) {
  const value = Number(bytes || 0)
  if (!Number.isFinite(value) || value <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = value
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit += 1
  }
  return `${size >= 10 || unit === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[unit]}`
}

function logLevelTagType(level) {
  const normalized = String(level || '').toUpperCase()
  if (normalized === 'ERROR') return 'danger'
  if (normalized === 'WARN') return 'warning'
  if (normalized === 'DEBUG') return 'info'
  return 'success'
}

function hasLogDetails(record) {
  return Boolean(
    record?.throwableClass ||
      record?.throwableMessage ||
      record?.stackTrace ||
      Object.keys(record?.attributes || {}).length
  )
}

function formatThrowable(record) {
  return [record.throwableClass, record.throwableMessage]
    .filter(Boolean)
    .join(': ')
}

function formatLogAttributes(attributes) {
  return JSON.stringify(attributes || {}, null, 2)
}

function downloadFilename(contentDisposition) {
  const value = String(contentDisposition || '')
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(value)
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1])
    } catch {
      return encoded[1]
    }
  }
  return /filename="([^"]+)"/i.exec(value)?.[1] || ''
}

function selectedLogDownloadRangeMs() {
  const option = logDownloadRangeOptions.find((item) => item.value === logDownloadRange.value)
  return option?.millis || 0
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
