<template>
  <div>
    <el-card style="margin-bottom:14px">
      <h3>练习（按知识点自动出题 · 自动判分 · 错题自动入库）</h3>
      <el-input v-model="kp" placeholder="知识点，如：导数及其应用" style="width:240px;margin-right:8px" />
      <el-input-number v-model="count" :min="3" :max="20" />
      <el-button type="primary" style="margin-left:8px" @click="create">生成练习</el-button>
    </el-card>

    <el-card v-if="practice" style="margin-bottom:14px">
      <div v-for="(q, i) in practice.questions" :key="q.id" class="q-card">
        <b>{{ i + 1 }}.</b> <span class="meta">{{ q.type }} · 难度 {{ q.difficulty }}</span>
        <div class="q-stem" v-html="render(q.stem)"></div>
        <el-input v-model="answers[q.id]" placeholder="作答（选择填字母，判断填对/错）" style="margin-top:8px" />
      </div>
      <el-button type="primary" @click="submit">提交判分</el-button>
    </el-card>

    <el-card v-if="report">
      <h3>判分结果：正确率 {{ (report.correctRate * 100).toFixed(0) }}%（{{ report.correct }}/{{ report.total }}）</h3>
      <div v-for="d in report.detail" :key="d.questionId" class="meta">
        题 {{ d.questionId }}：<b :style="{color: d.correct ? 'var(--success)' : 'var(--danger)'}">{{ d.correct ? '✅ 正确' : '❌ 错误（已入错题本）' }}</b>
        ｜ 标准答案：{{ d.standard }}
      </div>
    </el-card>

    <el-card style="margin-top:14px">
      <h3>我的错题本（未解决）</h3>
      <el-table :data="wrongs" size="small">
        <el-table-column prop="questionId" label="题目ID" width="100" />
        <el-table-column prop="kpNames" label="知识点" />
        <el-table-column prop="wrongCount" label="错误次数" width="100" />
        <el-table-column prop="lastWrongAt" label="最近出错" width="170" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import katex from 'katex'
import { ElMessage } from 'element-plus'
import http from '../api/request'

const kp = ref('导数及其应用'), count = ref(5)
const practice = ref(null), answers = reactive({}), report = ref(null), wrongs = ref([])

async function create() {
  practice.value = await http.post('/practices', { kp: kp.value, count: count.value })
  Object.keys(answers).forEach(k => delete answers[k])
  report.value = null
}
async function submit() {
  const list = practice.value.questions.map(q => ({ questionId: q.id, answer: answers[q.id] || '' }))
  report.value = await http.post(`/practices/${practice.value.practiceId}/submit`, list)
  ElMessage.success('已判分')
  wrongs.value = await http.get('/wrong-questions', { params: { resolved: false } })
}
wrongs.value = []
http.get('/wrong-questions', { params: { resolved: false } }).then(d => { wrongs.value = d }).catch(() => {})

function render(s) {
  if (!s) return ''
  return String(s).replace(/\\\((.+?)\\\)/gs, (_, tex) => {
    try { return katex.renderToString(tex, { throwOnError: false }) } catch (_) { return _ }
  })
}
</script>
