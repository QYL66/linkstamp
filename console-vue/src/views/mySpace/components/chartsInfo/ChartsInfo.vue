<!-- LinkStamp changes: show actual region statistics without bundled map data. -->
<template>
  <el-dialog v-model="dialogVisible" :title="props.title" width="70%" :before-close="handleClose">
    <template #header>
      <div style="display: flex">
        <el-icon v-if="!isGroup" size="25" aria-hidden="true"><Link /></el-icon>
        <div style="display: flex; flex-direction: column; margin-left: 5px">
          <span style="font-size: 25px; line-height: 25px; font-weight: 550">{{
            props.title
          }}</span>
          <span v-if="!isGroup" style="margin-top: 5px; font-size: 15px">{{
            props.originUrl
          }}</span>
        </div>
      </div>
      <span v-if="isGroup" style="margin: 5px 0 0 5px">共{{ props.nums }}条短链接</span>
    </template>
    <div style="position: absolute; right: 30px; z-index: 999">
      <el-date-picker v-model="dateValue" :clearable="true" type="daterange" range-separator="To" start-placeholder="开始时间"
        end-placeholder="结束时间" value-format="YYYY-MM-DD" :shortcuts="shortcuts" :size="size" />
    </div>
    <!-- 具体展示内容 -->
    <el-tabs v-model="showPane">
      <!-- 切换， name用于确定展示哪个标签，和showPane对应 -->
      <el-tab-pane name="访问数据" label="访问数据">
        <!-- 数据图表 -->
        <div class="content-box scroll-box" style="height: calc(100vh - 280px); overflow: scroll">
          <!-- 访问曲线 -->
          <TitleContent class="chart-item" style="width: 800px" title="访问曲线" @onMounted="initLineChart">
            <template v-slot:titleButton>
              <div>
                <el-button @click="isLine = !isLine">切换为曲线</el-button>
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
                <div v-show="isLine" class="lineChart"></div>
                <!-- 表格 -->
                <div v-show="!isLine" style="padding: 20px">
                  <el-table :data="visitsData" border style="width: 100%; height: 210px; overflow: scroll"
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
          <TitleContent class="chart-item" style="width: 800px" title="访问地区">
            <template #content>
              <el-table :data="localeRows" max-height="245" empty-text="所选日期内没有地区数据">
                <el-table-column prop="locale" label="地区" />
                <el-table-column prop="cnt" label="访问次数" />
                <el-table-column label="占比">
                  <template #default="{ row }">{{ (row.ratio * 100).toFixed(2) }}%</template>
                </el-table-column>
              </el-table>
            </template>
          </TitleContent>
          <!-- 24小时分布 -->
          <TitleContent class="chart-item" title="24小时分布" style="width: 800px">
            <template #content>
              <BarChart style="height: 100%; width: 100%" :chartData="{
                xAxis: [
                  0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
                  22, 23
                ],
                value: props.info?.hourStats || new Array(24).fill(0)
              }"></BarChart>
            </template>
          </TitleContent>
          <!-- 高频访问IP -->
          <TitleContent class="chart-item" title="高频访问IP" style="width: 390px">
            <template #content>
              <KeyValue :dataLists="props.info?.topIpStats" style="height: 100%; width: 100%"></KeyValue>
            </template>
          </TitleContent>
          <!-- 一周分布 -->
          <TitleContent class="chart-item" title="一周分布" style="width: 390px">
            <template #content>
              <BarChart style="height: 100%; width: 100%" :chartData="{
                xAxis: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
                value: props.info?.weekdayStats || new Array(7).fill(0)
              }"></BarChart>
            </template>
          </TitleContent>

          <!-- 访问来源TOP5 -->
          <!-- <TitleContent class="chart-item" title="访问来源 TOP5" style="width: 390px">
            <template #content>
              <KeyValue :data-lists="IPdataList" style="height: 100%; width: 100%;"></KeyValue>
            </template>
          </TitleContent> -->
          <!-- 操作系统 -->
          <TitleContent class="chart-item" title="操作系统" style="width: 390px">
            <template #content>
              <ProgressLine style="height: 100%; width: 100%" :dataLists="props.info?.osStats"></ProgressLine>
            </template>
          </TitleContent>
          <!-- 访问浏览器 -->
          <TitleContent class="chart-item" title="访问浏览器" style="width: 390px">
            <template #content>
              <ProgressLine style="height: 100%; width: 100%" :dataLists="props.info?.browserStats"></ProgressLine>
            </template>
          </TitleContent>
          <!-- 访客类型 -->
          <TitleContent v-if="!isGroup" class="chart-item" title="访客类型" style="width: 390px">
            <template #content>
              <ProgressPie style="height: 100%; width: 100%" :labels="['新访客', '旧访客']" :data="userTypeList"></ProgressPie>
            </template>
          </TitleContent>
          <!-- 访问网络 -->
          <TitleContent class="chart-item" title="访问网络" style="width: 390px">
            <template #content>
              <ProgressPie style="height: 100%; width: 100%" :labels="['WIFI', '移动数据']" :data="netWorkList"></ProgressPie>
            </template>
          </TitleContent>
          <!-- 访问设备 -->
          <TitleContent class="chart-item" title="访问设备" style="width: 390px">
            <template #content>
              <ProgressPie style="height: 100%; width: 100%" :labels="['电脑', '移动设备']" :data="deviceList"></ProgressPie>
            </template>
          </TitleContent>
        </div>
      </el-tab-pane>
      <el-tab-pane name="历史记录" label="历史记录">
        <el-table :data="tableInfo?.data?.data?.records" style="width: 100%; height: calc(100vh - 300px)">
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
            @size-change="handleSizeChange" @current-change="handleCurrentChange" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch, reactive } from 'vue'
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
const dailyXAxis = ref()
// const dailyXAxis = ref([])
const showPane = ref('访问数据')
const dateValue = ref([getLastWeekFormatDate(), getTodayFormatDate()])
const emit = defineEmits(['changeTime', 'changePage'])
watch(
  () => dateValue.value,
  (newValue) => {
    // 解决首次关闭数据统计页面需要点两次关闭键的bug
    if (!newValue && !dialogVisible.value) {
      return
    }
    emit('changeTime', newValue)
  }
)
const props = defineProps({
  title: {
    type: String,
    default: '默认标题'
  },
  info: Object,
  tableInfo: Object,
  isGroup: Boolean,
  nums: Number,
  originUrl: String
})
const pageParams = reactive({
  current: 1,
  size: 10
})
const totalNums = ref(0)
watch(
  () => props.tableInfo,
  () => {
    totalNums.value = props?.tableInfo?.data?.data?.total
  }
)
watch(
  () => pageParams,
  (newValue) => {
    // 解决首次关闭数据统计页面需要点两次关闭键的bug
    if (!newValue && !dialogVisible.value) {
      return
    }
    emit('changePage', newValue)
  },
  {
    deep: true
  }
)
// const title = ref(props.title)
// const info = ref(props.info)

// watch(
//   () => {
//     props.info
//   },
//   () => {
//     console.log(props.info, props.title)
//     info.value = props.info
//     title.value = props.title
//   },
//   {
//     deep: true,
//   }
// )
const dialogVisible = ref(false)
const handleClose = () => {
  dateValue.value = null
  unVisible()
  showPane.value = '访问数据'
  dateValue.value = [getLastWeekFormatDate(), getTodayFormatDate()]
  document.querySelector('.scroll-box').scrollTop = 0
}
const isVisible = () => {
  dialogVisible.value = true
}
const unVisible = () => {
  dialogVisible.value = false
}
defineExpose({
  unVisible,
  isVisible
})

const localeRows = computed(() => [...(props.info?.localeCnStats ?? [])]
  .sort((left, right) => right.cnt - left.cnt)
  .slice(0, 10))

// 访问次数相关
const isLine = ref(true)
// 访问曲线数据请求
const initLineChart = () => {
  const lineChartDom = document.querySelector('.lineChart')
  const lineChart = echarts.init(lineChartDom)
  let option = {
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
// 总访问次数
const totalPv = ref(0)
// 访问次数数据集数组
const pvList = ref([])
// 总访问人数
const totalUv = ref(0)
// 访问人数数据集数组
const uvList = ref([])
// 总访问IP数
const totalUip = ref(0)
// 访问IP数数据集数组
const uipList = ref([])
// 更新数据
watch(
  () => props?.info?.daily,
  () => {
    // 归零
    totalPv.value = 0
    totalUv.value = 0
    totalUip.value = 0
    pvList.value = []
    uvList.value = []
    uipList.value = []
    dailyXAxis.value = []
    visitsData.value = props?.info?.daily
    // 获取总数量和数据集数组
    visitsData?.value?.forEach((item) => {
      const { pv, uv, uip, date } = item
      const formDate = date.split('-')[1] + '月' + date.split('-')[2] + '日'
      totalPv.value += pv
      totalUv.value += uv
      totalUip.value += uip
      pvList.value.push(pv)
      uvList.value.push(uv)
      uipList.value.push(uip)
      dailyXAxis.value.push(formDate)
    })
    initLineChart()
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
      if (item.device === 'Mobile') {
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

<style lang="less" scoped>
.content-box {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  width: 100%;

  .chart-item {
    height: 300px;
    min-width: 300px;
    margin: 10px;
  }
}



.list-chart {
  display: flex;
  justify-content: space-between;

  .top10 {
    padding: 15px 30px;
    width: 400px;
    height: 270px;
    display: flex;
    flex-direction: column;
    overflow-y: auto;

    .top-item {
      display: flex;
      flex-direction: column;
      flex-wrap: wrap;

      div {
        height: 40px;
        display: flex;
        align-items: center;
        margin-right: 30px;
      }
    }

    span:nth-child(1) {
      color: #3464e0;
      font-size: 12px;
      font-weight: 700;
    }

    .key-value {
      display: flex;
      justify-content: space-between;
      width: 150px;
    }
  }
}

.lineChart {
  margin: 10px;
  width: 600px;
  height: 200px;
}

.flex-box {
  display: flex;
  justify-content: space-around;
}

.pagination-block {
  .el-pagination {
    margin-left: 20%;
  }
}
</style>
