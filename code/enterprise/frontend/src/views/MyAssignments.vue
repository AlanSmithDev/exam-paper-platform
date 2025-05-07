<template>
  <div class="hw-page">
    <header><h1 class="page-title">我的作业</h1><p class="page-subtitle">老师在名单中点名后，作业会出现在这里；客观题即时判分，解答题由老师批改</p>
      <el-button size="small" type="success" plain style="margin-top:8px" @click="openReport">我的学情</el-button>
    </header>

    <el-card v-for="a in list" :key="a.assignmentId" style="margin-bottom:14px">
      <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap">
        <div>
          <b>{{ a.title }}</b>
          <div style="font-size:12px;color:var(--text-2);margin-top:4px">
            截止：{{ a.deadline || '不限' }}
            <el-tag v-if="a.late" size="small" type="warning" style="margin-left:6px">迟交</el-tag>
          </div>
        </div>
        <div style="display:flex;gap:8px;align-items:center">
          <el-tag size="small" :type="a.myStatus === 2 ? 'success' : a.myStatus === 1 ? 'warning' : 'info'">
            {{ ['待完成', '已提交', '已批改'][a.myStatus] }}
          </el-tag>
          <b v-if="a.myStatus === 2 && a.score >= 0" style="color:var(--primary)">{{ a.score }}%</b>
          <el-button size="small" type="primary" :disabled="a.myStatus !== 0" @click="open(a.assignmentId)">
            {{ a.myStatus === 0 ? '进入作答' : '回看题目' }}
          </el-button>
        </div>
      </div>
    </el-card>
    <el-empty v-if="!list.length" description="暂无作业：老师在名单中点名后显示" />

    <el-drawer v-model="open_" :title="detail.title" size="min(560px,100vw)">      <p v-if="detail.deadline" style="font-size:12px;color:var(--text-2)">截止：{{ detail.deadline }}</p>
      <el-alert v-if="!detail.answerable" :title="detail.alreadySubmitted ? '已提交，等待老师批改（可回看题目）' : '作业未开放或已关闭'"
                :type="detail.alreadySubmitted ? 'success' : 'warning'" :closable="false" style="margin-bottom:12px" />
      <div v-for="(q, i) in detail.questions" :key="q.questionId" class="q">
        <div class="qhead"><b>{{ i + 1 }}.</b> <el-tag size="small">{{ q.type || '题目' }}</el-tag></div>
        <div class="stem" v-html="renderMath(q.stem)"></div>
        <template v-if="isChoice(q.options)">
          <el-radio-group v-model="answers[q.questionId]" :disabled="!detail.answerable" style="margin:6px 0">
            <el-radio v-for="o in parseOptions(q.options)" :key="o.label" :value="o.label" style="display:block;margin:4px 0">
              <b>{{ o.label }}.</b> <span v-html="renderMath(o.content)"></span>
            </el-radio>
          </el-radio-group>
        </template>
        <el-input v-else v-model="answers[q.questionId]" type="textarea" :rows="3"
                  :disabled="!detail.answerable" placeholder="在此作答（解答题由老师批改）" style="margin:6px 0" />
      </div>
      <template #footer v-if="detail.answerable">
        <el-button @click="open_ = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submit">提交作业</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="reportOpen" title="我的学情（考后诊断）" size="min(560px,100vw)">
      <template v-if="report">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="参与作业">{{ report.assignments }} 次</el-descriptions-item>
          <el-descriptions-item label="累计正确率">{{ report.overall.correctRatePct }}%</el-descriptions-item>
          <el-descriptions-item label="答对 / 答错">{{ report.overall.correct }} / {{ report.overall.wrong }}</el-descriptions-item>
          <el-descriptions-item label="待批改">{{ report.overall.pending }} 题</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin:14px 0 6px">薄弱知识点</h4>
        <div>
          <el-tag v-for="k in report.weakKnowledgePoints" :key="k.kp" size="small"
                  :type="k.correctRatePct < 60 ? 'danger' : 'info'" style="margin:0 6px 6px 0">
            {{ k.kp }} · 错{{ k.wrong }} · 正确率{{ k.correctRatePct }}%
          </el-tag>
          <span v-if="!report.weakKnowledgePoints?.length" style="font-size:12px;color:var(--text-2)">暂无作答数据</span>
        </div>
        <h4 style="margin:14px 0 6px">最近作业</h4>
        <el-table :data="report.trend" size="small">
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
          <div v-for="w in report.wrongBook" :key="w.questionId" class="wrong-row">
            <router-link :to="'/questions/' + w.questionId">题目 #{{ w.questionId }}</router-link>
            <span class="meta">{{ w.kpNames || '无知识点' }} · 错 {{ w.wrongCount }} 次</span>
          </div>
          <span v-if="!report.wrongBook?.length" style="font-size:12px;color:var(--text-2)">错题本已清空，继续保持！</span>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/request'
import { renderMath } from '../utils/math'

const list = ref([])
const open_ = ref(false)
const detail = reactive({ assignmentId: null, title: '', deadline: '', answerable: false, alreadySubmitted: false, questions: [] })
const answers = reactive({})
const busy = ref(false)
// 我的学情（考后诊断：跨作业正确率/薄弱知识点/趋势/错题本 TOP，docs/26 F-XKW-12）
const reportOpen = ref(false), report = ref(null)

onMounted(loadMine)

async function loadMine() {
  try { list.value = await http.get('/assignments/my') } catch { list.value = [] }
}

async function openReport() {
  try { report.value = await http.get('/assignments/my/report'); reportOpen.value = true } catch (_) {}
}

async function open(id) {
  const d = await http.get('/assignments/' + id + '/questions')
  Object.assign(detail, d)
  Object.keys(answers).forEach(k => delete answers[k])
  for (const q of d.questions) answers[q.questionId] = ''
  open_.value = true
}

function isChoice(optionsJson) {
  try { return Array.isArray(JSON.parse(optionsJson || 'null')) && JSON.parse(optionsJson).length > 0 } catch { return false }
}
function parseOptions(optionsJson) {
  try { return JSON.parse(optionsJson || '[]') } catch { return [] }
}

async function submit() {
  const payload = detail.questions
          .filter(q => String(answers[q.questionId] ?? '').trim() !== '')
          .map(q => ({ questionId: q.questionId, answer: String(answers[q.questionId]) }))
  if (!payload.length) { ElMessage.warning('请至少作答一题'); return }
  busy.value = true
  try {
    const r = await http.post('/assignments/' + detail.assignmentId + '/submit', payload)
    ElMessage.success(`已提交：客观题 ${r.correctObjective}/${r.total}` +
            (r.pendingManual > 0 ? `，${r.pendingManual} 题待老师批改` : '') +
            (r.late ? '（迟交已标记）' : '') + `，正确率 ${r.correctRatePct}%`)
    open_.value = false
    loadMine()
  } catch (_) {} finally { busy.value = false }
}
</script>

<style scoped>
.hw-page{max-width:980px;margin:auto}
.q{border-top:1px solid var(--line);padding:14px 0}
.qhead{display:flex;gap:8px;align-items:center;margin-bottom:6px}
.stem{line-height:1.9;overflow-wrap:anywhere}
.wrong-row{display:flex;justify-content:space-between;align-items:center;gap:10px;padding:7px 0;border-bottom:1px dashed var(--line);font-size:13px}
.wrong-row .meta{font-size:12px;color:var(--text-2)}
</style>
