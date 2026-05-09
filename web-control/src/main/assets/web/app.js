const state = {
  view: 'library',
  library: null,
  sources: [],
  playback: null,
  editingSource: null,
  statusTimer: null
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  });
  const envelope = await response.json();
  if (!response.ok || !envelope.ok) {
    throw new Error(envelope.error || `HTTP ${response.status}`);
  }
  return envelope.data;
}

function toast(message) {
  const el = $('#toast');
  el.textContent = message;
  el.classList.add('is-visible');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => el.classList.remove('is-visible'), 2600);
}

function formatTime(ms) {
  if (!Number.isFinite(ms) || ms <= 0) return '00:00';
  const total = Math.floor(ms / 1000);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function titleOf(anime) {
  return anime.title || anime.titleCn || anime.id || '未知番剧';
}

function originalTitleOf(anime) {
  const primary = titleOf(anime);
  const candidates = [anime.titleCn, anime.id].filter(Boolean);
  return candidates.find((value) => value && value !== primary) || '';
}

function firstChar(text) {
  return (text || 'M').trim().slice(0, 1).toUpperCase();
}

function setView(view) {
  state.view = view;
  $$('.tab').forEach((tab) => tab.classList.toggle('is-active', tab.dataset.view === view));
  $$('.view').forEach((panel) => panel.classList.toggle('is-active', panel.id === `${view}View`));

  const copy = {
    library: ['片库', '浏览番剧、选择剧集并投到电视端播放。'],
    sources: ['媒体源', '用电脑或手机键盘添加、编辑和扫描媒体源。'],
    remote: ['遥控器', '播放控制、快进快退和进度拖动。']
  }[view];
  $('#viewTitle').textContent = copy[0];
  $('#viewSubtitle').textContent = copy[1];
  $('#searchInput').closest('.search').style.display = view === 'library' ? 'grid' : 'none';

  if (view === 'sources') loadSources();
  if (view === 'remote') loadPlaybackStatus();
}

async function loadInfo() {
  const info = await api('/api/info');
  $('#serverLabel').textContent = info.deviceName;
  const host = info.localIps[0] || location.hostname;
  $('#accessUrl').textContent = `http://${host}:${info.port}`;
}

async function loadLibrary() {
  const query = $('#searchInput').value.trim();
  state.library = await api(`/api/library${query ? `?query=${encodeURIComponent(query)}` : ''}`);
  renderLibrary();
}

function renderLibrary() {
  const library = state.library || { continueWatching: [], allAnime: [] };
  $('#libraryCount').textContent = `${library.allAnime.length} 部`;

  const continueList = $('#continueList');
  continueList.innerHTML = '';
  const validContinue = library.continueWatching.filter((item) => item.anime && item.episode);
  $('#continueSection').style.display = validContinue.length ? 'block' : 'none';
  validContinue.forEach((item) => {
    const button = document.createElement('button');
    button.className = 'continue-item';
    const title = titleOf(item.anime);
    const originalTitle = originalTitleOf(item.anime);
    const episodeName = item.episode.title || item.episode.fileName || `第 ${item.episode.episodeNumber} 集`;
    const duration = item.episode.duration || 0;
    const percent = duration > 0 ? Math.min(100, Math.round(item.positionMs / duration * 100)) : 0;
    button.innerHTML = `
      <strong>${escapeHtml(title)}</strong>
      ${originalTitle ? `<span class="sub-title">${escapeHtml(originalTitle)}</span>` : ''}
      <small>${escapeHtml(episodeName)} · ${formatTime(item.positionMs)}</small>
      <div class="progress"><i style="width:${percent}%"></i></div>
    `;
    button.addEventListener('click', () => playEpisode(item.episode.id, item.positionMs));
    continueList.appendChild(button);
  });

  const grid = $('#animeGrid');
  grid.innerHTML = '';
  if (!library.allAnime.length) {
    grid.innerHTML = '<div class="empty">还没有扫描到番剧。先去“媒体源”添加并扫描。</div>';
    return;
  }

  library.allAnime.forEach((anime) => {
    const button = document.createElement('button');
    button.className = 'poster';
    const title = titleOf(anime);
    const originalTitle = originalTitleOf(anime);
    const image = anime.posterUrl
      ? `<img src="${escapeAttr(anime.posterUrl)}" alt="">`
      : `<div class="poster-art">${escapeHtml(firstChar(title))}</div>`;
    button.innerHTML = `
      ${image}
      <div class="poster-info">
        <span class="poster-title">${escapeHtml(title)}</span>
        ${originalTitle ? `<span class="poster-original">${escapeHtml(originalTitle)}</span>` : ''}
        <small>${anime.episodeCount || 0} 集</small>
      </div>
    `;
    button.addEventListener('click', () => openAnime(anime.id));
    grid.appendChild(button);
  });
}

async function openAnime(animeId) {
  const detail = await api(`/api/anime/${encodeURIComponent(animeId)}`);
  $('#dialogTitle').textContent = titleOf(detail.anime);
  const originalTitle = originalTitleOf(detail.anime);
  $('#dialogSummary').textContent = detail.anime.summary || `${detail.episodes.length} 集`;
  $('#dialogOriginalTitle').textContent = originalTitle;
  $('#dialogOriginalTitle').style.display = originalTitle ? 'block' : 'none';
  const list = $('#episodeList');
  list.innerHTML = '';
  if (!detail.episodes.length) {
    list.innerHTML = '<div class="empty">暂无剧集。</div>';
  }
  detail.episodes.forEach(({ episode, progressMs }) => {
    const row = document.createElement('div');
    row.className = 'episode-row';
    const name = episode.title || episode.fileName || `第 ${episode.episodeNumber} 集`;
    row.innerHTML = `
      <div>
        <strong>第 ${episode.episodeNumber} 集 · ${escapeHtml(name)}</strong>
        <small>${progressMs ? `已看到 ${formatTime(progressMs)}` : '未观看'}</small>
      </div>
      <button>播放</button>
    `;
    row.querySelector('button').addEventListener('click', () => playEpisode(episode.id, progressMs));
    list.appendChild(row);
  });
  $('#animeDialog').showModal();
}

async function playEpisode(episodeId, startPositionMs = 0) {
  await api('/api/playback/play', {
    method: 'POST',
    body: JSON.stringify({ episodeId, startPositionMs })
  });
  $('#animeDialog').close();
  toast('已发送到电视端播放');
  setView('remote');
}

async function loadSources() {
  state.sources = await api('/api/sources');
  renderSources();
}

function renderSources() {
  const list = $('#sourceList');
  list.innerHTML = '';
  if (!state.sources.length) {
    list.innerHTML = '<div class="empty">还没有媒体源。</div>';
    return;
  }
  state.sources.forEach((source) => {
    const location = source.connectionInfo.url || source.connectionInfo.path || '';
    const item = document.createElement('article');
    item.className = 'source-item';
    item.innerHTML = `
      <strong>${escapeHtml(source.name)}</strong>
      <small>${source.type} · ${source.isConnected ? '可连接' : '待验证'} · ${escapeHtml(location)}</small>
      <div class="source-actions">
        <button data-action="edit">编辑</button>
        <button data-action="scan">扫描</button>
        <button data-action="delete">删除</button>
      </div>
    `;
    item.querySelector('[data-action="edit"]').addEventListener('click', () => editSource(source));
    item.querySelector('[data-action="scan"]').addEventListener('click', () => scanSource(source.id));
    item.querySelector('[data-action="delete"]').addEventListener('click', () => deleteSource(source.id));
    list.appendChild(item);
  });
}

function resetSourceForm() {
  state.editingSource = null;
  $('#sourceFormTitle').textContent = '添加源';
  $('#sourceId').value = '';
  $('#sourceType').value = 'LOCAL';
  $('#sourceName').value = '';
  $('#sourceLocation').value = '/storage/emulated/0/Download';
  $('#sourceUsername').value = '';
  $('#sourcePassword').value = '';
  $('#sourceMessage').textContent = '';
  updateSourceLocationLabel();
}

function editSource(source) {
  state.editingSource = source;
  $('#sourceFormTitle').textContent = '编辑源';
  $('#sourceId').value = source.id;
  $('#sourceType').value = source.type;
  $('#sourceName').value = source.name;
  $('#sourceLocation').value = source.connectionInfo.url || source.connectionInfo.path || '';
  $('#sourceUsername').value = source.connectionInfo.username || '';
  $('#sourcePassword').value = '';
  $('#sourceMessage').textContent = '密码留空会保留原密码。';
  updateSourceLocationLabel();
}

function sourcePayload() {
  return {
    id: Number($('#sourceId').value || 0),
    type: $('#sourceType').value,
    name: $('#sourceName').value.trim(),
    location: $('#sourceLocation').value.trim(),
    username: $('#sourceUsername').value.trim() || null,
    password: $('#sourcePassword').value || null
  };
}

async function saveSource(event) {
  event.preventDefault();
  const payload = sourcePayload();
  const isEdit = Boolean(payload.id);
  const saved = await api(isEdit ? `/api/sources/${payload.id}` : '/api/sources', {
    method: isEdit ? 'PUT' : 'POST',
    body: JSON.stringify(payload)
  });
  toast(`${saved.name} 已保存`);
  resetSourceForm();
  await loadSources();
  await loadLibrary();
}

async function testSource() {
  const payload = sourcePayload();
  $('#sourceMessage').textContent = '测试中...';
  const result = await api('/api/sources/test', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  $('#sourceMessage').textContent = result.message;
}

async function scanSource(sourceId) {
  toast('开始扫描媒体源');
  const result = await api(`/api/sources/${sourceId}/scan`, { method: 'POST' });
  toast(`扫描完成：${result.episodesFound} 个文件`);
  await loadLibrary();
}

async function scanAll() {
  toast('开始扫描全部媒体源');
  const results = await api('/api/sources/scan-all', { method: 'POST' });
  const count = results.reduce((sum, item) => sum + item.episodesFound, 0);
  toast(`扫描完成：${count} 个文件`);
  await loadLibrary();
}

async function deleteSource(sourceId) {
  if (!confirm('确定删除这个媒体源？')) return;
  await api(`/api/sources/${sourceId}`, { method: 'DELETE' });
  toast('媒体源已删除');
  await loadSources();
  await loadLibrary();
}

function updateSourceLocationLabel() {
  const type = $('#sourceType').value;
  $('#sourceLocationLabel').textContent = {
    LOCAL: '文件夹路径',
    WEBDAV: 'WebDAV 地址',
    SMB: 'SMB 地址'
  }[type] || '位置';
  if (type === 'SMB' && !$('#sourceLocation').value) $('#sourceLocation').value = 'smb://';
}

async function loadPlaybackStatus() {
  state.playback = await api('/api/playback/status');
  renderPlayback();
}

function renderPlayback() {
  const playback = state.playback || {};
  $('#playbackState').textContent = playback.state || 'Idle';
  $('#playbackTitle').textContent = playback.mediaSourceId || '未播放';
  $('#playbackUri').textContent = playback.uri || '';
  $('#positionLabel').textContent = formatTime(playback.positionMs || 0);
  $('#durationLabel').textContent = formatTime(playback.durationMs || 0);
  const seek = $('#seekBar');
  const duration = playback.durationMs || 0;
  seek.value = duration > 0 ? Math.round((playback.positionMs || 0) / duration * 1000) : 0;
}

async function playbackCommand(command, extra = {}) {
  state.playback = await api('/api/playback/command', {
    method: 'POST',
    body: JSON.stringify({ command, ...extra })
  });
  renderPlayback();
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[char]));
}

function escapeAttr(value) {
  return escapeHtml(value).replace(/`/g, '&#96;');
}

function bindEvents() {
  $$('.tab').forEach((tab) => tab.addEventListener('click', () => setView(tab.dataset.view)));
  $('#refreshBtn').addEventListener('click', () => state.view === 'sources' ? loadSources() : loadLibrary());
  $('#searchInput').addEventListener('input', () => {
    clearTimeout(state.searchTimer);
    state.searchTimer = setTimeout(loadLibrary, 220);
  });
  $('#closeDialogBtn').addEventListener('click', () => $('#animeDialog').close());
  $('#newSourceBtn').addEventListener('click', resetSourceForm);
  $('#sourceType').addEventListener('change', updateSourceLocationLabel);
  $('#sourceForm').addEventListener('submit', saveSource);
  $('#testSourceBtn').addEventListener('click', testSource);
  $('#scanAllBtn').addEventListener('click', scanAll);
  $$('[data-command]').forEach((button) => {
    button.addEventListener('click', () => playbackCommand(button.dataset.command));
  });
  $$('[data-speed]').forEach((button) => {
    button.addEventListener('click', () => playbackCommand('speed', { speed: Number(button.dataset.speed) }));
  });
  $('#seekBar').addEventListener('change', () => {
    const duration = state.playback?.durationMs || 0;
    playbackCommand('seek', { positionMs: Math.round(Number($('#seekBar').value) / 1000 * duration) });
  });
}

async function init() {
  bindEvents();
  resetSourceForm();
  await Promise.all([loadInfo(), loadLibrary(), loadSources(), loadPlaybackStatus()]);
  state.statusTimer = setInterval(loadPlaybackStatus, 2000);
}

init().catch((error) => toast(error.message));
