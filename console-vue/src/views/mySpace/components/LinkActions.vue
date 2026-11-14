<template>
  <div class="row-actions">
    <button type="button" class="icon-button" aria-label="查看访问统计" title="查看访问统计" @click="$emit('stats')"><el-icon><Histogram /></el-icon></button>
    <template v-if="!isRecycleBin">
      <button type="button" class="icon-button" aria-label="复制短链" title="复制短链" @click="$emit('copy')"><el-icon><CopyDocument /></el-icon></button>
      <QRCode :url="url" />
      <button type="button" class="icon-button" aria-label="编辑短链" title="编辑短链" @click="$emit('edit')"><el-icon><EditPen /></el-icon></button>
      <el-popconfirm title="将这条短链移入回收站？" width="220" @confirm="$emit('trash')">
        <template #reference><button type="button" class="icon-button" aria-label="移入回收站" title="移入回收站"><el-icon><Delete /></el-icon></button></template>
      </el-popconfirm>
    </template>
    <template v-else>
      <button type="button" class="icon-button" aria-label="恢复短链" title="恢复短链" @click="$emit('restore')"><el-icon><RefreshLeft /></el-icon></button>
      <el-popconfirm title="删除后无法恢复。确定删除？" width="235" @confirm="$emit('remove')">
        <template #reference><button type="button" class="icon-button" aria-label="永久删除" title="永久删除"><el-icon><Delete /></el-icon></button></template>
      </el-popconfirm>
    </template>
  </div>
</template>

<script setup>
import QRCode from './qrCode/QRCode.vue'

defineProps({ url: { type: String, required: true }, isRecycleBin: Boolean })
defineEmits(['stats', 'copy', 'edit', 'trash', 'restore', 'remove'])
</script>

<style scoped>
.row-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 3px; }
.icon-button { display: inline-grid; place-items: center; width: 30px; height: 30px; border: 0; border-radius: 7px; background: transparent; color: var(--accent); font-size: 16px; }
.icon-button:hover { background: var(--accent-soft); }
</style>
