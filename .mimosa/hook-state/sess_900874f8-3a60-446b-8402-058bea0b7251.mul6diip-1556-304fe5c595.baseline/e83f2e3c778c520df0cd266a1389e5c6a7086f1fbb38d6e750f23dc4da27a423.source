/* KaTeX 多 CDN 降级加载器（企业级可靠性）：
   1) 依次尝试 jsdelivr → unpkg → cdnjs；
   2) 加载完成前页面若有 renderMathInElement 调用，进入队列，加载完成后统一渲染；
   3) 全部 CDN 失败 → 保留 LaTeX 源码显示（不阻断页面）。 */
(function () {
  var CDNS = [
    { base: 'https://cdn.jsdelivr.net/npm/katex@0.16.9', d: 'dist/' },
    { base: 'https://unpkg.com/katex@0.16.9', d: 'dist/' },
    { base: 'https://cdnjs.cloudflare.com/ajax/libs/KaTeX/0.16.9', d: '' }
  ];
  var i = 0;

  // 渲染占位：katex 未就绪时收集调用，就绪后统一执行
  if (typeof window.renderMathInElement !== 'function') {
    window.renderMathInElement = function (el) {
      window.__ktxQueue = window.__ktxQueue || [];
      window.__ktxQueue.push(el);
    };
  }

  function loadCss(base, d) {
    var l = document.createElement('link');
    l.rel = 'stylesheet'; l.href = base + '/' + d + 'katex.min.css';
    document.head.appendChild(l);
  }
  function loadJs(src, ok, fail) {
    var s = document.createElement('script');
    s.src = src; s.async = false;
    s.onload = ok; s.onerror = fail;
    document.head.appendChild(s);
  }
  function tryLoad() {
    if (i >= CDNS.length) { console.warn('[katex-loader] 全部 CDN 不可用，页面保留 LaTeX 源码'); return; }
    var c = CDNS[i];
    loadCss(c.base, c.d);
    loadJs(c.base + '/' + c.d + 'katex.min.js', function () {
      loadJs(c.base + '/' + c.d + 'contrib/auto-render.min.js', function () {
        var real = window.renderMathInElement;
        window.renderMathInElement = real;
        var queue = window.__ktxQueue || [];
        window.__ktxQueue = [];
        queue.forEach(function (el) { try { real(el, { delimiters: [{ left: '\\(', right: '\\)', display: false }, { left: '$$', right: '$$', display: true }] }); } catch (e) { } });
        try { real(document.body, { delimiters: [{ left: '\\(', right: '\\)', display: false }, { left: '$$', right: '$$', display: true }] }); } catch (e) { }
      }, tryLoad);
    }, tryLoad);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', tryLoad);
  else tryLoad();
})();
