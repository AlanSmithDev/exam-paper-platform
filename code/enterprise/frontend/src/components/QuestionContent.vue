<template>
  <div class="question-content">
    <div class="q-stem" v-html="renderMath(question.stem)"></div>
    <div v-if="question.options?.length" class="question-options"><div v-for="(option, i) in question.options" :key="i"><b>{{String.fromCharCode(65+i)}}.</b> <span v-html="renderMath(option)"></span></div></div>
    <figure v-if="question.figureUrl"><img :src="question.figureUrl" :alt="question.figureAlt || '题目配图'" loading="lazy"><figcaption>{{question.figureAlt}}</figcaption></figure>
    <div v-if="answers" class="question-answer"><b>参考答案</b><div v-html="renderMath(question.answer)"></div><template v-if="analysis"><b>解析</b><div v-html="renderMath(question.analysis)"></div></template></div>
  </div>
</template>
<script setup>
import { renderMath } from '../utils/math'
defineProps({ question: { type: Object, required: true }, answers: Boolean, analysis: Boolean })
</script>
<style scoped>
.question-content{min-width:0;line-height:1.9;overflow-wrap:anywhere}.q-stem{font-size:14px}.question-options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px 22px;margin-top:12px;font-size:13px}.question-options b{font-weight:500;margin-right:5px}figure{margin:16px 0}figure img{display:block;max-width:100%;width:300px;height:auto;background:#fff}figcaption{font-size:11px;color:var(--text-2)}.question-answer{display:grid;grid-template-columns:70px minmax(0,1fr);gap:10px;margin-top:18px;padding-top:14px;border-top:1px solid var(--line);font-size:13px}.question-answer b{font-weight:500;color:var(--primary)}:deep(.katex-html){white-space:normal}:deep(.katex){font-size:1.06em}@media(max-width:600px){.question-options{grid-template-columns:1fr}}
</style>
