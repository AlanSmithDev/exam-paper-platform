<template>
  <div class="space-page"><header><h1 class="page-title">个人空间</h1><p class="page-subtitle">云端试卷、本机收藏、试题篮与试卷草稿</p></header><div class="space-summary"><div><span>云端试卷</span><strong>{{papers.length}}</strong></div><div><span>收藏题目</span><strong>{{savedQuestions.length}}</strong></div><div><span>试题篮</span><strong>{{basket.length}}</strong></div><div><span>本机草稿</span><strong>{{draft?1:0}}</strong></div></div><el-tabs v-model="tab">
    <el-tab-pane label="云端试卷" name="cloud">
      <p class="cloud-hint">云端试卷保存于服务端，支持整卷插题、移除与考查范围分析（登录后可用）。</p>
      <el-button size="small" style="margin-bottom:14px" @click="loadPapers">刷新列表</el-button>
      <el-empty v-if="!papers.length" description="暂无云端试卷：可先通过 /api/v1/papers/generate 云端组卷，或等待工作台云端同步上线" />
      <article v-for="p in papers" :key="p.id" class="cloud-paper">
        <div class="cloud-head">
          <div class="draft-info"><strong>{{p.title}}</strong><p>#{{p.id}} · 满分 {{p.totalScore||0}} 分 · {{p.status===0?'编辑中':'已定稿'}} · {{(p.createdAt||'').replace('T',' ').slice(0,16)}}</p></div>
          <div class="cloud-actions">
            <el-button size="small" @click="toggleScope(p)">{{scopeOf(p.id)?'收起范围':'考查范围'}}</el-button>
            <el-button size="small" type="primary" :disabled="p.status!==0" @click="openInsert(p)">插题</el-button>
            <el-button size="small" type="success" @click="openExport(p)">导出</el-button>
            <el-button size="small" type="warning" plain @click="saveTemplate(p)">存为模板</el-button>
          </div>
        </div>
        <div v-if="scopeOf(p.id)" class="scope-bar">
          <el-tag v-for="s in scopeOf(p.id)" :key="s.kp" size="small" style="margin:3px 6px 3px 0">{{s.kp}} ×{{s.count}}</el-tag>
          <span v-if="!scopes[p.id]?.length" style="font-size:12px;color:var(--text-2)">本卷题目暂无知识点标签</span>
        </div>
        <div v-if="inserting?.id===p.id" class="insert-form">
          <el-input v-model="insertQid" placeholder="题目ID（题库 /questions/{id}）" style="width:220px" size="small" />
          <el-input v-model="insertPos" placeholder="插入位置（可选，1起始）" style="width:180px" size="small" />
          <el-input v-model="insertScore" placeholder="分值（可选）" style="width:120px" size="small" />
          <el-button size="small" type="primary" @click="doInsert(p)">插入</el-button>
          <el-button size="small" @click="inserting=null">取消</el-button>
        </div>
        <div v-if="exporting?.id===p.id" class="insert-form">
          <el-select v-model="exportPaper" size="small" style="width:130px"><el-option label="A4 纵向" value="A4"/><el-option label="A3 纵向" value="A3"/></el-select>
          <el-select v-model="exportCols" size="small" style="width:100px"><el-option label="单栏" :value="1"/><el-option label="双栏" :value="2"/></el-select>
          <el-select v-model="exportAns" size="small" style="width:180px"><el-option label="答案随题（教师卷）" value="INLINE"/><el-option label="答案分离末页" value="SEPARATED"/><el-option label="不含答案（学生卷）" value="NONE"/></el-select>
          <el-button size="small" type="primary" :loading="exportBusy" @click="doExport(p)">导出下载</el-button>
          <el-button size="small" @click="exporting=null">取消</el-button>
          <span class="export-hint">A3 双栏自动横向对折排版</span>
        </div>
      </article>
    </el-tab-pane>
    <el-tab-pane label="收藏题目" name="saved"><div class="tab-actions" v-if="savedQuestions.length"><el-button size="small" @click="addSaved">全部加入试题篮</el-button></div><article v-for="q in savedQuestions" :key="q.id" class="space-row"><el-tag size="small">{{q.type}}</el-tag><router-link :to="'/questions/'+q.id">{{plain(q.stem)}}</router-link><el-button :icon="StarFilled" circle text aria-label="取消收藏" @click="toggleSaved(q.id)"/></article><el-empty v-if="!savedQuestions.length" description="暂无收藏题目"/></el-tab-pane>
    <el-tab-pane label="试题篮" name="basket"><article v-for="q in basket" :key="q.id" class="space-row"><el-tag size="small">{{q.type}}</el-tag><router-link :to="'/questions/'+q.id">{{plain(q.stem)}}</router-link><el-button :icon="Delete" circle text aria-label="移除题目" @click="removeQuestion(q.id)"/></article><el-empty v-if="!basket.length" description="试题篮为空"/><div class="tab-actions" v-else><el-button type="primary" @click="$router.push('/papers')">进入组卷工作台</el-button></div></el-tab-pane>
    <el-tab-pane label="我的草稿" name="draft"><article v-if="draft" class="space-row"><el-icon><Document/></el-icon><div class="draft-info"><strong>{{draft.title}}</strong><p>{{draft.questions?.length||0}} 道题 · {{draft.updatedAt?new Date(draft.updatedAt).toLocaleString('zh-CN'):'本机保存'}}</p></div><el-button size="small" type="primary" @click="$router.push('/papers')">继续编辑</el-button></article><el-empty v-else description="暂无本机草稿"/></el-tab-pane>
    <el-tab-pane label="试卷模板" name="templates">
      <p class="cloud-hint">模板保存试卷的题目与分值快照，一键复制为新卷（docs/25 TJ-80/TJ-22）。</p>
      <el-button size="small" style="margin-bottom:14px" @click="loadTemplates">刷新模板</el-button>
      <el-empty v-if="!templates.length" description="暂无模板：在「云端试卷」中点「存为模板」创建" />
      <article v-for="t in templates" :key="t.id" class="space-row">
        <el-icon><Document/></el-icon>
        <div class="draft-info"><strong>{{t.name}}</strong><p>满分 {{t.totalScore}} 分 · {{(t.createdAt||'').replace('T',' ').slice(0,16)}}</p></div>
        <el-button size="small" type="primary" @click="applyTemplate(t)">模板出卷</el-button>
        <el-button size="small" type="danger" text @click="removeTemplate(t)">删除</el-button>
      </article>
    </el-tab-pane>
  </el-tabs></div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { StarFilled, Delete, Document } from '@element-plus/icons-vue'
import { cloneQuestions } from '../mock'
import { useWorkspace } from '../composables/workspace'
import http from '../api/request'
const {savedIds,basket,draft,toggleSaved,removeQuestion,addQuestions}=useWorkspace(),tab=ref('saved'),all=cloneQuestions()
const savedQuestions=computed(()=>all.filter(q=>savedIds.value.includes(q.id)))
function plain(stem){return String(stem||'').replace(/\$/g,'')}
function addSaved(){if(addQuestions(savedQuestions.value))ElMessage.success('收藏题目已加入试题篮')}

// ---------- 云端试卷（docs/26 F-XKW-05：/papers/mine + /scope + 插题/移除） ----------
const papers=ref([]),scopes=ref({}),openScopes=ref({}),inserting=ref(null)
const insertQid=ref(''),insertPos=ref(''),insertScore=ref('')
const scopeOf=id=>openScopes.value[id]?scopes.value[id]:null
onMounted(()=>{if(localStorage.getItem('examforge_token')){loadPapers();loadTemplates()}})
async function loadPapers(){
  try{papers.value=await http.get('/papers/mine')}catch{papers.value=[]}
  for(const p of papers.value) loadScope(p.id)
}
async function loadScope(id){
  try{const d=await http.get('/papers/'+id+'/scope');scopes.value={...scopes.value,[id]:d.scope||[]}}catch{scopes.value={...scopes.value,[id]:[]}}
}
function toggleScope(p){openScopes.value={...openScopes.value,[p.id]:!openScopes.value[p.id]};if(openScopes.value[p.id]&&!scopes.value[p.id])loadScope(p.id)}
function openInsert(p){inserting.value=p;insertQid.value='';insertPos.value='';insertScore.value=''}
async function doInsert(p){
  const qid=Number(insertQid.value)
  if(!qid){ElMessage.warning('请输入题目ID');return}
  try{
    await http.post('/papers/'+p.id+'/questions',{
      questionId:qid,
      position:insertPos.value?Number(insertPos.value):undefined,
      score:insertScore.value?Number(insertScore.value):undefined
    })
    ElMessage.success('插入成功，顺序与总分已更新')
    inserting.value=null
    papers.value=await http.get('/papers/mine')
    loadScope(p.id)
  }catch(_){}
}

// ---------- 导出版面（docs/22 C6：A4/A3 单双栏 + 答案三模式；下载端点需 JWT，blob 拉取再触发保存） ----------
const exporting=ref(null),exportPaper=ref('A4'),exportCols=ref(1),exportAns=ref('INLINE'),exportBusy=ref(false)
function openExport(p){exporting.value=p;exportPaper.value='A4';exportCols.value=1;exportAns.value='INLINE'}
async function doExport(p){
  exportBusy.value=true
  try{
    const d=await http.post('/papers/'+p.id+'/export',{paper:exportPaper.value,columns:exportCols.value,answerMode:exportAns.value})
    const raw=await fetch(d.downloadUrl,{headers:{Authorization:'Bearer '+localStorage.getItem('examforge_token')}})
    if(!raw.ok)throw new Error('下载失败')
    const a=document.createElement('a')
    a.href=URL.createObjectURL(await raw.blob())
    a.download=d.downloadUrl.split('/').pop()
    a.click();URL.revokeObjectURL(a.href)
    ElMessage.success('导出成功，已开始下载')
    exporting.value=null
  }catch(err){
    if(err?.response?.status===429)ElMessage.warning(err.response?.data?.message||'点数不足，请充值或升级会员')
  }finally{exportBusy.value=false}
}

// ---------- 试卷模板（docs/25 TJ-80/TJ-22：存为模板 / 模板选题 / 删除） ----------
const templates = ref([])
async function loadTemplates(){
  try{templates.value=await http.get('/papers/templates')}catch{templates.value=[]}
}
async function saveTemplate(p){
  try{
    const name = await ElMessageBox.prompt('模板名称（将保存本卷题目与分值快照）', '存为模板', {
      inputValue: p.title, confirmButtonText: '保存', cancelButtonText: '取消'
    })
    const d = await http.post('/papers/'+p.id+'/save-template', { name: name.value })
    ElMessage.success(`已存为模板「${d.name}」（${d.count} 题 · ${d.totalScore} 分）`)
    tab.value = 'templates'
    loadTemplates()
  }catch(e){ if(e !== 'cancel' && e?.message !== 'cancel') {} }
}
async function applyTemplate(t){
  try{
    const d = await http.post('/papers/templates/'+t.id+'/apply', {})
    ElMessage.success(`已生成新卷「${d.title}」（${d.count} 题 · ${d.totalScore} 分）`)
    tab.value = 'cloud'
    papers.value = await http.get('/papers/mine')
  }catch(_){}
}
async function removeTemplate(t){
  try{ await http.delete('/papers/templates/'+t.id); loadTemplates() }catch(_){}
}
</script>
<style scoped>
.space-page{max-width:1250px;margin:auto}.space-summary{display:grid;grid-template-columns:repeat(4,1fr);margin:24px 0;border-block:1px solid var(--line);background:white}.space-summary>div{padding:22px}.space-summary span{font-size:12px;color:var(--text-2)}.space-summary strong{display:block;font-size:26px;margin-top:10px}.space-row{display:flex;gap:16px;align-items:center;padding:20px 0;border-bottom:1px solid var(--line)}.space-row>a{flex:1;min-width:0;font-size:13px;color:#415d76;text-decoration:none;overflow-wrap:anywhere;line-height:1.8}.space-row>.el-tag,.space-row>.el-button{flex:none}.draft-info{flex:1;min-width:0}.draft-info strong{font-size:14px;overflow-wrap:anywhere}.draft-info p{font-size:12px;color:var(--text-2)}.tab-actions{margin:15px 0}.cloud-hint{font-size:12px;color:var(--text-2);margin-bottom:12px}.cloud-paper{border:1px solid var(--line);border-radius:8px;background:#fff;padding:16px;margin-bottom:14px}.cloud-head{display:flex;justify-content:space-between;align-items:center;gap:12px}.cloud-actions{display:flex;gap:8px;flex:none}.scope-bar{border-top:1px dashed var(--line);margin-top:12px;padding-top:12px}.insert-form{display:flex;gap:8px;flex-wrap:wrap;align-items:center;border-top:1px dashed var(--line);margin-top:12px;padding-top:12px}.export-hint{font-size:12px;color:var(--text-2)}@media(max-width:500px){.space-summary>div{padding:16px}.space-row{gap:9px}.cloud-head{flex-wrap:wrap}}
</style>
