<template>
  <div class="stat-card">
    <div class="title-box">
      <span>{{ title }}</span>
      <slot name="titleButton">
      </slot>
    </div>
    <div class="content-box">
      <slot name="content"></slot>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
defineProps({
  title: {
    type: String,
    default: '默认标题'
  }
})
const emits = defineEmits(['onMounted'])
onMounted(() => {
  emits('onMounted')
})
</script>

<style lang="scss" scoped>
.stat-card { border: 1px solid var(--line); border-radius: 12px; display: flex; flex-direction: column; }
.title-box {
  display: flex;
  position: relative;
  justify-content: space-between;
  align-items: center;
  height: 50px;
  border-bottom: 1px solid var(--line);
  padding: 15px 20px;
  color: #254bc3;
  font-size: 15px;
  font-weight: 650;
}

.content-box {
  flex: 1;
}
</style>