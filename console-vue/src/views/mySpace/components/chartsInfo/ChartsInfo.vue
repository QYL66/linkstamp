<!-- LinkStamp changes: show actual region statistics without bundled map data. -->
<template>
  <el-dialog v-model="dialogVisible" :title="props.title" width="960px" destroy-on-close @closed="resetView">
    <div class="stats-toolbar">
      <p>{{ isGroup ? '共 ' + props.nums + ' 条短链' : props.originUrl }}</p>
      <el-date-picker v-model="dateValue" type="daterange" range-separator="至" start-placeholder="开始日期"
        end-placeholder="结束日期" value-format="YYYY-MM-DD" :shortcuts="shortcuts" @change="updateDates" />
    </div>

    <el-tabs v-model="showPane">
      <!-- 切换， name用于确定展示哪个标签，和showPane对应 -->
      <el-tab-pane name="访问数据" label="访问数据">
        <!-- 数据图表 -->
        <div class="stats-grid">
          <!-- 访问曲线 -->
          <TitleContent class="chart-item chart-wide" title="访问趋势">
            <template v-slot:titleButton>
              <div>
                <el-button @click="isLine = !isLine">{{ isLine ? '查看表格' : '查看曲线' }}</el-button>
              </div>
            </template>
            <template #content>
              <div class="list-chart">
                <div v-show="isLine" class="top10" style="padding-top: 20px">
                  <div class="key-value" style="margin-top: 10px">
                    <span>访问次数</span>
                    <span>{{ totalPv }}</span>
                  </div>
                  <div class="key-value" style="margin-top: 10px">
                    <span>访问人数</span>
                    <span>{{ totalUv }}</span>
                  </div>
                  <div class="key-value" style="margin-top: 10px">
                    <span>访问IP数</span>
                    <span>{{ totalUip }}</span>
                  </div>
                </div>
                <!-- 折线图 -->
                <div v-show="isLine" ref="lineChartEl" class="line-chart"></div>
                <!-- 表格 -->
                <div v-show="!isLine" style="padding: 12px; grid-column: 1 / -1">
                  <el-table :data="visitsData" border max-height="240"
                    :header-cell-style="{ background: '#eef1f6', color: '#606266' }">
                    <el-table-column prop="date" label="时间" width="160" />
                    <el-table-column prop="pv" label="访问次数" width="160" />
                    <el-table-column prop="uv" label="访问人数" width="160" />
                    <el-table-column prop="uip" label="访问IP数" width="160" />
                  </el-table>
                </div>
              </div>
            </template>
          </TitleContent>
          <TitleContent class="chart-item" title="访问地区">
            <template #content>
              <el-table :data="localeRows" max-height="245" empty-text="当前没有可展示的地区数据">
                <el-table-column prop="locale" label="地区" />
                <el-table-column prop="cnt" label="访问次数" />
                <el-table-column label="占比">
                  <template #default="{ row }">{{ (row.ratio * 100).toFixed(2) }}%</template>
                </el-table-column>
              </el-table>
            </template>
          </TitleContent>
          <!-- 24小时分布 -->
          <TitleContent class="chart-item" title="24小时分布">
            <template #content>
              <BarChart :chartData="{
                xAxis: [
                  0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
                  22, 23
                ],
                value: props.info?.hourStats || new Array(24).fill(0)
              }"></BarChart>
            </template>
          </TitleContent>
          <!-- 高频访问IP -->
          <TitleContent class="chart-item" title="高频访问IP">
            <template #content>
              <KeyValue :dataLists="props.info?.topIpStats" style="height: 100%; width: 100%"></KeyValue>
            </template>
          </TitleContent>
          <!-- 一周分布 -->
          <TitleContent class="chart-item" title="一周分布">
            <template #content>
              <BarChart :chartData="{
                xAxis: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
                value: props.info?.weekdayStats || new Array(7).fill(0)
              }"></BarChart>
            </template>
          </TitleContent>

          <!-- 操作系统 -->
          <TitleContent class="chart-item" title="操作系统">
            <template #content>
              <ProgressLine style="height: 100%; width: 100%" :dataLists="props.info?.osStats"></ProgressLine>
            </template>
          </TitleContent>
          <!-- 访问浏览器 -->
          <TitleContent class="chart-item" title="访问浏览器">
            <template #content>
              <ProgressLine style="height: 100%; width: 100%" :dataLists="props.info?.browserStats"></ProgressLine>
            </template>
          </TitleContent>
          <!-- 访客类型 -->
          <TitleContent v-if="!isGroup" class="chart-item" title="访客类型">
            <template #content>
              <ProgressPie style="height: 100%; width: 100%" :labels="['新访客', '旧访客']" :data="userTypeList"></ProgressPie>
            </template>
          </TitleContent>
          <!-- 访问网络 -->
          <TitleContent class="chart-item" title="访问网络">
            <template #content>
              <ProgressPie style="height: 100%; width: 100%" :labels="['WIFI', '移动数据']" :data="netWorkList"></ProgressPie>
            </template>
          </TitleContent>
          <!-- 访问设备 -->
          <TitleContent class="chart-item" title="访问设备">
            <template #content>
              <ProgressPie style="height: 100%; width: 100%" :labels="['电脑', '移动设备']" :data="deviceList"></ProgressPie>
            </template>
          </TitleContent>
        </div>
      </el-tab-pane>
      <el-tab-pane name="历史记录" label="历史记录">
        <el-table :data="tableInfo?.data?.data?.records ?? []" max-height="480">
          <el-table-column prop="createTime" label="访问时间" width="160" />
          <el-table-column prop="ip" label="访问IP" width="140" />
          <el-table-column prop="locale" label="访客地区"> </el-table-column>
          <el-table-column prop="device" label="设备信息" min-width="220">
            <template #default="scope">
              <div class="flex-box">
                {{ [scope.row.browser, scope.row.os, scope.row.device, scope.row.network].filter(Boolean).join(' · ') }}
              </div>
            </template>
          </el-table-column>

          <el-table-column v-if="!isGroup" prop="uvType" label="访客类型" />
        </el-table>
        <!-- 分页器 -->
        <div class="pagination-block">
          <el-pagination v-model:current-page="pageParams.current" v-model:page-size="pageParams.size"
            :page-sizes="[10, 15, 20, 30]" layout="total, sizes, prev, pager, next, jumper" :total="totalNums"
            @size-change="updatePageSize" @current-change="updatePage" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, ref, watch, reactive } from 'vue'
import TitleContent from './TitleContent.vue'
import * as echarts from 'echarts'
import BarChart from './BarChart.vue'
import KeyValue from './KeyValue.vue'
import ProgressLine from './ProgressLine.vue'
import ProgressPie from './ProgressPie.vue'
import { getTodayFormatDate, getLastWeekFormatDate } from '@/utils/plugins.js'

// 选择时间
const shortcuts = [
  {
    text: '今天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 0)
      return [start, end]
    }
  },
  {
    text: '昨天',
    value: () => {
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 1)
      return [start, start]
    }
  },
  {
    text: '近七天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 7)
      return [start, end]
    }
  },
  {
    text: '近三十天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 30)
      return [start, end]
    }
  }
]
const dailyXAxis = ref([])
const showPane = ref('访问数据')
const defaultRange = () => [getLastWeekFormatDate().slice(0, 10), getTodayFormatDate().slice(0, 10)]
const dateValue = ref(defaultRange())
const emit = defineEmits(['changeTime', 'changePage'])
const props = defineProps({
  title: String,
  info: Object,
  tableInfo: Object,
  isGroup: Boolean,
  nums: Number,
  originUrl: String
})
const pageParams = reactive({ current: 1, size: 10 })
const totalNums = computed(() => Number(props.tableInfo?.data?.data?.total ?? 0))
const dialogVisible = ref(false)

function updateDates(dates) {
  pageParams.current = 1
  if (!dates) dateValue.value = defaultRange()
  emit('changeTime', dateValue.value)
}
function updatePage() {
  if (dialogVisible.value) emit('changePage', { ...pageParams })
}
function updatePageSize() {
  pageParams.current = 1
  updatePage()
}
function resetView() {
  dateValue.value = defaultRange()
  pageParams.current = 1
  pageParams.size = 10
  showPane.value = '访问数据'
  isLine.value = true
}
function isVisible() {
  dialogVisible.value = true
}
defineExpose({ isVisible })

const localeRows = computed(() => [...(props.info?.localeCnStats ?? [])]
  .sort((left, right) => right.cnt - left.cnt)
  .slice(0, 10))

// 访问次数相关
const isLine = ref(true)
// 访问曲线数据请求
const initLineChart = () => {
  const element = lineChartEl.value
  if (!element || !element.clientWidth || !isLine.value) return
  const lineChart = echarts.getInstanceByDom(element) || echarts.init(element)
  lineChart.resize()
  let option = {
    color: ['#254bc3', '#7989bd', '#96623f'],
    title: {
      show: false,
      text: 'Stacked Line'
    },
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['访问次数', '访问人数', '访问IP数']
    },
    grid: {
      left: '3%',
      right: '9%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dailyXAxis.value
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '访问次数',
        type: 'line',
        data: pvList.value
      },
      {
        name: '访问人数',
        type: 'line',
        data: uvList.value
      },
      {
        name: '访问IP数',
        type: 'line',
        data: uipList.value
      }
    ]
  }
  lineChart.setOption(option)
}
const lineChartEl = ref()
watch(lineChartEl, (element, previous, onCleanup) => {
  if (!element) return
  const observer = new ResizeObserver(initLineChart)
  observer.observe(element)
  onCleanup(() => {
    observer.disconnect()
    echarts.getInstanceByDom(element)?.dispose()
  })
})
watch([isLine, showPane], () => nextTick(initLineChart))

// 总访问次数
const totalPv = computed(() => props.info?.pv ?? 0)
// 访问次数数据集数组
const pvList = ref([])
// 总访问人数
const totalUv = computed(() => props.info?.uv ?? 0)
// 访问人数数据集数组
const uvList = ref([])
// 总访问IP数
const totalUip = computed(() => props.info?.uip ?? 0)
// 访问IP数数据集数组
const uipList = ref([])
// 更新数据
watch(
  () => props?.info?.daily,
  () => {
    // 归零
    pvList.value = []
    uvList.value = []
    uipList.value = []
    dailyXAxis.value = []
    visitsData.value = props?.info?.daily
    // 获取总数量和数据集数组
    visitsData?.value?.forEach((item) => {
      const { pv, uv, uip, date } = item
      const formDate = date.split('-')[1] + '月' + date.split('-')[2] + '日'
      pvList.value.push(pv)
      uvList.value.push(uv)
      uipList.value.push(uip)
      dailyXAxis.value.push(formDate)
    })
    void nextTick(initLineChart)
  }
)
// 访问表格数据
const visitsData = ref()
// 访问设备和访问网络
const userTypeList = ref([0, 0])
const deviceList = ref([0, 0])
const netWorkList = ref([0, 0])
watch(
  () => props.info?.uvTypeStats,
  () => {
    // 初始化
    userTypeList.value = [0, 0]
    // 对访问用户类型的数据进行转化
    props.info?.uvTypeStats?.forEach((item) => {
      if (item.uvType === 'newUser') {
        userTypeList.value[0] = item.cnt
      } else if (item.uvType === 'oldUser') {
        userTypeList.value[1] = item.cnt
      }
    })
  },
  {
    immediate: true
  }
)
watch(
  () => props.info?.deviceStats,
  () => {
    deviceList.value = [0, 0]
    // 对访问设备类型的数据进行转化
    props.info?.deviceStats?.forEach((item) => {
      if (item.device === 'Mobile') {
        deviceList.value[1] = item.cnt
      } else {
        deviceList.value[0] = item.cnt
      }
    })
  },
  {
    immediate: true
  }
)
watch(
  () => props.info?.networkStats,
  () => {
    netWorkList.value = [0, 0]
    // 对访问设备类型的数据进行转化
    props.info?.networkStats?.forEach((item) => {
      if (item.network === 'Mobile') {
        netWorkList.value[1] = item.cnt
      } else {
        netWorkList.value[0] = item.cnt
      }
    })
  },
  {
    immediate: true
  }
)
</script>

<style scoped>
.stats-toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; align-items: center; margin-bottom: 10px; }
.stats-toolbar p { color: var(--muted); overflow-wrap: anywhere; }
.stats-toolbar :deep(.el-date-editor) { max-width: 100%; }
.stats-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; max-height: 62dvh; overflow: auto; padding: 2px 5px 5px 2px; }
.chart-item { min-width: 0; min-height: 175px; }
.chart-wide { grid-column: 1 / -1; }
.list-chart { display: grid; grid-template-columns: 150px minmax(0, 1fr); align-items: stretch; }
.top10 { padding: 10px 13px; font-variant-numeric: tabular-nums; }
.key-value { display: flex; justify-content: space-between; gap: 8px; color: var(--muted); }
.key-value span + span { color: var(--ink); font-weight: 700; }
.line-chart { width: 100%; min-width: 0; height: 215px; }
.pagination-block { margin-top: 12px; overflow-x: auto; }
@media (max-width: 640px) {
  .stats-grid { grid-template-columns: minmax(0, 1fr); }
  .list-chart { grid-template-columns: minmax(0, 1fr); }
  .top10 { display: flex; flex-wrap: wrap; gap: 10px; }
}
</style>
