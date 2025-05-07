<template>
  <div>
    <el-card style="max-width:860px;margin:20px auto">
      <h3>🔍 AI 搜 · 自然语言搜题</h3>
      <div style="display:flex;gap:10px;margin-top:10px">
        <el-input v-model="query" placeholder="例：高二 立体几何 解答题 较难" @keyup.enter="doSearch" />
        <el-button type="primary" @click="doSearch">AI 搜</el-button>
      </div>
      <p class="meta" style="margin-top:8px">AI 自动解析难度/题型/知识点后检索；0 结果时可用「AI 出同款」</p>
      <el-alert v-if="parsed" :title="'已解析：' + JSON.stringify(parsed)" type="info" :closable="false" style="margin-top:10px" />

      <el-divider style="margin:18px 0 12px" />
      <h4 style="margin:0 0 10px">📷 拍照搜题</h4>
      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <label class="photo-btn">
          选择 / 拍摄题目图片
          <input type="file" accept="image/jpeg,image/png,image/webp" hidden @change="onPhoto" />
        </label>
        <span v-if="photoName" class="meta">已选：{{ photoName }}</span>
        <el-button v-if="photoData" type="success" :loading="photoBusy" @click="doPhotoSearch">识别并搜题</el-button>
      </div>
      <el-input v-model="photoHint" placeholder="识别失败时的文字补充（可选）：把题目文字打出来再搜" style="margin-top:10px" />
      <div v-if="photoInfo" style="margin-top:10px">
        <el-alert v-if="photoInfo.recognized" type="success" :closable="false"
                  :title="'已识别题干：' + photoInfo.stem" />
        <el-alert v-else type="warning" :closable="false"
                  :title="photoInfo.reason || '未能识别图片中的题目，可填写文字补充后重试'" />
        <p v-if="photoInfo.keywords && photoInfo.keywords.length" class="meta">
          检索关键词：{{ photoInfo.keywords.join(' / ') }}
        </p>
      </div>
      <el-divider style="margin:18px 0 12px" />
      <h4 style="margin:0 0 10px">📝 AI 教学设计草稿（TJ-42）</h4>
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
        <el-select v-model="tdGrade" size="small" style="width:110px">
          <el-option v-for="g in ['小学','初中','高中']" :key="g" :label="g" :value="g" />
        </el-select>
        <el-input v-model="tdSubject" placeholder="学科" size="small" style="width:100px" />
        <el-input v-model="tdKp" placeholder="知识点（必填），如：函数的单调性" size="small" style="flex:1;min-width:220px" />
        <el-button size="small" type="primary" :loading="tdBusy" @click="genDesign">生成草稿</el-button>
      </div>
      <el-input v-model="tdStem" type="textarea" :rows="2" placeholder="例题题干（可选，围绕它设计教学环节）" style="margin-top:8px" />
      <div v-if="td" style="margin-top:10px">
        <el-alert type="success" :closable="false" :title="td.gradeLevel + ' · ' + td.subject + ' · ' + td.kp" />
        <h5 style="margin:10px 0 4px">教学目标</h5>
        <ul class="td-list"><li v-for="(o, i) in td.objectives" :key="'o'+i">{{ o }}</li></ul>
        <h5 style="margin:10px 0 4px">重难点</h5>
        <ul class="td-list">
          <li v-for="(k, i) in td.keyPoints" :key="'k'+i">重点：{{ k }}</li>
          <li v-for="(d, i) in td.difficulties" :key="'d'+i">难点：{{ d }}</li>
        </ul>
        <h5 style="margin:10px 0 4px">教学过程（四环节）</h5>
        <el-table :data="td.process" size="small">
          <el-table-column prop="title" label="环节" width="150" />
          <el-table-column prop="detail" label="设计" min-width="280" />
        </el-table>
        <h5 style="margin:10px 0 4px">板书设计</h5>
        <div v-for="(b, i) in td.board" :key="'b'+i" style="font-size:13px;line-height:1.8">{{ b }}</div>
        <p class="meta" style="margin-top:8px">AIGC 草稿仅供教研参考，使用前请人工复核（消耗 1 次 AI 配额）</p>
      </div>
      <p class="meta" style="margin-top:8px">图片仅用于识别，jpg/png/webp ≤ 8MB；每次拍照搜题消耗 1 次 AI 配额</p>
    </el-card>

    <div style="max-width:860px;margin:0 auto" v-if="list.length">
      <div class="q-card" v-for="q in list" :key="q.id">
        <div class="meta">
          {{ q.type }} ｜ 难度 {{ q.difficulty }} ｜ 系数 {{ q.coefficient }} ｜ {{ q.kpNames }}
          <el-tag v-if="q.matchScore != null" size="small" style="margin-left:6px"
                  :type="q.matchScore >= 0.6 ? 'success' : 'info'">匹配度 {{ (q.matchScore * 100).toFixed(0) }}%</el-tag>
        </div>
        <div class="q-stem" v-html="render(q.stem)"></div>
        <el-button size="small" style="margin-top:8px" @click="$router.push('/questions/' + q.id)">查看详情与解析</el-button>
      </div>
      <el-empty v-if="!list.length" description="没有找到合适题目 —— 可用「AI 出同款」直接生成" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import katex from 'katex'
import http from '../api/request'

const query = ref(''), list = ref([]), parsed = ref(null)

async function doSearch() {
  const d = await http.post('/ai/search', { query: query.value })
  parsed.value = d.parsed
  list.value = d.list
}

// ---------- 拍照搜题（docs/23 §3A）：选图 → base64 → /ai/photo-search；失败可填文字 hint 走降级检索 ----------
const photoData = ref(''), photoName = ref(''), photoMime = ref(''), photoHint = ref(''),
      photoBusy = ref(false), photoInfo = ref(null)

function onPhoto(e) {
  const f = e.target.files && e.target.files[0]
  if (!f) return
  if (f.size > 8 * 1024 * 1024) { ElMessage.warning('图片超过 8MB 上限，请压缩后重试'); return }
  photoName.value = f.name
  photoMime.value = f.type || ''
  const r = new FileReader()
  r.onload = () => { photoData.value = String(r.result || '') }   // data URL 原样上传，后端剥离前缀
  r.readAsDataURL(f)
}

async function doPhotoSearch() {
  photoBusy.value = true
  try {
    const d = await http.post('/ai/photo-search', {
      imageBase64: photoData.value,
      mime: photoMime.value,
      hint: photoHint.value || undefined
    })
    photoInfo.value = d
    list.value = d.list || []
    if (!d.recognized && !d.hintUsed) ElMessage.warning(d.reason || '未能识别图片中的题目')
  } catch (err) {
    if (err && err.response && err.response.status === 429) {
      ElMessage.warning((err.response.data && err.response.data.message) || '今日 AI 配额已用完')
    }
  } finally { photoBusy.value = false }
}

function render(s) {
  if (!s) return ''
  return String(s).replace(/\\\((.+?)\\\)/gs, (_, tex) => {
    try { return katex.renderToString(tex, { throwOnError: false }) } catch (_) { return _ }
  })
}

// ---------- AI 教学设计草稿（TJ-42，docs/26 §8）：学段/学科/知识点 → 结构化教案（MOCK 渠道确定性输出） ----------
const tdGrade = ref('高中'), tdSubject = ref('数学'), tdKp = ref(''), tdStem = ref(''),
      tdBusy = ref(false), td = ref(null)

async function genDesign() {
  if (!tdKp.value.trim()) { ElMessage.warning('请填写知识点'); return }
  tdBusy.value = true
  try {
    td.value = await http.post('/ai/teaching-design', {
      gradeLevel: tdGrade.value, subject: tdSubject.value, kp: tdKp.value.trim(), stem: tdStem.value || undefined
    })
  } catch (_) {} finally { tdBusy.value = false }
}
</script>

<style scoped>
.photo-btn{display:inline-flex;align-items:center;gap:6px;padding:7px 14px;border:1px dashed var(--line,#dcdfe6);
  border-radius:6px;background:#fff;cursor:pointer;font-size:13px;color:var(--text-1,#303133)}
.photo-btn:hover{border-color:var(--brand,#409eff);color:var(--brand,#409eff)}
.td-list{margin:0;padding-left:18px;font-size:13px;line-height:1.9}
</style>
