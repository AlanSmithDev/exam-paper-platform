<template>
  <div class="creator-page">
    <header><h1 class="page-title">创作者中心</h1><p class="page-subtitle">上传资源 → 管理员审核上架 → 他人点数下载 → 分成自动入账（docs/26 §6）</p></header>

    <div class="creator-grid">
      <section class="panel">
        <h2>上传资源</h2>
        <el-form label-width="78px" label-position="left">
          <el-form-item label="标题" required>
            <el-input v-model="form.title" maxlength="200" placeholder="如：2.3 匀变速直线运动位移关系课件（人教A版2019）" />
          </el-form-item>
          <el-form-item label="学段/学科" required>
            <el-select v-model="form.stage" style="width:110px"><el-option v-for="(n,i) in stageNames" :key="i" :label="n" :value="i+1" /></el-select>
            <el-select v-model="form.subject" style="width:110px;margin-left:8px"><el-option v-for="s in subjects" :key="s" :label="s" :value="s" /></el-select>
          </el-form-item>
          <el-form-item label="类别/格式" required>
            <el-select v-model="form.category" style="width:130px"><el-option v-for="c in categories" :key="c" :label="c" :value="c" /></el-select>
            <el-select v-model="form.format" style="width:100px;margin-left:8px"><el-option v-for="f in formats" :key="f" :label="f" :value="f" /></el-select>
          </el-form-item>
          <el-form-item label="等级">
            <el-select v-model="form.level">
              <el-option label="免费 FREE" value="FREE" /><el-option label="普通 NORMAL" value="NORMAL" />
              <el-option label="特供 SPECIAL" value="SPECIAL" /><el-option label="精品 BOUTIQUE" value="BOUTIQUE" />
            </el-select>
          </el-form-item>
          <el-form-item label="定价（元）">
            <el-input-number v-model="priceYuan" :min="0" :max="1000" :precision="2" :step="0.5" :disabled="form.level === 'FREE'" />
            <span class="hint">{{ form.level === 'FREE' ? '免费资源价格必须为 0' : '他人下载时按点数扣取，1 元 = 100 点' }}</span>
          </el-form-item>
          <el-form-item label="页数">
            <el-input-number v-model="form.pages" :min="0" :max="2000" />
            <span class="hint">免费预览按 33% 计（至少 1 页）</span>
          </el-form-item>
          <el-form-item label="文件键">
            <el-input v-model="form.fileKey" placeholder="seed/xxx.pptx（演示环境占位，OSS 接入前可留空）" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="uploading" @click="upload">提交审核</el-button>
            <span class="hint">提交后进入待审（status=0），超管端「资源审核」上架后即可被下载并产生分成</span>
          </el-form-item>
        </el-form>
      </section>

      <section class="panel">
        <h2>分成收益</h2>
        <div class="earning-summary">
          <div><small>累计分成</small><b>{{ totalShare }}</b><em>点 ≈ ¥{{ (totalShare / 100).toFixed(2) }}</em></div>
          <div><small>分成笔数</small><b>{{ earningTotal }}</b></div>
          <div><small>记账比例</small><b>{{ demoMode ? '50%' : '流水内快照' }}</b></div>
          <div><small>签约状态</small><b v-if="contract">{{ contract.ratePct }}%</b><b v-else>未签约</b><em>{{ contract ? contract.subject : '按全局默认比例' }}</em></div>
        </div>
        <el-table v-if="earnings.length" :data="earnings" size="small" style="margin-top:12px">
          <el-table-column label="资源" width="76"><template #default="{ row }">#{{ row.resourceId }}</template></el-table-column>
          <el-table-column label="下载实收" width="90"><template #default="{ row }">{{ row.amountCents }} 点</template></el-table-column>
          <el-table-column label="我的分成" width="90"><template #default="{ row }"><b style="color:var(--primary)">{{ row.shareCents }} 点</b></template></el-table-column>
          <el-table-column prop="ratePct" label="比例" width="70"><template #default="{ row }">{{ row.ratePct }}%</template></el-table-column>
          <el-table-column label="时间"><template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ') }}</template></el-table-column>
        </el-table>
        <el-empty v-if="!earnings.length" description="暂无分成流水：资源被他人点数下载后自动入账" />
        <div v-if="earningTotal > earnings.length" style="text-align:center;margin-top:12px">
          <el-pagination layout="prev, pager, next" :total="earningTotal" :page-size="earningSize" :current-page="earningPage" @current-change="p => { earningPage = p; loadEarnings() }" />
        </div>
        <p class="hint" style="margin-left:0;margin-top:10px;display:block">分成即时入账点数账户（理由码 CREATOR_SHARE，自下载不分成）；即时入账失败的流水由月度结算任务按自然月补发（理由码 CREATOR_SETTLE）。服务不可用时展示演示数据。</p>
      </section>
    </div>

    <section class="panel" style="margin-top:16px">
      <h2>月度结算 <span class="hint">即时入账失败的流水按自然月聚合补发，每月 1 日 03:00 自动执行（docs/26 §6 P3）</span></h2>
      <el-table v-if="settlements.length" :data="settlements" size="small">
        <el-table-column label="结算月" width="110"><template #default="{ row }">{{ row.month }}</template></el-table-column>
        <el-table-column label="补发分成" width="170"><template #default="{ row }"><b style="color:var(--primary)">{{ row.shareCents }} 点 ≈ ¥{{ (row.shareCents / 100).toFixed(2) }}</b></template></el-table-column>
        <el-table-column prop="rowCount" label="覆盖流水笔数" width="120" />
        <el-table-column label="入账时间"><template #default="{ row }">{{ (row.creditedAt || '').replace('T', ' ') }}</template></el-table-column>
      </el-table>
      <el-empty v-else description="暂无结算记录：分成即时入账成功则不产生结算单" />
      <div v-if="settlementTotal > settlements.length" style="text-align:center;margin-top:12px">
        <el-pagination layout="prev, pager, next" :total="settlementTotal" :page-size="settlementSize" :current-page="settlementPage" @current-change="p => { settlementPage = p; loadSettlements() }" />
      </div>
    </section>

    <section class="panel" style="margin-top:16px">
      <h2>月收入榜 TOP20</h2>
      <el-table v-if="board.length" :data="board" size="small">
        <el-table-column prop="rank" label="#" width="56" />
        <el-table-column label="创作者" width="150"><template #default="{ row }">{{ row.nickname || ('创作者#' + row.creatorUserId) }}</template></el-table-column>
        <el-table-column label="本月分成"><template #default="{ row }"><b style="color:var(--primary)">{{ row.shareCents }} 点 ≈ ¥{{ (row.shareCents / 100).toFixed(2) }}</b></template></el-table-column>
        <el-table-column prop="downloads" label="计费下载笔数" width="130" />
      </el-table>
      <el-empty v-else description="本月暂无分成记录：等待创作者资源被点数下载" />
      <p class="hint" style="margin-left:0;display:block;margin-top:8px">按自然月聚合分成流水（docs/26 §6 月收入榜），昵称经 user 服务补齐，服务不可用降级创作者#ID。</p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/request'

const router = useRouter()
const stageNames = ['小学', '初中', '高中', '大学', '考研', '中职']
const subjects = ['数学', '语文', '英语', '物理', '化学', '生物', '历史', '地理', '政治']
const categories = ['PPT课件', '教案', '学案', '作业', '试卷', '题集', '素材', '示范课']
const formats = ['pptx', 'docx', 'pdf', 'zip', 'mp4']

const demoMode = ref(false)
const form = ref({ title: '', stage: 3, subject: '数学', category: 'PPT课件', format: 'pptx', pages: 12, level: 'NORMAL', fileKey: '' })
const priceYuan = ref(2)
const uploading = ref(false)
const earnings = ref([]), earningTotal = ref(0), totalShare = ref(0), earningPage = ref(1), earningSize = 10
const board = ref([])
const settlements = ref([]), settlementTotal = ref(0), settlementPage = ref(1), settlementSize = 10
const contract = ref(null)

onMounted(() => { loadEarnings(); loadBoard(); loadSettlements(); loadContract() })

async function loadContract() {
  try { contract.value = await http.get('/resources/creator/contract') } catch { contract.value = null }
}

async function loadBoard() {
  try {
    board.value = await http.get('/resources/creator/board')
  } catch {
    board.value = [
      { rank: 1, creatorUserId: 10086, nickname: '珠溪语文', shareCents: 3260000, downloads: 16300 },
      { rank: 2, creatorUserId: 10010, nickname: '学科网数编组', shareCents: 2110000, downloads: 10550 },
      { rank: 3, creatorUserId: 10032, nickname: '勤勉理科', shareCents: 1340000, downloads: 6700 }
    ]
  }
}

async function loadEarnings() {
  try {
    const d = await http.get('/resources/creator/earnings', { params: { page: earningPage.value, size: earningSize } })
    earnings.value = d.items || []; earningTotal.value = Number(d.total || 0)
    totalShare.value = Number(d.totalShareCents || 0); demoMode.value = false
  } catch {
    demoMode.value = true
    earnings.value = [{ id: 1, resourceId: 9001, amountCents: 200, shareCents: 100, ratePct: 50, createdAt: '2026-09-29T10:00:00' }]
    earningTotal.value = 1; totalShare.value = 100
  }
}

async function loadSettlements() {
  try {
    const p = await http.get('/resources/creator/settlements', { params: { page: settlementPage.value, size: settlementSize } })
    settlements.value = p.records || []; settlementTotal.value = Number(p.total || 0)
  } catch {
    settlements.value = []; settlementTotal.value = 0
  }
}

async function upload() {
  if (!localStorage.getItem('examforge_token')) { ElMessage.warning('请先登录'); router.push('/login'); return }
  const f = form.value
  if (!f.title || !f.stage || !f.category) { ElMessage.warning('标题/学段/类别必填'); return }
  uploading.value = true
  try {
    await http.post('/resources/creator/upload', { ...f, priceCents: f.level === 'FREE' ? 0 : Math.round(priceYuan.value * 100) })
    ElMessage.success('已提交，待管理员审核通过后上架')
    form.value = { title: '', stage: 3, subject: '数学', category: 'PPT课件', format: 'pptx', pages: 12, level: 'NORMAL', fileKey: '' }
  } finally { uploading.value = false }
}
</script>

<style scoped>
.creator-page{max-width:1350px;margin:auto}.creator-grid{display:grid;grid-template-columns:minmax(0,5fr) minmax(0,7fr);gap:16px;align-items:start}.panel{background:#fff;border:1px solid var(--line);border-radius:8px;padding:18px}.panel h2{font-size:15px;margin-bottom:14px}.hint{font-size:11px;color:#9ba8b6;margin-left:10px}.earning-summary{display:flex;gap:26px;border-bottom:1px solid var(--line);padding-bottom:14px}.earning-summary small{display:block;font-size:11px;color:#9ba8b6}.earning-summary b{font-size:20px}.earning-summary em{font-style:normal;font-size:11px;color:#9ba8b6;margin-left:6px}@media(max-width:1000px){.creator-grid{grid-template-columns:1fr}}
</style>
