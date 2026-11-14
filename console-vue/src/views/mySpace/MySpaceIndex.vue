<!-- LinkStamp changes: redesign the short-link workspace and improve request states. -->
<template>
  <div class="console-page">
    <header class="page-heading">
      <div class="page-title">
        <h1>短链管理</h1>
        <p v-if="isRecycleBin">查看已移除的短链</p>
        <p v-else-if="activeGroup">当前分组 <strong>{{ activeGroup.name }}</strong></p>
      </div>
      <div v-if="!isRecycleBin" class="page-actions">
        <el-button :disabled="!activeGroup" @click="isAddSmallLinks = true"><el-icon><Upload /></el-icon> 批量创建</el-button>
        <el-button type="primary" :disabled="!activeGroup" @click="isAddSmallLink = true"><el-icon><Plus /></el-icon> 创建短链</el-button>
      </div>
    </header>
    <dl v-if="!isRecycleBin && activeGroup" class="overview" aria-label="分组概览">
      <div>
        <dt><el-icon><Link /></el-icon> 分组内短链</dt>
        <dd>{{ formatMetric(activeGroup.shortLinkCount ?? 0) }}</dd>
      </div>
      <div>
        <dt><el-icon><Histogram /></el-icon> 近 7 天访问次数</dt>
        <dd>{{ formatMetric(overview?.pv) }}</dd>
      </div>
      <div>
        <dt><el-icon><User /></el-icon> 近 7 天访客数</dt>
        <dd>{{ formatMetric(overview?.uv) }}</dd>
      </div>
    </dl>
    <div class="console-grid">
      <aside class="group-panel" aria-label="短链分组">
        <div class="group-heading">
          <h2>短链分组</h2>
          <button class="icon-button" type="button" aria-label="新建分组" title="新建分组" @click="showAddGroup"><el-icon><Plus /></el-icon></button>
        </div>
        <ul ref="groupList" class="group-list">
          <li v-for="item in editableTabs" :key="item.gid" :class="{ selected: !isRecycleBin && activeGid === item.gid }">
            <span class="drag-handle" title="拖动排序" aria-hidden="true"><el-icon><Rank /></el-icon></span>
            <button type="button" class="group-button" :aria-current="!isRecycleBin && activeGid === item.gid ? 'true' : undefined" @click="selectGroup(item.gid)">
              <el-icon><Folder /></el-icon>
              <span>{{ item.name }}</span>
              <small>{{ item.shortLinkCount ?? 0 }}</small>
            </button>
            <el-dropdown trigger="click">
              <button class="icon-button group-menu" type="button" :aria-label="item.name + '的分组操作'"><el-icon><MoreFilled /></el-icon></button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="chartsVisible({ describe: item.name, gid: item.gid, group: true })">分组统计</el-dropdown-item>
                  <el-dropdown-item @click="showEditGroup(item.gid, item.name)">编辑分组</el-dropdown-item>
                  <el-dropdown-item divided @click="deleteGroup(item)">删除分组</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </li>
        </ul>
        <button type="button" class="recycle-button" :class="{ selected: isRecycleBin }" :aria-current="isRecycleBin ? 'page' : undefined" @click="openRecycleBin">
          <el-icon><Delete /></el-icon> 回收站
        </button>
      </aside>
      <section class="link-panel" aria-labelledby="link-section-title">
        <div class="link-heading">
          <div>
            <h2 id="link-section-title">{{ isRecycleBin ? '回收站' : activeGroup?.name || '短链接' }}</h2>
            <p v-if="isRecycleBin">恢复短链，或将它从回收站移除。</p>
            <p v-else>{{ totalNums }} 条短链</p>
          </div>
          <div v-if="!isRecycleBin && activeGroup" class="list-tools">
            <el-button @click="chartsVisible({ describe: activeGroup.name, gid: activeGid, group: true })"><el-icon><Histogram /></el-icon> 分组统计</el-button>
            <el-select v-model="pageParams.orderTag" class="sort-select" aria-label="短链排序" @change="changeSort">
              <el-option v-for="option in sortOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </div>
        </div>
        <el-skeleton v-if="loading && !tableData.length" class="table-loading" :rows="4" animated />
        <div v-else-if="pageError" class="empty-state" role="alert">
          <el-icon><Warning /></el-icon>
          <strong>短链加载失败</strong>
          <p>{{ pageError }}</p>
          <el-button @click="loadWorkspace">重试</el-button>
        </div>
        <div v-else-if="!tableData.length" class="empty-state">
          <el-icon><Link /></el-icon>
          <strong>{{ isRecycleBin ? '回收站是空的' : '这里还没有短链接' }}</strong>
          <p>{{ isRecycleBin ? '移入回收站的链接会出现在这里。' : '创建短链接后，就可以分享并查看访问数据。' }}</p>
          <el-button v-if="!isRecycleBin" type="primary" @click="activeGroup ? isAddSmallLink = true : showAddGroup()">
            {{ activeGroup ? '创建短链' : '新建分组' }}
          </el-button>
        </div>
        <el-table v-else :data="tableData" v-loading="loading" class="link-table" aria-label="短链接列表">
          <el-table-column label="短链与目标网址" min-width="270">
            <template #default="{ row }">
              <div class="link-cell">
                <span class="link-icon"><el-icon><Link /></el-icon></span>
                <div class="link-text">
                  <strong :title="row.describe">{{ row.describe || row.shortUri }}</strong>
                  <a v-if="!isRecycleBin && !isExpired(row)" :href="shortUrl(row)" target="_blank" rel="noopener noreferrer" :title="shortUrl(row)">{{ row.fullShortUrl }} ↗</a>
                  <span v-else class="inactive-link">{{ row.fullShortUrl }}</span>
                  <span class="origin-url" :title="row.originUrl">{{ row.originUrl }}</span>
                </div>
              </div>
              <div v-if="compact" class="compact-actions">
                <div class="compact-metrics">
                  <span class="status" :class="{ inactive: isRecycleBin || isExpired(row) }">{{ linkStatus(row) }}</span>
                  <span>访问 {{ row.totalPv ?? 0 }} · 访客 {{ row.totalUv ?? 0 }} · IP {{ row.totalUip ?? 0 }}</span>
                </div>
                <LinkActions :url="shortUrl(row)" :is-recycle-bin="isRecycleBin"
                @stats="chartsVisible(row)" @copy="copyUrl(shortUrl(row))" @edit="editLink(row)"
                @trash="applyLinkAction(row, 'toRecycleBin', '已移入回收站')"
                @restore="applyLinkAction(row, 'recoverLink', '已恢复短链')"
                @remove="applyLinkAction(row, 'removeLink', '已删除短链')" />
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="!compact" label="状态" width="88">
            <template #default="{ row }">
              <span class="status" :class="{ inactive: isRecycleBin || isExpired(row) }">{{ linkStatus(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="!compact" label="访问次数" width="101">
            <template #default="{ row }"><div class="metric"><strong>{{ row.totalPv ?? 0 }}</strong><small>今日 {{ row.todayPv ?? 0 }}</small></div></template>
          </el-table-column>
          <el-table-column v-if="!compact" label="访客数" width="93">
            <template #default="{ row }"><div class="metric"><strong>{{ row.totalUv ?? 0 }}</strong><small>今日 {{ row.todayUv ?? 0 }}</small></div></template>
          </el-table-column>
          <el-table-column v-if="!compact" label="独立 IP" width="93">
            <template #default="{ row }"><div class="metric"><strong>{{ row.totalUip ?? 0 }}</strong><small>今日 {{ row.todayUip ?? 0 }}</small></div></template>
          </el-table-column>
          <el-table-column v-if="!compact" label="操作" width="194">
            <template #default="{ row }">
              <LinkActions :url="shortUrl(row)" :is-recycle-bin="isRecycleBin"
                @stats="chartsVisible(row)" @copy="copyUrl(shortUrl(row))" @edit="editLink(row)"
                @trash="applyLinkAction(row, 'toRecycleBin', '已移入回收站')"
                @restore="applyLinkAction(row, 'recoverLink', '已恢复短链')"
                @remove="applyLinkAction(row, 'removeLink', '已删除短链')" />
            </template>
          </el-table-column>
        </el-table>
        <div v-if="totalNums && !pageError" class="pagination-block">
          <el-pagination v-model:current-page="pageParams.current" v-model:page-size="pageParams.size"
            :page-sizes="[10, 15, 20, 30]" layout="total, sizes, prev, pager, next" :total="totalNums"
            @size-change="changeSort" @current-change="loadPage" />
        </div>
      </section>
    </div>
    <ChartsInfo ref="chartsInfoRef" :title="chartsInfoTitle" :info="chartsInfo" :tableInfo="tableInfo"
      :isGroup="isGroup" :nums="statsGroupCount" :originUrl="originUrl1"
      @changeTime="changeTime" @changePage="changePage" />
    <el-dialog v-model="groupDialog" :title="editGid ? '编辑分组' : '新建分组'" width="420px">
      <el-form label-position="top" @submit.prevent="saveGroup">
        <el-form-item label="分组名称">
          <el-input v-model.trim="groupName" maxlength="64" autocomplete="off" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialog = false">取消</el-button>
        <el-button type="primary" :loading="groupSaving" :disabled="!groupName" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="isAddSmallLink" title="创建链接" width="630px" destroy-on-close>
      <CreateLink :groupInfo="editableTabs" :defaultGid="activeGid" :is-single="true" @onSubmit="linkCreated" @cancel="isAddSmallLink = false" />
    </el-dialog>
    <el-dialog v-model="isEditLink" title="编辑链接" width="630px" destroy-on-close>
      <EditLink :editData="editData" :groupInfo="editableTabs" @onSubmit="isEditLink = false" @updatePage="loadWorkspace" @cancel="isEditLink = false" />
    </el-dialog>
    <el-dialog v-model="isAddSmallLinks" title="批量创建" width="680px" destroy-on-close>
      <CreateLinks :groupInfo="editableTabs" :defaultGid="activeGid" @onSubmit="linkCreated" @cancel="isAddSmallLinks = false" />
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Sortable from 'sortablejs'
import { ElMessage, ElMessageBox } from 'element-plus'
import API from '@/api'
import { getTodayFormatDate, getLastWeekFormatDate } from '@/utils/plugins.js'
import ChartsInfo from './components/chartsInfo/ChartsInfo.vue'
import CreateLink from './components/createLink/CreateLink.vue'
import CreateLinks from './components/createLink/CreateLinks.vue'
import EditLink from './components/editLink/EditLink.vue'
import LinkActions from './components/LinkActions.vue'

const route = useRoute()
const router = useRouter()
const groupList = ref()
const smallScreen = window.matchMedia('(max-width: 680px)')
const compact = ref(smallScreen.matches)
const syncScreen = (event) => { compact.value = event.matches }
const editableTabs = ref([])
const activeGid = ref('')
const activeGroup = computed(() => editableTabs.value.find((group) => group.gid === activeGid.value))
const isRecycleBin = computed(() => route.query.view === 'recycle')
const overview = ref()
const numberFormat = new Intl.NumberFormat('zh-CN')
const formatMetric = (value) => value == null ? '—' : numberFormat.format(value)
const tableData = ref([])
const totalNums = ref(0)
const loading = ref(true)
const pageError = ref('')
const pageParams = reactive({ current: 1, size: 15, orderTag: 'created' })
const sortOptions = [
  { value: 'created', label: '最新创建' },
  { value: 'totalPv', label: '累计访问量' },
  { value: 'todayPv', label: '今日访问量' },
  { value: 'totalUv', label: '累计访客数' },
  { value: 'todayUv', label: '今日访客数' },
  { value: 'totalUip', label: '累计独立 IP' },
  { value: 'todayUip', label: '今日独立 IP' }
]
let pageRequest = 0
let sortable

function unwrap(response) {
  if (response.data?.code !== '0') throw new Error(response.data?.message || '请求失败，请重试')
  return response.data.data
}

watch([activeGroup, isRecycleBin], ([group, recycling], _previous, onCleanup) => {
  overview.value = undefined
  if (!group || recycling) return
  let current = true
  onCleanup(() => { current = false })
  void API.group.queryGroupStats({ gid: group.gid, startDate: getLastWeekFormatDate(), endDate: getTodayFormatDate() })
    .then((response) => {
      if (current) overview.value = unwrap(response) ?? { pv: 0, uv: 0 }
    })
    .catch(() => {
      if (current) overview.value = undefined
    })
})

async function loadWorkspace() {
  loading.value = true
  pageError.value = ''
  try {
    editableTabs.value = (unwrap(await API.group.queryGroup()) ?? []).slice().reverse()
    if (!activeGroup.value) activeGid.value = editableTabs.value[0]?.gid ?? ''
    await loadPage()
  } catch (error) {
    pageError.value = error.message
    loading.value = false
  }
}

async function loadPage() {
  const request = ++pageRequest
  loading.value = true
  pageError.value = ''
  try {
    let page = {}
    const { current, size, orderTag } = pageParams
    if (isRecycleBin.value) {
      page = unwrap(await API.smallLinkPage.queryRecycleBin({ current, size })) ?? {}
    } else if (activeGid.value) {
      page = unwrap(await API.smallLinkPage.queryPage({
        gid: activeGid.value, current, size, orderTag: orderTag === 'created' ? undefined : orderTag
      })) ?? {}
    }
    if (request !== pageRequest) return
    const total = Number(page.total ?? 0)
    const lastPage = Math.max(1, Math.ceil(total / size))
    if (current > lastPage) {
      pageParams.current = lastPage
      return loadPage()
    }
    tableData.value = page.records ?? []
    totalNums.value = total
  } catch (error) {
    if (request === pageRequest) pageError.value = error.message
  } finally {
    if (request === pageRequest) loading.value = false
  }
}

function changeSort() {
  pageParams.current = 1
  return loadPage()
}

function selectGroup(gid) {
  if (activeGid.value === gid && !isRecycleBin.value) return
  activeGid.value = gid
  if (isRecycleBin.value) return router.push('/home/space')
  return changeSort()
}

function openRecycleBin() {
  return router.push('/home/space?view=recycle')
}

watch(isRecycleBin, changeSort)
onMounted(() => {
  smallScreen.addEventListener('change', syncScreen)
  void loadWorkspace()
  sortable = Sortable.create(groupList.value, {
    animation: 150,
    handle: '.drag-handle',
    async onEnd({ oldIndex, newIndex }) {
      if (oldIndex === newIndex || oldIndex == null || newIndex == null) return
      const previous = editableTabs.value
      const ordered = previous.slice()
      const [moved] = ordered.splice(oldIndex, 1)
      ordered.splice(newIndex, 0, moved)
      const item = groupList.value.children[newIndex]
      const anchor = groupList.value.children[oldIndex + (newIndex < oldIndex ? 1 : 0)]
      groupList.value.insertBefore(item, anchor ?? null)
      editableTabs.value = ordered
      try {
        unwrap(await API.group.sortGroup(ordered.map(({ gid }, sortOrder) => ({ gid, sortOrder }))))
      } catch (error) {
        editableTabs.value = previous
        ElMessage.error(error.message)
      }
    }
  })
})
onUnmounted(() => {
  smallScreen.removeEventListener('change', syncScreen)
  sortable?.destroy()
})

const groupDialog = ref(false)
const editGid = ref('')
const groupName = ref('')
const groupSaving = ref(false)

function showAddGroup() {
  showEditGroup('', '')
}

function showEditGroup(gid, name) {
  editGid.value = gid
  groupName.value = name
  groupDialog.value = true
}

async function saveGroup() {
  if (!groupName.value || groupSaving.value) return
  groupSaving.value = true
  try {
    const name = groupName.value
    unwrap(await (editGid.value ? API.group.editGroup({ gid: editGid.value, name }) : API.group.addGroup({ name })))
    groupDialog.value = false
    ElMessage.success('已保存分组')
    await loadWorkspace()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    groupSaving.value = false
  }
}

async function deleteGroup(group) {
  if (Number(group.shortLinkCount) > 0) {
    ElMessage.warning('请先移走该分组里的短链')
    return
  }
  try {
    await ElMessageBox.confirm('确定删除分组「' + group.name + '」？', '删除分组', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  try {
    unwrap(await API.group.deleteGroup({ gid: group.gid }))
    ElMessage.success('已删除分组')
    await loadWorkspace()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const isAddSmallLink = ref(false)
const isAddSmallLinks = ref(false)
const isEditLink = ref(false)
const editData = ref()

function linkCreated() {
  isAddSmallLink.value = false
  isAddSmallLinks.value = false
  return loadWorkspace()
}

function editLink(row) {
  editData.value = row
  isEditLink.value = true
}

async function applyLinkAction(row, method, message) {
  try {
    unwrap(await API.smallLinkPage[method]({ gid: row.gid, fullShortUrl: row.fullShortUrl }))
    ElMessage.success(message)
    await loadWorkspace()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const isExpired = (row) => row.validDateType === 1 && new Date(row.validDate).getTime() < Date.now()
const linkStatus = (row) => isRecycleBin.value ? '已移除' : isExpired(row) ? '已过期' : '有效'
const shortUrl = (row) => 'http://' + row.fullShortUrl

async function copyUrl(url) {
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('已复制短链')
  } catch {
    ElMessage.error('无法复制。请从短链地址手动复制。')
  }
}

const chartsInfoRef = ref()
const chartsInfoTitle = ref('')
const chartsInfo = ref()
const tableInfo = ref()
const originUrl1 = ref()
const statsTarget = ref()
const isGroup = computed(() => Boolean(statsTarget.value?.group))
const statsGroupCount = computed(() => editableTabs.value.find((group) => group.gid === statsTarget.value?.gid)?.shortLinkCount ?? 0)
const statsFormData = reactive({ startDate: '', endDate: '', size: 10, current: 1 })
let statsRequest = 0

function setStatsDates(dates) {
  statsFormData.startDate = dates?.[0] ? dates[0].slice(0, 10) + ' 00:00:00' : getLastWeekFormatDate()
  statsFormData.endDate = dates?.[1] ? dates[1].slice(0, 10) + ' 23:59:59' : getTodayFormatDate()
}

function statsParams() {
  const { gid, fullShortUrl, enableStatus } = statsTarget.value
  return { ...statsFormData, gid, fullShortUrl, enableStatus }
}

async function loadStats() {
  const request = ++statsRequest
  const params = statsParams()
  try {
    const [summary, history] = await (isGroup.value
      ? Promise.all([API.group.queryGroupStats(params), API.group.queryGroupTable(params)])
      : Promise.all([API.smallLinkPage.queryLinkStats(params), API.smallLinkPage.queryLinkTable(params)]))
    if (request !== statsRequest) return
    chartsInfo.value = unwrap(summary)
    unwrap(history)
    tableInfo.value = history
  } catch (error) {
    if (request === statsRequest) ElMessage.error(error.message)
  }
}

function chartsVisible(row) {
  statsTarget.value = row
  chartsInfoTitle.value = row.describe || row.shortUri || '访问统计'
  originUrl1.value = row.originUrl
  chartsInfo.value = undefined
  tableInfo.value = undefined
  statsFormData.current = 1
  statsFormData.size = 10
  setStatsDates()
  chartsInfoRef.value.isVisible()
  return loadStats()
}

function changeTime(dates) {
  if (!statsTarget.value) return
  statsFormData.current = 1
  setStatsDates(dates)
  return loadStats()
}

async function changePage({ current, size }) {
  if (!statsTarget.value) return
  const request = ++statsRequest
  statsFormData.current = current ?? 1
  statsFormData.size = size ?? 10
  const params = statsParams()
  try {
    const result = await (isGroup.value ? API.group.queryGroupTable(params) : API.smallLinkPage.queryLinkTable(params))
    if (request === statsRequest) {
      unwrap(result)
      tableInfo.value = result
    }
  } catch (error) {
    if (request === statsRequest) ElMessage.error(error.message)
  }
}
</script>

<style scoped>
.page-heading {
  position: relative;
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  padding: clamp(18px, 3vw, 36px);
  margin-bottom: 15px;
  border: 1px solid #d5d9e4;
  border-radius: 9px;
  background-color: #f9f8f2;
  background-image: linear-gradient(var(--grid-line) 1px, transparent 1px), linear-gradient(90deg, var(--grid-line) 1px, transparent 1px);
  background-size: 20px 20px;
}
.page-heading h1 { font-size: clamp(29px, 4vw, 52px); letter-spacing: -.045em; line-height: 1.1; }
.page-heading p { margin-top: 9px; color: var(--muted); }
.page-heading p strong { color: var(--accent); }
.overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--paper);
  margin: 0 0 18px;
}
.overview > div { padding: clamp(9px, 1.7vw, 17px) clamp(10px, 2.3vw, 25px); min-width: 0; }
.overview > div + div { border-left: 1px solid var(--line); }
.overview dt { display: flex; align-items: center; gap: 6px; color: var(--muted); font-weight: 550; line-height: 1.35; }
.overview dt .el-icon { color: var(--accent); }
.overview dd { margin: 5px 0 0; font: 650 clamp(19px, 2.6vw, 31px)/1.15 var(--mono); color: var(--ink); overflow-wrap: anywhere; }
.page-actions, .list-tools { display: flex; align-items: center; gap: 8px; }
.page-actions .el-button, .list-tools .el-button { margin: 0; }
.page-actions .el-button { height: 40px; padding: 0 15px; }
.page-actions .el-icon, .list-tools .el-icon { margin-right: 5px; }
.console-grid { display: grid; grid-template-columns: 218px minmax(0, 1fr); gap: 18px; align-items: start; }
.group-panel, .link-panel { background: var(--paper); border: 1px solid var(--line); border-radius: 9px; min-width: 0; }
.group-panel { padding: 12px; }
.group-heading, .link-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.group-heading { padding: 5px 5px 11px; }
.group-heading h2 { font-size: 14px; }
.group-list { list-style: none; padding: 0; margin: 0 0 12px; }
.group-list li { display: flex; align-items: center; border-radius: 8px; padding: 2px 3px; min-height: 42px; }
.group-list li:hover, .group-list .selected, .recycle-button.selected { background: var(--accent-soft); color: var(--accent); }
.group-button, .recycle-button { display: flex; align-items: center; gap: 8px; background: none; border: 0; text-align: left; }
.group-button { flex: 1; min-width: 0; padding: 6px 3px; font-weight: 600; }
.group-button span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.group-button small { margin-left: auto; color: var(--muted); font-variant-numeric: tabular-nums; }
.group-menu { margin-left: 3px; }
.drag-handle { display: grid; place-items: center; cursor: grab; width: 15px; color: var(--muted); }
.recycle-button { width: 100%; border-top: 1px solid var(--line); padding: 12px 8px 8px; border-radius: 7px; color: var(--muted); }
.recycle-button:hover { color: var(--accent); }
.link-panel { overflow: hidden; }
.link-heading { padding: 17px 20px; min-height: 80px; }
.link-heading h2 { font-size: 20px; line-height: 1.25; }
.link-heading p { color: var(--muted); font-size: 13px; margin-top: 4px; }
.sort-select { width: 124px; }
.link-table { --el-table-header-bg-color: #f1f0ea; --el-table-row-hover-bg-color: #f6f6fc; --el-table-border-color: var(--line); --el-table-header-text-color: var(--muted); --el-table-bg-color: var(--paper); --el-table-tr-bg-color: var(--paper); }
.link-table :deep(.el-table__cell) { padding: 12px 0; }
.link-table :deep(.cell) { padding: 0 10px; }
.link-table :deep(.el-table__cell:first-child .cell) { padding-left: 20px; }
.link-cell { display: flex; gap: 11px; align-items: start; }
.link-icon { display: grid; place-items: center; width: 34px; height: 34px; flex: 0 0 34px; border-radius: 9px; background: var(--accent-soft); color: var(--accent); }
.link-text { display: grid; min-width: 0; line-height: 1.45; }
.link-text > * { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.link-text strong { font-weight: 700; color: var(--ink); }
.link-text a, .inactive-link { font-family: var(--mono); font-size: 12px; font-weight: 650; }
.link-text a:hover { text-decoration: underline; }
.origin-url, .inactive-link, .metric small { color: var(--muted); }
.origin-url, .metric small { font-size: 12px; }
.compact-actions { margin-top: 10px; display: grid; gap: 5px; }
.compact-metrics { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; color: var(--muted); font-size: 12px; }
.status { padding: 3px 7px; border-radius: 5px; background: var(--accent-soft); color: var(--accent); font-weight: 600; font-size: 12px; white-space: nowrap; }
.status.inactive { background: #f3efeb; color: #775f46; }
.metric { display: grid; font-variant-numeric: tabular-nums; }
.metric strong { font-family: var(--mono); }
.metric strong { color: var(--ink); font-size: 16px; }

.icon-button { display: inline-grid; place-items: center; width: 30px; height: 30px; flex: 0 0 auto; border: 0; border-radius: 7px; background: transparent; color: var(--accent); font-size: 16px; transition: background .18s ease; }
.icon-button:hover { background: var(--accent-soft); }
.pagination-block { display: flex; justify-content: flex-end; overflow-x: auto; padding: 14px 20px; }
.table-loading { padding: 20px; }
.empty-state { display: grid; justify-items: center; gap: 9px; text-align: center; padding: 54px 15px 64px; border-top: 1px solid var(--line); }
.empty-state > .el-icon { color: var(--accent); font-size: 23px; width: 47px; height: 47px; border-radius: 12px; background: var(--accent-soft); }
.empty-state strong { font-size: 17px; }
.empty-state p { color: var(--muted); }
@media (max-width: 860px) {
  .console-grid { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .group-list { display: flex; gap: 5px; overflow-x: auto; padding-bottom: 4px; }
  .group-list li { min-width: 150px; }
  .group-heading { padding-bottom: 4px; }
  .recycle-button { padding: 8px; }
}
@media (max-width: 560px) {
  .page-heading, .link-heading { align-items: flex-start; flex-direction: column; }
  .page-heading { margin-bottom: 16px; }
  .overview { margin-bottom: 12px; }
  .overview dt { font-size: 12px; }
  .overview dt .el-icon { display: none; }
  .link-heading { padding: 12px; }
  .pagination-block { justify-content: flex-start; padding-inline: 8px; }
  .pagination-block :deep(.el-pagination__sizes) { display: none; }
}
</style>
