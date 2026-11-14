<!-- LinkStamp changes: draw the QR code trigger without a bundled image. -->
<template>
  <el-popover placement="bottom" trigger="click" :width="180">
    <div class="main-box">
      <canvas ref="canvas"></canvas>
      <el-button type="primary" :width="100" @click="download">下载二维码</el-button>
    </div>
    <template #reference>
      <button type="button" class="qr-code" aria-label="查看二维码" title="查看二维码">
        <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
          <path d="M2 2h6v6H2zm10 0h6v6h-6zM2 12h6v6H2z" fill="none" stroke="currentColor" />
          <path d="M4 4h2v2H4zm10 0h2v2h-2zM4 14h2v2H4zm8-2h2v2h-2zm4 0h2v4h-2zm-4 4h4v2h-4zm4 2h2v2h-2z" fill="currentColor" />
        </svg>
      </button>
    </template>
  </el-popover>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
const props = defineProps({
  url: String
})
const canvas = ref()
// 二维码
const download = () => {
  let a = document.createElement('a')
  a.href = canvas.value.toDataURL('image/png');
  a.download = '二维码.png';
  a.click()
  ElMessage.success('正在下载')
}
watch([canvas, () => props.url], async ([element, url]) => {
  if (!element || !url) return
  try {
    await QRCode.toCanvas(element, url, { width: 150, color: { dark: '#202327', light: '#ffffff' } })
  } catch {
    ElMessage.error('二维码生成失败')
  }
})
</script>

<style lang="scss" scoped>
.main-box {
  display: flex;
  flex-direction: column;
}

.qr-code {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--accent);
}

.qr-code:hover {
  background: var(--accent-soft);
}
</style>
