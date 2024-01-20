import katex from 'katex'
export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]))
}
export function renderMath(value) {
  return String(value ?? '').split(/(\$[^$]+\$)/g).map(part => part.startsWith('$') && part.endsWith('$')
    ? katex.renderToString(part.slice(1, -1), { throwOnError: false, trust: false, strict: 'ignore' })
    : escapeHtml(part).replace(/\n/g, '<br>')).join('')
}
