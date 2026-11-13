<!-- LinkStamp changes: draw the QR code trigger without a bundled image. -->
<template>
  <el-popover placement="bottom" trigger="click" :width="180">
    <div class="main-box">
      <canvas ref="canvas"></canvas>
      <el-button type="primary" :width="100" @click="download">下载二维码</el-button>
    </div>
    <template #reference>
      <div class="qr-code">
        <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
          <path d="M2 2h6v6H2zm10 0h6v6h-6zM2 12h6v6H2z" fill="none" stroke="currentColor" />
          <path d="M4 4h2v2H4zm10 0h2v2h-2zM4 14h2v2H4zm8-2h2v2h-2zm4 0h2v4h-2zm-4 4h4v2h-4zm4 2h2v2h-2z" fill="currentColor" />
        </svg>
      </div>
    </template>
  </el-popover>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
const props = defineProps({
  url: String
})
const canvas = ref()
// 二维码
const createQrCode = () => {
  QRCode.toCanvas(canvas.value, props.url, { width: 150, height: 150 })
}
const download = () => {
  let a = document.createElement('a')
  a.href = canvas.value.toDataURL('image/png');
  a.download = '二维码';
  a.click()
  ElMessage.success('正在下载')
}
onMounted(() => {
  createQrCode()
})
</script>

<style lang="scss" scoped>
.main-box {
  display: flex;
  flex-direction: column;
}

.qr-code {
  cursor: pointer !important;
}

.qr-code:hover {
  opacity: .5;
}
</style>
