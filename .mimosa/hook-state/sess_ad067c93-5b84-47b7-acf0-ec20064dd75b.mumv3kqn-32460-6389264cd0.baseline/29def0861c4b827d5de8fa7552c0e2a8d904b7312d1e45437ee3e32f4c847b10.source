import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

export function readLocal(key, fallback, valid = () => true) {
  try {
    const value = JSON.parse(localStorage.getItem(key) || 'null')
    return value !== null && valid(value) ? value : fallback
  } catch { return fallback }
}
export function writeLocal(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); return true }
  catch { ElMessage.error('本机存储不可用或空间不足，请先导出当前内容'); return false }
}
const isQuestions = value => Array.isArray(value) && value.every(q => q && Number.isFinite(q.id) && typeof q.stem === 'string')
const basket = ref(readLocal('examforge_basket', [], isQuestions))
const savedIds = ref(readLocal('examforge_saved_ids', [], Array.isArray))
const draft = ref(readLocal('examforge_paper_draft', null, value => typeof value === 'object' && !Array.isArray(value)))
watch(basket, value => {
  writeLocal('examforge_basket', value)
  writeLocal('examforge_basket_ids', value.map(q => q.id))
}, { deep: true })
watch(savedIds, value => writeLocal('examforge_saved_ids', value), { deep: true })
window.addEventListener('storage', event => {
  if (event.key === 'examforge_basket') basket.value = readLocal('examforge_basket', [], isQuestions)
  if (event.key === 'examforge_saved_ids') savedIds.value = readLocal('examforge_saved_ids', [], Array.isArray)
  if (event.key === 'examforge_paper_draft') draft.value = readLocal('examforge_paper_draft', null)
})
function addQuestions(items) {
  const unique = items.filter((q, index) => !basket.value.some(item => item.id === q.id) && items.findIndex(item => item.id === q.id) === index)
  if (basket.value.length + unique.length > 100) { ElMessage.warning('试题篮最多保存 100 道题'); return false }
  basket.value.push(...unique)
  return true
}
function removeQuestion(id) { basket.value = basket.value.filter(q => q.id !== id) }
function toggleSaved(id) { savedIds.value = savedIds.value.includes(id) ? savedIds.value.filter(item => item !== id) : [...savedIds.value, id] }
function saveDraft(value) {
  const snapshot = { ...value, updatedAt: new Date().toISOString() }
  if (!writeLocal('examforge_paper_draft', snapshot)) return false
  draft.value = snapshot
  return true
}
export function useWorkspace() { return { basket, savedIds, draft, addQuestions, removeQuestion, toggleSaved, saveDraft } }
