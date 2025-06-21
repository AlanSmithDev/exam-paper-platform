<template>
  <div class="resource-page">
    <header><h1 class="page-title">教学资源中心</h1><p class="page-subtitle">课件 / 教案 / 学案 / 试卷文档库 · 免费预览 33% · 点数计费下载（服务不可用时自动回退演示数据）</p></header>

    <div class="toolbar">
      <el-input v-model="query" clearable placeholder="搜索资源标题" style="max-width:340px" @keyup.enter="reload" />
      <el-select v-model="stage" style="width:110px" @change="reload">
        <el-option label="全部学段" :value="null" /><el-option v-for="(n,i) in stageNames" :key="i" :label="n" :value="i+1" />
      </el-select>
      <el-select v-model="subject" style="width:110px" @change="reload">
        <el-option label="全部学科" value="" /><el-option v-for="s in subjects" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-model="level" style="width:110px" @change="reload">
        <el-option label="全部等级" value="" /><el-option label="免费" value="FREE" /><el-option label="普通" value="NORMAL" /><el-option label="特供" value="SPECIAL" /><el-option label="精品" value="BOUTIQUE" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="reload">搜索</el-button>
      <div style="flex:1" />
      <el-button :icon="ShoppingCart" @click="basketOpen = true">资源篮 <b>{{ basket.length }}</b></el-button>
      <el-button :icon="Download" @click="mineOpen = true">我的下载</el-button>
    </div>

    <div class="tabs">
      <button v-for="c in categories" :key="c" :class="{ active: category === c }" @click="category = c; reload()">{{ c }}</button>
    </div>

    <div class="resource-grid">
      <article v-for="item in items" :key="item.id" class="resource-item">
        <div class="resource-type">{{ item.category }}</div>
        <el-tag size="small" :type="item.level === 'FREE' ? 'success' : item.level === 'BOUTIQUE' ? 'warning' : 'info'" style="float:right">
          {{ { FREE: '免费', NORMAL: '普通', SPECIAL: '特供', BOUTIQUE: '精品' }[item.level] }}
        </el-tag>
        <h2>{{ item.title }}</h2>
        <div class="resource-meta">
          <span>{{ stageNames[(item.stage || 1) - 1] }} · {{ item.subject }}</span>
          <span>{{ item.format }}{{ item.pages ? ' · ' + item.pages + ' 页' : '' }}</span>
        </div>
        <div class="resource-meta">
          <span>浏览 {{ item.browseCount || 0 }} · 下载 {{ item.downloadCount || 0 }}</span>
          <b style="color:var(--primary)">{{ item.level === 'FREE' ? '免费' : '¥' + ((item.priceCents || 0) / 100).toFixed(2) }}</b>
        </div>
        <div class="resource-actions">
          <el-button size="small" @click="openDetail(item)">详情预览</el-button>
          <el-button size="small" @click="addBasket(item)">+资源篮</el-button>
          <el-button size="small" type="primary" plain @click="download(item)">{{ item.level === 'FREE' ? '下载' : '点数下载' }}</el-button>
        </div>
      </article>
    </div>
    <el-empty v-if="!items.length" description="当前筛选暂无资源" />
    <div v-if="!demoMode && total > 0" style="text-align:center;margin-top:22px">
      <el-pagination layout="prev, pager, next" :total="total" :page-size="size" :current-page="page" @current-change="p => { page = p; reload() }" />
    </div>

    <el-dialog v-model="detailOpen" :title="current?.title" width="min(560px,94vw)">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="类别">{{ current?.category }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ { FREE: '免费', NORMAL: '普通', SPECIAL: '特供', BOUTIQUE: '精品' }[current?.level] }}</el-descriptions-item>
        <el-descriptions-item label="作者">{{ current?.author || '—' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ current?.school || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="preview.totalPages" style="margin-top:14px">
        <el-progress :percentage="Math.round(preview.freePages / preview.totalPages * 100)" :stroke-width="14" striped />
        <p style="font-size:12px;color:var(--text-2);margin-top:8px">
          免费预览 {{ preview.freePages }} / {{ preview.totalPages }} 页，剩余 {{ preview.lockedPages }} 页下载后查看全部
        </p>
      </div>
      <el-button size="small" text type="warning" style="margin-top:6px" @click="appealOpen = true">版权异议 / 挑错建议</el-button>
      <template #footer>
        <el-button @click="detailOpen = false">关闭</el-button>
        <el-button @click="addBasket(current); detailOpen = false">+资源篮</el-button>
        <el-button type="primary" @click="download(current); detailOpen = false">{{ current?.level === 'FREE' ? '免费下载' : '点数下载' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="appealOpen" title="版权异议 / 挑错建议" width="min(480px,94vw)">
      <el-select v-model="appealType" style="width:100%;margin-bottom:10px">
        <el-option label="版权异议" value="COPYRIGHT" /><el-option label="内容错误" value="ERROR" /><el-option label="其他" value="OTHER" />
      </el-select>
      <el-input v-model="appealText" type="textarea" :rows="4" placeholder="请描述具体问题（至少 5 字），核实后将在 SLA 内处理" />
      <template #footer>
        <el-button @click="appealOpen = false">取消</el-button>
        <el-button type="primary" @click="submitAppeal">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="basketOpen" title="资源篮" size="min(420px,100vw)">
      <article v-for="item in basket" :key="item.id" class="basket-row">
        <div><b style="font-size:13px">{{ item.title }}</b><p style="font-size:11px;color:var(--text-2)">{{ item.category }} · {{ item.level === 'FREE' ? '免费' : '¥' + ((item.priceCents || 0) / 100).toFixed(2) }}</p></div>
        <el-button :icon="Delete" circle text @click="removeBasket(item)" />
      </article>
      <el-empty v-if="!basket.length" description="资源篮为空：从列表点「+资源篮」加入" />
      <div v-if="basket.length" style="margin-top:16px;display:flex;justify-content:space-between;align-items:center;gap:10px">
        <span style="font-size:11px;color:var(--text-2)">一次结算合并扣点；已购/免费件不重复计费</span>
        <el-button type="primary" :loading="checkingOut" @click="checkout">一键结算</el-button>
      </div>
    </el-drawer>

    <el-drawer v-model="mineOpen" title="我的下载" size="min(420px,100vw)">
      <article v-for="d in mine" :key="d.id" class="basket-row">
        <div><b style="font-size:13px">#{{ d.resourceId }}</b><p style="font-size:11px;color:var(--text-2)">{{ d.mode === 'FREE' ? '免费下载' : '点数下载 ¥' + ((d.priceCents || 0) / 100).toFixed(2) }} · {{ (d.createdAt || '').replace('T', ' ') }}</p></div>
      </article>
      <el-empty v-if="!mine.length" description="暂无下载记录" />
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, ShoppingCart, Download, Delete } from '@element-plus/icons-vue'
import http from '../api/request'

const router = useRouter()
const stageNames = ['小学', '初中', '高中', '大学', '考研', '中职']
const subjects = ['数学', '语文', '英语', '物理', '化学', '生物', '历史', '地理', '政治']
const categories = ['全部', 'PPT课件', '教案', '学案', '作业', '试卷', '题集', '素材']

const demoMode = ref(false)
const items = ref([]), total = ref(0), page = ref(1), size = 12
const stage = ref(null), subject = ref(''), level = ref(''), category = ref('全部'), query = ref('')
const detailOpen = ref(false), current = ref({}), preview = ref({})
const appealOpen = ref(false), appealType = ref('COPYRIGHT'), appealText = ref('')
const basketOpen = ref(false), basket = ref([])
const mineOpen = ref(false), mine = ref([])
const checkingOut = ref(false)

onMounted(() => { reload(); loadBasket() })

async function reload() {
  try {
    const d = await http.get('/resources', { params: {
      stage: stage.value || undefined, subject: subject.value || undefined,
      level: level.value || undefined, category: category.value === '全部' ? undefined : category.value,
      keyword: query.value || undefined, page: page.value, size
    } })
    items.value = d.records || []; total.value = Number(d.total || 0); demoMode.value = false
  } catch {
    demoMode.value = true; page.value = 1
    items.value = demoItems().filter(x =>
      (stage.value ? x.stage === stage.value : true) &&
      (subject.value ? x.subject === subject.value : true) &&
      (level.value ? x.level === level.value : true) &&
      (category.value === '全部' || x.category === category.value) &&
      (!query.value || x.title.includes(query.value)))
    total.value = items.value.length
  }
}

function demoItems() {
  return [
    { id: 9001, title: '2.2 基本不等式同步课件（人教A版2019必修第一册）', stage: 3, subject: '数学', category: 'PPT课件', format: 'pptx', pages: 48, level: 'NORMAL', priceCents: 200, browseCount: 103206, downloadCount: 2, author: '学科网数编组' },
    { id: 9002, title: '1.1 集合的概念教案+学案打包', stage: 3, subject: '数学', category: '教案', format: 'zip', pages: 12, level: 'FREE', priceCents: 0, browseCount: 4779, downloadCount: 86, author: '学科网数编组' },
    { id: 9003, title: '《归园田居（其一）》精品课件 18张（统编版必修上册）', stage: 3, subject: '语文', category: 'PPT课件', format: 'pptx', pages: 18, level: 'BOUTIQUE', priceCents: 500, browseCount: 117, downloadCount: 2, author: '珠溪语文' },
    { id: 9004, title: '八年级上册物理第一次月考试卷（含答案解析）', stage: 2, subject: '物理', category: '试卷', format: 'docx', pages: 8, level: 'NORMAL', priceCents: 200, browseCount: 870, downloadCount: 11, author: '勤勉理科' }
  ]
}

function requireLogin() {
  if (!localStorage.getItem('examforge_token')) { ElMessage.warning('请先登录'); router.push('/login'); return true }
  return false
}

async function openDetail(item) {
  current.value = item; appealText.value = ''
  if (demoMode.value || !item.id || item.id > 9000) {
    const total_ = item.pages || 0
    preview.value = { totalPages: total_, freePages: Math.max(1, Math.floor(total_ * 0.33)), lockedPages: Math.max(0, total_ - Math.max(1, Math.floor(total_ * 0.33))) }
    detailOpen.value = true; return
  }
  try {
    const d = await http.get('/resources/' + item.id)
    current.value = d.item || item; preview.value = d.preview || {}; detailOpen.value = true
  } catch { preview.value = { totalPages: item.pages || 0, freePages: 1, lockedPages: 0 }; detailOpen.value = true }
}

async function addBasket(item) {
  if (requireLogin()) return
  if (demoMode.value) { ElMessage.info('演示模式：启动后端后可用资源篮'); return }
  await http.post('/resources/' + item.id + '/basket')
  ElMessage.success('已加入资源篮')
  loadBasket()
}

async function removeBasket(item) {
  await http.delete('/resources/' + item.id + '/basket')
  loadBasket()
}

async function loadBasket() {
  try { basket.value = await http.get('/resources/basket') } catch { basket.value = [] }
}

async function checkout() {
  if (requireLogin()) return
  if (demoMode.value) { ElMessage.info('演示模式：启动后端后可批量结算'); return }
  checkingOut.value = true
  try {
    const r = await http.post('/resources/basket/checkout')
    ElMessage.success(`结算完成：扣 ${r.totalCharged} 点（免费 ${r.freeCount} 件、已购跳过 ${r.ownedCount} 件、下架跳过 ${r.skippedCount} 件）`)
    basket.value = []
    mine.value = await http.get('/resources/downloads/mine').catch(() => mine.value)
    mineOpen.value = true
  } finally { checkingOut.value = false }
}

async function download(item) {
  if (requireLogin()) return
  if (demoMode.value) { ElMessage.info('演示模式：启动后端后可计费下载'); return }
  const d = await http.post('/resources/' + item.id + '/download')
  ElMessage.success(d.mode === 'FREE' ? '下载成功（免费/已购）' : `已扣 ${d.charged} 点，开始下载`)
  mineOpen.value = true
  mine.value = await http.get('/resources/downloads/mine').catch(() => mine.value)
}

async function submitAppeal() {
  if (requireLogin()) return
  if ((appealText.value || '').trim().length < 5) { ElMessage.warning('请描述具体问题（至少 5 字）'); return }
  await http.post('/resources/appeals', {
    targetType: 'RESOURCE', targetId: current.value?.id,
    appealType: appealType.value, content: appealText.value.trim()
  })
  appealOpen.value = false
  ElMessage.success('已提交，我们将在核实后处理')
}
</script>

<style scoped>
.resource-page{max-width:1350px;margin:auto}.toolbar{display:flex;gap:12px;margin:22px 0;flex-wrap:wrap}.toolbar .el-select{width:110px}.tabs{display:flex;gap:18px;overflow:auto;border-bottom:1px solid var(--line);margin-bottom:20px}.tabs button{border:0;background:none;white-space:nowrap;padding:12px 0;color:#748598;cursor:pointer}.tabs button.active{color:var(--primary);border-bottom:2px solid var(--primary);font-weight:600}.resource-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:15px}.resource-item{background:#fff;border:1px solid var(--line);border-radius:8px;padding:18px}.resource-type{font-size:11px;color:#238b77;background:#eaf8f3;display:inline-block;padding:4px 7px;border-radius:3px}.resource-item h2{font-size:15px;margin:14px 0 8px;overflow-wrap:anywhere}.resource-meta{display:flex;justify-content:space-between;align-items:center;gap:8px;font-size:11px;color:#9ba8b6;border-top:1px solid var(--line);padding-top:10px;margin-top:8px}.resource-actions{display:flex;justify-content:space-between;align-items:center;gap:8px;margin-top:14px}.basket-row{display:flex;gap:10px;align-items:center;border-bottom:1px solid var(--line);padding:14px 0}.basket-row>div{flex:1;min-width:0}@media(max-width:1000px){.resource-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:620px){.resource-grid{grid-template-columns:1fr}.toolbar{flex-wrap:wrap}}
</style>
