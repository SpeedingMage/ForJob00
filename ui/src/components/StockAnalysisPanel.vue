<template>
  <div class="dashboard">
    <aside class="sidebar">
      <div class="user-info">
        <span class="phone-label">{{ phone }}</span>
        <el-button size="small" text @click="$emit('logout')">退出</el-button>
      </div>
      <div class="fav-header"><h3>我的自选</h3></div>
      <div class="fav-list">
        <div v-if="favorites.length === 0" class="fav-empty">暂无自选股票</div>
        <div v-for="code in favorites" :key="code"
          :class="['fav-item', { active: code === selectedStock, disabled: addLoading }]"
          @click="addLoading ? null : selectStock(code)">
          <span class="fav-code">{{ code }}</span>
          <span v-if="!addLoading" class="fav-del" @click.stop="removeFavorite(code)">&times;</span>
        </div>
      </div>
      <div class="add-stock">
        <el-input v-model="addCode" placeholder="添加代码" size="small"
          :disabled="addLoading" @keyup.enter="addFavorite" />
        <el-button size="small" :loading="addLoading" @click="addFavorite">+</el-button>
      </div>
    </aside>

    <main class="main-content">
      <!-- 未选中股票 -->
      <div v-if="!selectedStock" class="welcome-view">
        <header class="header">
          <h1>AI 股票分析面板</h1>
          <p class="subtitle">从左侧选择自选股，或输入新代码开始分析</p>
        </header>
        <div class="input-section">
          <el-card>
            <div class="input-row">
              <el-input v-model="searchCode" placeholder="输入股票代码，如 AAPL、600519"
                size="large" clearable @keyup.enter="startAnalyze()" />
              <el-button type="primary" size="large" :loading="analyzeLoading"
                :disabled="!searchCode.trim()" @click="startAnalyze()">
                {{ analyzeLoading ? '分析中...' : '开始 AI 分析' }}
              </el-button>
            </div>
          </el-card>
        </div>
        <div v-if="analyzeError" class="error-section">
          <el-alert :title="analyzeError" type="error" show-icon closable @close="analyzeError=''" />
        </div>
      </div>

      <!-- 已选中股票: 详情 -->
      <div v-else class="detail-view">
        <header class="detail-header">
          <h2>{{ selectedStock }}</h2>
          <el-button type="primary" size="small"
            :loading="analyzeLoading"
            :disabled="reanalyzeCooldown > 0"
            @click="reanalyze">
            {{ reanalyzeCooldown > 0 ? reanalyzeCooldown + 's' : '重新 AI 分析' }}
          </el-button>
        </header>

        <div v-if="detailLoading && !detail" class="loading-wrap">
          <el-skeleton :rows="6" animated />
        </div>

        <template v-if="detail">
          <!-- AI 分析卡片（静态，只有手动点按钮才更新） -->
          <div v-if="detail.analysis" class="analysis-section">
            <div class="cards-row">
              <el-card class="result-card">
                <template #header><span class="card-label">市场情绪</span></template>
                <div class="sentiment-content">
                  <el-tag :type="sentimentType" size="large" effect="dark" disable-transitions>
                    {{ sentimentLabel }}
                  </el-tag>
                </div>
              </el-card>
              <el-card class="result-card">
                <template #header><span class="card-label">风险等级</span></template>
                <div class="risk-content">{{ detail.analysis.riskLevel }}</div>
              </el-card>
            </div>
            <el-card class="result-card summary-card">
              <template #header><span class="card-label">分析总结</span></template>
              <p class="summary-text">{{ detail.analysis.summary }}</p>
            </el-card>
          </div>
          <div v-else class="no-analysis">
            <el-empty description="暂无 AI 分析，请点击「重新 AI 分析」" />
          </div>

          <!-- 图表1：实时行情 -->
          <el-card class="chart-card">
            <template #header>
              <div class="chart-header-row">
                <span class="card-label">实时行情</span>
                <span v-if="marketStatusLabel" :class="['market-status-badge', marketStatusClass]">
                  {{ marketStatusLabel }}
                </span>
              </div>
            </template>
            <div v-if="!realtimeHasData" class="no-realtime-data">
              <span>{{ marketStatusLabel === '交易中' ? '加载中...' : '暂无行情数据' }}</span>
            </div>
            <div v-show="realtimeHasData" class="chart-wrap realtime-chart"><canvas ref="realtimeCanvas"></canvas></div>
          </el-card>

          <!-- 图表2：近10日历史走势 -->
          <el-card class="chart-card">
            <template #header><span class="card-label">近 10 日走势</span></template>
            <div class="chart-wrap history-chart"><canvas ref="historyCanvas"></canvas></div>
          </el-card>
        </template>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import axios from 'axios'
import { Chart, registerables } from 'chart.js'
Chart.register(...registerables)

const props = defineProps({ phone: String })
defineEmits(['logout'])

// ---- 收藏列表 ----
const favorites = ref([])
const addCode = ref('')
const addLoading = ref(false)
const lastAddTime = ref(0)
const ADD_COOLDOWN = 1000 // 两次添加的最小间隔（毫秒）

const fetchFavorites = async () => {
  try {
    const res = await axios.get('/api/favorites/list', { params: { phone: props.phone } })
    if (res.data.success) favorites.value = res.data.data || []
  } catch { favorites.value = [] }
}

const addFavorite = async () => {
  const code = addCode.value.trim().toUpperCase()
  if (!code) return

  // 第一层：loading 锁，防止并发请求
  if (addLoading.value) return

  // 第二层：时间锁，防止极快速双击穿透 loading 锁
  if (Date.now() - lastAddTime.value < ADD_COOLDOWN) return

  if (favorites.value.includes(code)) { addCode.value = ''; return }

  lastAddTime.value = Date.now()
  addLoading.value = true
  try {
    const res = await axios.post('/api/favorites/add', { phone: props.phone, stockCode: code })
    if (res.data.success) {
      favorites.value.unshift(code)
      addCode.value = ''
      if (!selectedStock.value) selectStock(code)
    }
  } catch { /* ignore */ }
  finally { addLoading.value = false }
}

const removeFavorite = async (code) => {
  try {
    await axios.post('/api/favorites/remove', { phone: props.phone, stockCode: code })
    favorites.value = favorites.value.filter(c => c !== code)
    if (selectedStock.value === code) {
      selectedStock.value = favorites.value.length > 0 ? favorites.value[0] : ''
    }
  } catch { /* ignore */ }
}

// ---- 搜索 & 分析 ----
const searchCode = ref('')
const selectedStock = ref('')
const analyzeLoading = ref(false)
const analyzeError = ref('')
const reanalyzeCooldown = ref(0)
let cooldownTimer = null

const startAnalyze = async () => {
  const code = searchCode.value.trim().toUpperCase()
  if (!code) return
  analyzeLoading.value = true
  analyzeError.value = ''
  try {
    await axios.post('/api/stock/analyze', { stockCode: code })
    if (!favorites.value.includes(code)) {
      await axios.post('/api/favorites/add', { phone: props.phone, stockCode: code })
      favorites.value.unshift(code)
    }
    searchCode.value = ''
    selectStock(code)
  } catch (e) {
    analyzeError.value = e.response?.data?.message || e.message || '分析失败'
  } finally {
    analyzeLoading.value = false
  }
}

const reanalyze = async () => {
  if (!selectedStock.value || reanalyzeCooldown.value > 0) return
  analyzeLoading.value = true
  try {
    await axios.post('/api/stock/analyze', { stockCode: selectedStock.value })
    await fetchDetail()
    reanalyzeCooldown.value = 30
    clearInterval(cooldownTimer)
    cooldownTimer = setInterval(() => {
      reanalyzeCooldown.value--
      if (reanalyzeCooldown.value <= 0) clearInterval(cooldownTimer)
    }, 1000)
  } catch { /* ignore */ }
  finally { analyzeLoading.value = false }
}

// ---- 股票详情 ----
const detail = ref(null)
const detailLoading = ref(false)

const selectStock = (code) => {
  if (selectedStock.value === code) return
  selectedStock.value = code
}

const fetchDetail = async () => {
  if (!selectedStock.value) return
  detailLoading.value = true
  detail.value = null
  try {
    const res = await axios.get('/api/stock/detail', { params: { stockCode: selectedStock.value } })
    detail.value = res.data
    await nextTick()
    renderHistoryChart()
    // 启动实时行情轮询
    startRealtimePolling()
  } catch { detail.value = null }
  finally { detailLoading.value = false }
}

// ---- 图表1：实时行情 ----
const realtimeCanvas = ref(null)
let realtimeChart = null
let realtimeTimer = null
const marketStatus = ref('')
const realtimeHasData = ref(false)

const startRealtimePolling = async () => {
  stopRealtimePolling()
  if (!selectedStock.value) return

  const fetchRealtime = async () => {
    try {
      const res = await axios.get('/api/stock/realtime', { params: { stockCode: selectedStock.value } })
      const body = res.data
      marketStatus.value = body.status || ''
      const data = body.data || []
      realtimeHasData.value = data.length > 0
      if (data.length > 0) renderRealtimeChart(data)
      // 根据状态调整轮询间隔
      adjustRealtimeInterval(body.status)
    } catch { /* ignore */ }
  }
  await fetchRealtime()
}

const adjustRealtimeInterval = (status) => {
  if (realtimeTimer) { clearInterval(realtimeTimer); realtimeTimer = null }
  const interval = status === 'OPEN' ? 5000 : 30000
  realtimeTimer = setInterval(async () => {
    try {
      const res = await axios.get('/api/stock/realtime', { params: { stockCode: selectedStock.value } })
      const body = res.data
      marketStatus.value = body.status || ''
      const data = body.data || []
      realtimeHasData.value = data.length > 0
      if (data.length > 0) renderRealtimeChart(data)
      // 状态变化时重新调整间隔
      if ((body.status === 'OPEN') !== (status === 'OPEN')) {
        adjustRealtimeInterval(body.status)
      }
    } catch { /* ignore */ }
  }, interval)
}

const stopRealtimePolling = () => {
  if (realtimeTimer) { clearInterval(realtimeTimer); realtimeTimer = null }
}

const renderRealtimeChart = (data) => {
  if (realtimeChart) { realtimeChart.destroy(); realtimeChart = null }
  const canvas = realtimeCanvas.value
  if (!canvas || !data?.length) return

  const labels = data.map(d => d.time)
  const prices = data.map(d => d.price)
  const isUp = prices.length >= 2 && prices[prices.length - 1] >= prices[0]

  realtimeChart = new Chart(canvas, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: '实时价',
        data: prices,
        borderColor: isUp ? '#67c23a' : '#f56c6c',
        backgroundColor: (isUp ? '#67c23a' : '#f56c6c') + '15',
        fill: true,
        tension: 0.2,
        pointRadius: 0,
        borderWidth: 1.5
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { ticks: { maxTicksLimit: 10, maxRotation: 0 }, grid: { display: false } },
        y: { grid: { color: '#ebeef5' } }
      }
    }
  })
}

// ---- 图表2：10日历史 ----
const historyCanvas = ref(null)
let historyChart = null

const renderHistoryChart = () => {
  if (historyChart) { historyChart.destroy(); historyChart = null }
  const canvas = historyCanvas.value
  if (!canvas || !detail.value?.priceHistory?.length) return

  const history = detail.value.priceHistory
  const labels = history.map(d => d.date)
  const closes = history.map(d => d.close)
  const isUp = closes.length >= 2 && closes[closes.length - 1] >= closes[0]
  const lineColor = isUp ? '#67c23a' : '#f56c6c'

  historyChart = new Chart(canvas, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: '收盘价',
        data: closes,
        borderColor: lineColor,
        backgroundColor: lineColor + '25',
        fill: true,
        tension: 0.3,
        pointRadius: 4,
        pointBackgroundColor: lineColor
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { grid: { display: false } },
        y: { grid: { color: '#ebeef5' } }
      }
    }
  })
}

// ---- computed ----
const sentimentType = computed(() => {
  if (!detail.value?.analysis) return ''
  const s = detail.value.analysis.sentiment
  if (s === 'Bullish') return 'success'
  if (s === 'Bearish') return 'danger'
  return 'warning'
})
const sentimentLabel = computed(() => {
  if (!detail.value?.analysis) return ''
  const s = detail.value.analysis.sentiment
  if (s === 'Bullish') return '看涨 Bullish'
  if (s === 'Bearish') return '看跌 Bearish'
  return '中性 Neutral'
})
const marketStatusLabel = computed(() => {
  switch (marketStatus.value) {
    case 'OPEN': return '交易中'
    case 'LUNCH_BREAK': return '午间休市'
    case 'PRE_MARKET': return '盘前'
    case 'AFTER_MARKET': return '已收盘'
    case 'WEEKEND': return '休市'
    default: return ''
  }
})
const marketStatusClass = computed(() => {
  switch (marketStatus.value) {
    case 'OPEN': return 'status-open'
    case 'LUNCH_BREAK': return 'status-lunch'
    default: return 'status-closed'
  }
})

// ---- lifecycle ----
watch(selectedStock, (code) => {
  stopRealtimePolling()
  if (code) fetchDetail()
})

onMounted(() => fetchFavorites())
onUnmounted(() => stopRealtimePolling())
</script>

<style scoped>
.dashboard { display: flex; height: 100vh; background: #f5f7fa; }

.sidebar {
  width: 220px; min-width: 220px; background: #1a1a2e; color: #fff;
  display: flex; flex-direction: column; padding: 16px;
}
.user-info { display: flex; justify-content: space-between; align-items: center;
  padding-bottom: 12px; border-bottom: 1px solid #333; margin-bottom: 12px; }
.phone-label { font-size: 13px; color: #ccc; }
.fav-header h3 { font-size: 14px; margin: 0 0 10px; color: #ccc; }
.fav-list { flex: 1; overflow-y: auto; }
.fav-empty { color: #666; font-size: 12px; padding: 8px 0; }
.fav-item { display: flex; justify-content: space-between; align-items: center;
  padding: 8px 10px; border-radius: 6px; cursor: pointer; font-size: 14px;
  margin-bottom: 2px; transition: background 0.15s; }
.fav-item:hover { background: #2a2a4e; }
.fav-item.active { background: #409eff; font-weight: 600; }
.fav-item.disabled { pointer-events: none; opacity: 0.5; }
.fav-code { font-family: monospace; }
.fav-del { font-size: 18px; opacity: 0.5; cursor: pointer; }
.fav-del:hover { opacity: 1; color: #f56c6c; }
.add-stock { display: flex; gap: 6px; padding-top: 12px;
  border-top: 1px solid #333; margin-top: 8px; }
.add-stock .el-input { flex: 1; }

.main-content { flex: 1; overflow-y: auto; padding: clamp(16px, 3vw, 40px); }

.welcome-view { max-width: min(700px, 90%); margin: clamp(20px, 5vh, 40px) auto 0; }
.header { text-align: center; margin-bottom: clamp(16px, 3vw, 24px); }
.header h1 { font-size: clamp(20px, 3vw, 26px); color: #303133; margin: 0 0 6px; }
.subtitle { color: #909399; font-size: clamp(13px, 1.5vw, 14px); margin: 0; }
.input-section { margin-bottom: 16px; }
.input-row { display: flex; gap: 12px; flex-wrap: wrap; }
.input-row .el-input { flex: 1; min-width: 200px; }
.error-section { margin-bottom: 16px; }

.detail-view { width: 100%; max-width: 1100px; }
.detail-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap;
  gap: 10px; margin-bottom: clamp(12px, 2vw, 16px); }
.detail-header h2 { font-size: clamp(18px, 2.5vw, 24px); margin: 0; font-family: monospace; }
.loading-wrap { padding: 20px; }

.analysis-section { margin-bottom: 16px; }
.cards-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; margin-bottom: 12px; }
.card-label { font-weight: 600; font-size: 14px; }
.sentiment-content { text-align: center; padding: 8px 0; }
.risk-content { color: #606266; line-height: 1.6; word-break: break-word; }
.summary-text { color: #303133; line-height: 1.8; white-space: pre-wrap; margin: 0; word-break: break-word; }
.result-card { border-radius: 8px; }
.no-analysis { padding: 20px 0; }

.chart-card { border-radius: 8px; margin-bottom: 16px; }
.chart-header-row { display: flex; justify-content: space-between; align-items: center; }
.market-status-badge {
  font-size: 12px; padding: 2px 10px; border-radius: 10px; font-weight: 600;
}
.status-open { background: #e8f5e9; color: #2e7d32; }
.status-lunch { background: #fff3e0; color: #e65100; }
.status-closed { background: #f5f5f5; color: #909399; }
.no-realtime-data {
  display: flex; justify-content: center; align-items: center;
  height: clamp(200px, 30vw, 280px); color: #909399; font-size: 14px;
}
.chart-wrap { position: relative; width: 100%; }
.chart-wrap.realtime-chart { height: clamp(200px, 30vw, 280px); }
.chart-wrap.history-chart { height: clamp(220px, 32vw, 300px); }
.chart-wrap canvas { width: 100% !important; height: 100% !important; }

/* 平板竖屏 */
@media (max-width: 1024px) {
  .sidebar { width: 180px; min-width: 180px; }
  .chart-wrap.realtime-chart { height: 240px; }
  .chart-wrap.history-chart { height: 260px; }
}

/* 手机横屏 / 小平板 */
@media (max-width: 768px) {
  .dashboard { flex-direction: column; }
  .sidebar { width: 100%; min-width: auto; flex-direction: row; flex-wrap: wrap;
    gap: 8px; padding: 10px; align-items: center; }
  .user-info { border: none; margin: 0; padding: 0; }
  .fav-header { display: none; }
  .fav-list { display: flex; gap: 4px; overflow-x: auto; flex: none; }
  .fav-item { white-space: nowrap; padding: 4px 8px; }
  .fav-del { display: none; }
  .add-stock { border: none; margin: 0; padding: 0; }
  .main-content { padding: 16px; }
  .cards-row { grid-template-columns: 1fr; }
  .detail-header h2 { font-size: 18px; }
  .input-row { flex-direction: column; }
  .input-row .el-input { min-width: auto; }
  .chart-wrap.realtime-chart { height: 220px; }
  .chart-wrap.history-chart { height: 240px; }
}

/* 手机竖屏 */
@media (max-width: 480px) {
  .detail-header { flex-direction: column; align-items: flex-start; }
  .detail-view { width: 100%; }
  .chart-wrap.realtime-chart { height: 180px; }
  .chart-wrap.history-chart { height: 200px; }
  .header h1 { font-size: 18px; }
  .result-card :deep(.el-card__body) { padding: 12px; }
}
</style>
