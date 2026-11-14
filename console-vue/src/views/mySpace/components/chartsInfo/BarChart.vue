<template>
  <!-- 柱状图 -->
  <div class="barChart" ref="barChartRef">
  </div>
</template>

<script setup>
import * as echarts from 'echarts'
import { onMounted, onUnmounted, nextTick, ref, watch } from 'vue'
const props = defineProps({
  chartData: {
    type: Object,
    // eslint-disable-next-line vue/require-valid-default-prop
    default: {
      xAxis: [1, 2, 3, 4, 5, 6, 7],
      value: [12, 12, 32, 32, 23, 23, 16]
    }
  }
})
watch(
  () => props.chartData,
  () => {
    initChart()
  }
)
const barChartRef = ref()
const initChart = () => {
  const barDom = barChartRef.value
  if (!barDom?.clientWidth) return
  const barChart = echarts.getInstanceByDom(barDom) || echarts.init(barDom)
  barChart.resize()
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '5%',
      right: '5%',
      bottom: '5%',
      top: '13%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      axisLine: {
        show: false
      },
      axisTick: {
        show: false
      },
      axisLabel: {
        color: '#ababab'
      },
      data: props.chartData.xAxis
    },
    yAxis: {
      axisLabel: {
        color: '#ababab'
      },
      axisLine: {
        show: false
      },
      axisTick: {
        show: false
      },
      type: 'value'
    },
    series: [
      {
        data: props.chartData.value,
        type: 'bar',
        barWidth: '10px',
        itemStyle: {
          color: '#526ed4', borderRadius: [3, 3, 0, 0]
        },
        showBackground: true,
        backgroundStyle: {
          color: 'rgba(180, 180, 180, 0.2)'
        }
      }
    ]
  };
  barChart.setOption(option)
}
let observer
onMounted(() => {
  observer = new ResizeObserver(initChart)
  observer.observe(barChartRef.value)
  void nextTick(initChart)
})
onUnmounted(() => {
  observer?.disconnect()
  if (barChartRef.value) echarts.getInstanceByDom(barChartRef.value)?.dispose()
})
</script>

<style lang="scss" scoped>
.barChart {
  width: 100%;
  height: 185px;
}
</style>
