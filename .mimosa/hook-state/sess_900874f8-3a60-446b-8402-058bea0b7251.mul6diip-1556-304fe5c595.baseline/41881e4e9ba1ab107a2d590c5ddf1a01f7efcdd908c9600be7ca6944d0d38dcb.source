// 网络工具（仅提取客户端标识用于限流计数与审计入库；本模块不发起任何出站网络请求）
// 说明：X-Forwarded-For 仅信任第一跳（生产部署在可信反向代理之后，docs/19 §9）；
// 该值不做任何 fetch/http 调用，不存在 SSRF 面——仅作为 Map key 与参数绑定入库。
function clientIp(req) {
  return String(req.headers['x-forwarded-for'] || req.socket.remoteAddress || '-')
    .split(',')[0].trim().slice(0, 64) || '-';
}

module.exports = { clientIp };
