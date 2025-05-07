<template>
  <div class="assign-page">
    <header><h1 class="page-title">作业管理</h1><p class="page-subtitle">布置 → 点名 → 收作业 → 批改 → 班级报告 → 答题卡（e 卷通）</p></header>

    <el-card style="margin-bottom:16px">
      <h3>新建作业</h3>
      <el-form label-position="top">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="作业标题"><el-input v-model="form.title" maxlength="80" placeholder="如：第一章 集合 课后作业" /></el-form-item></el-col>
          <el-col :span="4"><el-form-item label="学科"><el-select v-model="form.subjectName" style="width:100%"><el-option v-for="s in subjects" :key="s" :label="s" :value="s" /></el-select></el-form-item></el-col>
          <el-col :span="4"><el-form-item label="截止（小时，0=不限）"><el-input-number v-model="form.deadlineHours" :min="0" :max="720" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="题目ID列表（逗号分隔，可从题库详情页复制）">
          <el-input v-model="form.qidsText" placeholder="如 101,102,103" />
        </el-form-item>
        <el-button type="primary" :loading="busy" @click="create">创建（草稿）</el-button>
      </el-form>
    </el-card>

    <el-card>
      <h3>我布置的作业 <el-button size="small" style="float:right" @click="loadMine">刷新</el-button></h3>
      <el-table :data="mine" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="['info', 'success', 'warning'][row.status]">{{ ['草稿', '已发布', '已关闭'][row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="截止" width="160">
          <template #default="{ row }">{{ row.deadline ? row.deadline.replace('T', ' ').slice(0, 16) : '不限' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="400">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" size="small" type="success" @click="publish(row)">发布</el-button>
            <el-button v-if="row.status === 1" size="small" @click="openRoster(row)">点名</el-button>
            <el-button v-if="row.status === 1" size="small" type="primary" plain @click="makeSheet(row)">答题卡</el-button>
            <el-button size="small" type="warning" plain @click="openReport(row)">班级报告</el-button>
            <el-button size="small" plain @click="openScans(row)">扫描阅卷</el-button>
            <el-button v-if="row.status !== 2" size="small" type="danger" plain @click="close(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!mine.length" description="还没有作业：创建并发布后，把学生 ID 点进名单即可收集作业" />
    </el-card>

    <el-dialog v-model="rosterVisible" title="点名（把学生加入作业名单）" width="min(440px,94vw)">
      <el-input v-model="rosterText" type="textarea" :rows="3" placeholder="学生用户ID，逗号或换行分隔，如 1001,1002" />
      <template #footer>
        <el-button @click="rosterVisible = false">取消</el-button>
        <el-button type="primary" @click="assignRoster">加入名单</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="reportVisible" title="班级报告" size="min(520px,100vw)">
      <template v-if="report">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="名单">{{ report.roster }} 人</el-descriptions-item>
          <el-descriptions-item label="已提交">{{ report.submitted }} 人</el-descriptions-item>
          <el-descriptions-item label="已批改">{{ report.graded }} 人</el-descriptions-item>
          <el-descriptions-item label="平均正确率">{{ report.avgScorePct }}%</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin:14px 0 6px">学生名单</h4>
        <el-table :data="report.rosterRows" size="small" max-height="220">
          <el-table-column prop="studentId" label="学生ID" width="90" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">{{ ['待完成', '已提交', '已批改'][row.status] }}</template>
          </el-table-column>
          <el-table-column prop="score" label="得分%" width="80">
            <template #default="{ row }">{{ row.score >= 0 ? row.score : '—' }}</template>
          </el-table-column>
          <el-table-column label="个体报告" width="90">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openStudentReport(row.studentId)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <h4 style="margin:14px 0 6px">薄弱知识点（按错误数）</h4>
        <div>
          <el-tag v-for="k in report.weakKnowledgePoints" :key="k.kp" size="small"
                  :type="k.correctRatePct < 60 ? 'danger' : 'info'" style="margin:0 6px 6px 0">
            {{ k.kp }} · 错{{ k.wrong }} · 正确率{{ k.correctRatePct }}%
          </el-tag>
          <span v-if="!report.weakKnowledgePoints?.length" style="font-size:12px;color:var(--text-2)">暂无作答数据</span>
        </div>
        <h4 style="margin:14px 0 6px">逐题正确率</h4>
        <el-table :data="report.questionStats" size="small">
          <el-table-column prop="questionId" label="题目ID" width="90" />
          <el-table-column prop="correct" label="对" width="60" />
          <el-table-column prop="wrong" label="错" width="60" />
          <el-table-column prop="pending" label="待批" width="60" />
        </el-table>
      </template>
    </el-drawer>

    <el-drawer v-model="studentReportVisible" :title="'学生学情 · #' + (studentReport ? studentReport.studentId : '')" size="min(520px,100vw)">
      <template v-if="studentReport">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="参与作业">{{ studentReport.assignments }} 次</el-descriptions-item>
          <el-descriptions-item label="累计正确率">{{ studentReport.overall.correctRatePct }}%</el-descriptions-item>
          <el-descriptions-item label="答对 / 答错">{{ studentReport.overall.correct }} / {{ studentReport.overall.wrong }}</el-descriptions-item>
          <el-descriptions-item label="待批改">{{ studentReport.overall.pending }} 题</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin:14px 0 6px">薄弱知识点（按错误数）</h4>
        <div>
          <el-tag v-for="k in studentReport.weakKnowledgePoints" :key="k.kp" size="small"
                  :type="k.correctRatePct < 60 ? 'danger' : 'info'" style="margin:0 6px 6px 0">
            {{ k.kp }} · 错{{ k.wrong }} · 正确率{{ k.correctRatePct }}%
          </el-tag>
          <span v-if="!studentReport.weakKnowledgePoints?.length" style="font-size:12px;color:var(--text-2)">暂无作答数据</span>
        </div>
        <h4 style="margin:14px 0 6px">最近作业</h4>
        <el-table :data="studentReport.trend" size="small">
          <el-table-column prop="title" label="作业" min-width="140" show-overflow-tooltip />
          <el-table-column prop="score" label="得分%" width="70">
            <template #default="{ row }">{{ row.score >= 0 ? row.score : '—' }}</template>
          </el-table-column>
          <el-table-column prop="correctRatePct" label="正确率%" width="80">
            <template #default="{ row }">{{ row.correctRatePct ?? '—' }}</template>
          </el-table-column>
        </el-table>
        <h4 style="margin:14px 0 6px">错题本（未解决 TOP10）</h4>
        <div>
          <div v-for="w in studentReport.wrongBook" :key="w.questionId" style="display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px dashed var(--line);font-size:13px">
            <router-link :to="'/questions/' + w.questionId">题目 #{{ w.questionId }}</router-link>
            <span style="font-size:12px;color:var(--text-2)">{{ w.kpNames || '无知识点' }} · 错 {{ w.wrongCount }} 次</span>
          </div>
          <span v-if="!studentReport.wrongBook?.length" style="font-size:12px;color:var(--text-2)">错题本已清空</span>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="scanVisible" :title="'扫描阅卷 · ' + (scanRow ? scanRow.title : '')" size="min(620px,100vw)">
      <el-table :data="scanRoster" size="small" style="margin-bottom:14px">
        <el-table-column prop="studentId" label="学生ID" width="90" />
        <el-table-column label="作业状态" width="90">
          <template #default="{ row }">{{ ['待完成', '已提交', '已批改'][row.status] }}</template>
        </el-table-column>
        <el-table-column label="扫描件" width="80">
          <template #default="{ row }">{{ row.scanCount }} 份</template>
        </el-table-column>
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-upload :show-file-list="false" accept=".jpg,.jpeg,.png,.pdf" :http-request="opt => uploadScan(row, opt)">
              <el-button size="small" type="primary" plain>上传扫描件</el-button>
            </el-upload>
            <el-button v-if="row.scanCount" size="small" @click="loadScans(row.studentId)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!scanList.length" description="选择学生后上传或查看扫描件" />
      <div v-for="s in scanList" :key="s.id" style="display:flex;gap:8px;align-items:center;padding:8px 0;border-bottom:1px solid var(--line)">
        <span style="font-size:12px">#{{ s.id }} · {{ s.fileName }} · {{ (s.sizeBytes / 1024).toFixed(0) }}KB · {{ s.status }}</span>
        <el-button size="small" text type="primary" @click="previewScan(s)">预览</el-button>
        <el-button v-if="s.status === 'UPLOADED'" size="small" type="primary" plain @click="aiRecognize(s)">AI 识别</el-button>
        <el-button v-if="s.status === 'UPLOADED'" size="small" @click="openTranscribe(s)">转录导入</el-button>
        <el-button v-if="s.status === 'RECOGNIZED'" size="small" type="success" @click="importScan(s)">导入落账</el-button>
        <el-button v-if="s.status === 'RECOGNIZED'" size="small" @click="openTranscribe(s)">修正导入</el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="transcribeVisible"
               :title="(transcribeScan && transcribeScan.status === 'RECOGNIZED' ? '修正识别结果 · #' : '人工转录 · #') + (transcribeScan ? transcribeScan.id : '')"
               width="min(540px,94vw)">
      <p style="font-size:12px;color:var(--text-2);margin:0 0 8px">
        每行一条：<b>题目ID=学生作答</b>。本作业题目ID：{{ scanRow ? scanRow.questionIds : '' }}。
        提交后客观题自动判分，解答题转待人工批改。
      </p>
      <el-input v-model="transcribeText" type="textarea" :rows="6" placeholder="101=A&#10;102=对&#10;103=对顶角相等" />
      <template #footer>
        <el-button @click="transcribeVisible = false">取消</el-button>
        <el-button type="primary" :loading="busyTranscribe" @click="submitTranscribe">提交并导入落账</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/request'

const subjects = ['语文', '数学', '英语', '物理', '化学', '生物', '政治', '历史', '地理']
const form = reactive({ title: '', subjectName: '数学', deadlineHours: 168, qidsText: '' })
const busy = ref(false)
const mine = ref([])
const rosterVisible = ref(false), rosterOpen = ref(null), rosterText = ref('')
const reportVisible = ref(false), report = ref(null)
// 学生个体学情（考后诊断，docs/26 F-XKW-12）：班级报告名单 → 查看 → 个体报告抽屉
const studentReportVisible = ref(false), studentReport = ref(null)

onMounted(loadMine)

async function loadMine() {
  try { mine.value = await http.get('/assignments/mine') } catch { mine.value = [] }
}

async function create() {
  const qids = form.qidsText.split(/[,,\s]+/).map(s => s.trim()).filter(Boolean).map(Number)
  if (!form.title.trim() || !qids.length) { ElMessage.warning('请填写标题和至少一个题目ID'); return }
  busy.value = true
  try {
    await http.post('/assignments', {
      title: form.title.trim(),
      subjectId: subjects.indexOf(form.subjectName) + 1,
      questionIds: qids, deadlineHours: form.deadlineHours
    })
    ElMessage.success('已创建草稿')
    form.title = ''; form.qidsText = ''
    loadMine()
  } catch (_) {} finally { busy.value = false }
}

async function publish(row) {
  await http.post('/assignments/' + row.id + '/publish')
  ElMessage.success('已发布'); loadMine()
}
async function close(row) {
  await http.post('/assignments/' + row.id + '/close')
  ElMessage.success('已关闭'); loadMine()
}
function openRoster(row) { rosterOpen.value = row; rosterText.value = ''; rosterVisible.value = true }
async function assignRoster() {
  const ids = rosterText.value.split(/[^0-9]+/).filter(Boolean).map(Number)
  if (!ids.length) { ElMessage.warning('请输入学生ID'); return }
  const d = await http.post('/assignments/' + rosterOpen.value.id + '/students', { studentIds: ids })
  ElMessage.success(`新增 ${d.added} 人，名单共 ${d.roster} 人`)
  rosterVisible.value = false
}
async function openReport(row) {
  report.value = await http.get('/assignments/' + row.id + '/report')
  reportVisible.value = true
}
async function openStudentReport(studentId) {
  try {
    studentReport.value = await http.get('/assignments/students/' + studentId + '/report')
    studentReportVisible.value = true
  } catch (_) {}
}
async function makeSheet(row) {
  // 下载端点需要 JWT：用 blob 拉取再触发浏览器保存
  const d = await http.get('/assignments/' + row.id + '/answer-sheet')
  const raw = await fetch(d.downloadUrl, { headers: { Authorization: 'Bearer ' + localStorage.getItem('examforge_token') } })
  if (!raw.ok) { ElMessage.error('答题卡下载失败'); return }
  const blob = await raw.blob()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'answer-sheet-' + row.id + '.' + (d.format === 'PDF' ? 'pdf' : 'html')
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success(`答题卡已生成（${d.format}，${d.questionCount} 题）`)
}

// ---- 扫描阅卷（扫描件=批改证据层 + 识别结果导入落账，docs/26 §7 二阶段）----
const scanVisible = ref(false), scanRow = ref(null), scanRoster = ref([]), scanList = ref([])
const transcribeVisible = ref(false), transcribeScan = ref(null), transcribeText = ref(''), busyTranscribe = ref(false)
async function openScans(row) {
  scanRow.value = row
  scanVisible.value = true
  scanRoster.value = await http.get('/assignments/' + row.id + '/students')
  scanList.value = []
}
async function loadScans(studentId) {
  scanList.value = await http.get('/assignments/' + scanRow.value.id + '/scans', { params: { studentId } })
}
async function uploadScan(row, opt) {
  const fd = new FormData()
  fd.append('file', opt.file)
  await http.post('/assignments/' + scanRow.value.id + '/students/' + row.studentId + '/scan', fd)
  ElMessage.success('扫描件已上传')
  loadScans(row.studentId)
  scanRoster.value = await http.get('/assignments/' + scanRow.value.id + '/students')
}
async function previewScan(s) {
  // 文件端点需 JWT：blob 拉取再新窗口预览
  const raw = await fetch('/api/v1/assignments/' + scanRow.value.id + '/scans/' + s.id + '/file',
    { headers: { Authorization: 'Bearer ' + localStorage.getItem('examforge_token') } })
  if (!raw.ok) { ElMessage.error('预览失败'); return }
  window.open(URL.createObjectURL(await raw.blob()))
}
async function aiRecognize(s) {
  // MOCK 渠道返回未识别→降级转录导入；配置 OPENAI_COMPAT 视觉模型后自动识别
  const r = await http.post('/assignments/' + scanRow.value.id + '/scans/' + s.id + '/ai-recognize')
  if (r.recognized) {
    ElMessage.success(`AI 已识别 ${r.answers.length} 题作答，可导入落账`)
  } else {
    ElMessage.warning(r.reason || 'AI 未能识别，请人工转录')
  }
  loadScans(s.studentId)
}
async function importScan(s) {
  const r = await http.post('/assignments/' + scanRow.value.id + '/scans/' + s.id + '/import', {})
  ElMessage.success(`已导入 ${r.imported} 题：客观题对 ${r.correctObjective}、待人工 ${r.pendingManual}${r.graded ? '，已全部批改 ✓' : ''}`)
  loadScans(s.studentId)
  scanRoster.value = await http.get('/assignments/' + scanRow.value.id + '/students')
}
function openTranscribe(s) { transcribeScan.value = s; transcribeText.value = ''; transcribeVisible.value = true }
async function submitTranscribe() {
  const answers = []
  for (const l of transcribeText.value.split(/\n+/).map(x => x.trim()).filter(Boolean)) {
    const m = l.split(/[=：:]/)
    if (!m[0] || !Number(m[0]) || m.length < 2) { ElMessage.warning('格式：题目ID=作答，如 101=A'); return }
    answers.push({ questionId: Number(m[0]), answer: m.slice(1).join('=') })
  }
  if (!answers.length) { ElMessage.warning('请至少录入一条作答'); return }
  busyTranscribe.value = true
  try {
    const json = JSON.stringify(answers)
    const scan = transcribeScan.value
    if (scan.status === 'UPLOADED') {
      // 人工转录：先落 RECOGNIZED（原文字符串体），再导入落账
      await http.post('/assignments/' + scanRow.value.id + '/scans/' + scan.id + '/recognize', json,
        { headers: { 'Content-Type': 'text/plain' } })
    }
    const r = await http.post('/assignments/' + scanRow.value.id + '/scans/' + scan.id + '/import',
      scan.status === 'RECOGNIZED' ? { ocrJson: json } : {})
    transcribeVisible.value = false
    ElMessage.success(`已导入 ${r.imported} 题：客观题对 ${r.correctObjective}、待人工 ${r.pendingManual}${r.graded ? '，已全部批改 ✓' : ''}`)
    loadScans(scan.studentId)
    scanRoster.value = await http.get('/assignments/' + scanRow.value.id + '/students')
  } finally { busyTranscribe.value = false }
}
</script>

<style scoped>
.assign-page{max-width:1250px;margin:auto}
</style>
