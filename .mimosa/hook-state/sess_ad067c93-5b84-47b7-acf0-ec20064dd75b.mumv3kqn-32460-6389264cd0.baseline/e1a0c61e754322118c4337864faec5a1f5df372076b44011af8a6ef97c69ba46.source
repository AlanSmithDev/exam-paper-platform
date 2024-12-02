export const normalizeType = type => type === '选择题' ? '单选题' : type

export function generateLocalPaper(pool, config, excluded = []) {
  const excludedIds = new Set(excluded)
  const candidates = pool.filter(q => !excludedIds.has(q.id) && (!config.chapters.length || config.chapters.includes(q.chapter || q.tags?.[0])) && (!config.scene || q.scene === config.scene))
    .sort((a, b) => Math.abs(a.difficulty - config.difficulty) - Math.abs(b.difficulty - config.difficulty) || b.year - a.year || a.id - b.id)
  const questions = [], warnings = [], used = new Set()
  for (const row of config.structure) {
    const count = Math.max(0, Math.min(30, Math.trunc(Number(row.count) || 0)))
    const matches = candidates.filter(q => normalizeType(q.type) === row.type && !used.has(q.id)).slice(0, count)
    matches.forEach(q => { used.add(q.id); questions.push({ ...q, score: Number(row.score) || 1 }) })
    if (matches.length < count) warnings.push(`${row.type}缺少 ${count - matches.length} 题`)
  }
  return { questions, warnings }
}
export function paperScore(questions) { return questions.reduce((sum, q) => sum + (Number(q.score) || 0), 0) }
