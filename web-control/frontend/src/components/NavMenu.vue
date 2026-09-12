<script setup>
defineProps({
  activeView: { type: String, required: true },
})
defineEmits(['select'])
</script>

<template>
  <el-menu class="nav-menu" :default-active="activeView" @select="$emit('select', $event)">
    <el-menu-item index="library"><el-icon><Film /></el-icon><span>片库</span></el-menu-item>
    <el-menu-item index="sources"><el-icon><FolderOpened /></el-icon><span>媒体源</span></el-menu-item>
    <el-menu-item index="automation"><el-icon><Cloudy /></el-icon><span>自动化</span></el-menu-item>
    <el-menu-item index="proxy"><el-icon><Setting /></el-icon><span>代理配置</span></el-menu-item>
    <el-menu-item index="metadata"><el-icon><Key /></el-icon><span>元数据</span></el-menu-item>
    <el-menu-item index="scan"><el-icon><Refresh /></el-icon><span>扫描设置</span></el-menu-item>
    <el-menu-item index="playback"><el-icon><VideoPlay /></el-icon><span>播放设置</span></el-menu-item>
    <el-menu-item index="audio-dsp"><el-icon><Headset /></el-icon><span>音频 DSP</span></el-menu-item>
    <el-menu-item index="translation"><el-icon><ChatLineSquare /></el-icon><span>字幕翻译</span></el-menu-item>
    <el-menu-item index="webui"><el-icon><Monitor /></el-icon><span>WebUI 访问</span></el-menu-item>
    <el-menu-item index="app-update"><el-icon><Download /></el-icon><span>应用更新</span></el-menu-item>
    <el-menu-item index="about"><el-icon><InfoFilled /></el-icon><span>关于</span></el-menu-item>
    <el-menu-item index="logs"><el-icon><Upload /></el-icon><span>日志</span></el-menu-item>
    <el-menu-item index="remote"><el-icon><SwitchButton /></el-icon><span>遥控器</span></el-menu-item>
  </el-menu>
</template>
