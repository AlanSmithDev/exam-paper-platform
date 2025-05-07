<template>
  <div><header class="library-head"><div><h1 class="page-title">试卷选题</h1><p class="page-subtitle">高中数学 · 原创样例套卷</p></div><el-button type="primary" @click="$router.push('/papers')">组卷工作台</el-button></header><div class="library-filters"><el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索试卷名称" clearable/><el-select v-model="category" aria-label="试卷类型"><el-option label="全部类型" value=""/><el-option v-for="name in ['阶段检测','专题练习','同步教学']" :key="name" :label="name" :value="name"/></el-select><el-select v-model="grade" aria-label="年级"><el-option label="全部年级" value=""/><el-option v-for="name in ['高一','高二','高三']" :key="name" :label="name" :value="name"/></el-select></div><div class="library-count">共 {{filtered.length}} 套试卷</div><article v-for="paper in filtered" :key="paper.id" class="paper-row"><div class="paper-symbol"><el-icon><Document/></el-icon></div><div class="paper-info"><h2>{{paper.title}}</h2><p>{{paper.year}} · {{paper.region}} · {{paper.grade}} · {{paper.questionIds.length}} 道题</p><el-tag size="small" type="info">{{paper.category}}</el-tag></div><div class="paper-actions"><el-button @click="preview=paper">整卷预览</el-button><el-button type="primary" plain @click="addPaper(paper)">整卷加入试题篮</el-button></div></article><el-empty v-if="!filtered.length" description="暂无匹配试卷"/>
    <el-drawer v-model="open" :title="preview?.title" size="min(860px,100vw)"><template v-if="preview"><div class="preview-top"><span>{{preview.questionIds.length}} 道题</span><el-button type="primary" @click="addPaper(preview)">全部加入试题篮</el-button></div><article v-for="(q,i) in previewQuestions" :key="q.id" class="preview-question"><div class="preview-meta"><b>{{i+1}}. {{q.type}}</b><el-button size="small" :disabled="basket.some(item=>item.id===q.id)" @click="addOne(q)">{{basket.some(item=>item.id===q.id)?'已加入':'加入试题篮'}}</el-button></div><QuestionContent :question="q"/></article></template></el-drawer>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { Search, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { demoPapers, demoQuestions } from '../mock'
import QuestionContent from '../components/QuestionContent.vue'
import { useWorkspace } from '../composables/workspace'
const {basket,addQuestions}=useWorkspace(),keyword=ref(''),category=ref(''),grade=ref(''),preview=ref(null)
const filtered=computed(()=>demoPapers.filter(p=>(!keyword.value||p.title.includes(keyword.value))&&(!category.value||p.category===category.value)&&(!grade.value||p.grade===grade.value)))
const open=computed({get:()=>!!preview.value,set:value=>{if(!value)preview.value=null}})
const questionsFor=paper=>paper.questionIds.map(id=>demoQuestions.find(q=>q.id===id)).filter(Boolean)
const previewQuestions=computed(()=>preview.value?questionsFor(preview.value):[])
function addPaper(paper){if(addQuestions(questionsFor(paper)))ElMessage.success('已加入试题篮，重复题目已忽略')}
function addOne(q){if(addQuestions([q]))ElMessage.success('已加入试题篮')}
</script>
<style scoped>
.library-head{display:flex;justify-content:space-between;gap:15px;align-items:center}.library-filters{display:flex;gap:12px;margin:24px 0}.library-filters .el-input{max-width:500px}.library-filters .el-select{width:160px}.library-count{color:var(--text-2);font-size:12px;margin:20px 0}.paper-row{display:flex;align-items:center;gap:20px;padding:24px 0;border-top:1px solid var(--line)}.paper-symbol{width:66px;height:82px;background:#e6f0f7;display:grid;place-items:center;border-left:3px solid #78a9cc;color:#3e7dae;font-size:29px;flex:none}.paper-info{flex:1;min-width:0}.paper-info h2{font-size:16px;margin:0;line-height:1.6}.paper-info p{font-size:12px;color:var(--text-2);margin:10px 0}.paper-actions{display:flex;gap:8px}.paper-actions .el-button+.el-button{margin:0}.preview-top,.preview-meta{display:flex;justify-content:space-between;align-items:center;gap:14px;font-size:12px}.preview-question{padding:20px 0;border-bottom:1px solid var(--line)}.preview-meta{margin-bottom:14px;color:var(--text-2)}@media(max-width:750px){.paper-row{flex-wrap:wrap}.paper-actions{width:100%;justify-content:flex-end}.library-filters{flex-wrap:wrap}.library-filters .el-input{max-width:none}.library-filters .el-select{width:calc(50% - 6px)}.library-head{align-items:flex-start}}
</style>
