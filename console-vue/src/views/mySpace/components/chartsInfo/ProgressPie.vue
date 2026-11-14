<template>
  <div class="main-box">
    <el-progress color="#254bc3" type="circle" :stroke-width="10" :percentage="data1Precentage">
      <template #default>
        <div class="flex-box">
          <span class="percentage-value">{{ labels[0] }}: {{ data1Precentage }}%</span>
          <span class="percentage-label">{{ data[0] }} {{ labels[0] === '新访客' ? '人' : '次' }}</span>
        </div>
      </template>
    </el-progress>
    <el-progress color="#254bc3" type="circle" :stroke-width="10" :percentage="data2Precentage">
      <template #default>
        <div class="flex-box">
          <span class="percentage-value">{{ labels[1] }}: {{ data2Precentage }}%</span>
          <span class="percentage-label">{{ data[1] }} {{ labels[1] === '旧访客' ? '人' : '次' }}</span>
        </div>
      </template>
    </el-progress>
  </div>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({
  labels: {
    type: Array,
    // eslint-disable-next-line vue/require-valid-default-prop
    default: ['数据1', '数据2']
  },
  data: {
    type: Array,
    // 和上面的labels对应
    default: [10, 0]
  }
})
const percentage = (index) => computed(() => {
  const total = Number(props.data[0]) + Number(props.data[1])
  return total ? Math.round(Number(props.data[index]) * 100 / total) : 0
})
const data1Precentage = percentage(0)
const data2Precentage = percentage(1)

</script>

<style lang="scss" scoped>
.main-box {
  padding: 10px;
  display: flex;
  align-items: center;
  justify-content: space-around;
  flex-wrap: wrap;
  gap: 12px;
}

.flex-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 14px;
  font-weight: 600;

  .percentage-value {
    margin-bottom: 5px;
  }
}
</style>
