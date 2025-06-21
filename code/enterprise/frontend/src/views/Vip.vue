<template>
  <div>
    <el-card style="margin-bottom:14px">
      <h3>我的权益</h3>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="会员">{{ ent.memberActive ? '✅ ' + ent.plan : '未开通' }}</el-descriptions-item>
        <el-descriptions-item label="到期时间">{{ ent.expireTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="点数余额">{{ ent.pointBalance }}</el-descriptions-item>
        <el-descriptions-item label="今日免费下载剩余">{{ ent.freeDownloadsLeft }} 次</el-descriptions-item>
      </el-descriptions>
      <el-button type="success" size="small" style="margin-top:10px" @click="checkin">📅 每日签到 +1 点</el-button>
    </el-card>

    <el-card style="margin-bottom:14px">
      <h3>会员档位（对标组卷网，价格更优）</h3>
      <el-row :gutter="14">
        <el-col :span="6" v-for="p in plans" :key="p.planId">
          <el-card shadow="hover">
            <b>{{ p.name }}</b>
            <div style="font-size:24px;color:var(--primary)">¥{{ (p.priceCents / 100).toFixed(0) }}<small>/{{ p.durationDays }}天</small></div>
            <el-button type="primary" size="small" style="margin-top:8px" @click="buy(p)">立即开通</el-button>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-bottom:14px">
      <h3>领券中心</h3>
      <el-row :gutter="14">
        <el-col :span="8" v-for="t in coupons" :key="t.id">
          <el-card shadow="never">
            <b>{{ t.name }}</b>
            <div class="meta">
              {{ t.type === 'FULL_REDUCTION' ? '满 ' + (t.minSpendCents/100) + ' 减 ' + (t.discountCents/100) + ' 元'
                : t.type === 'DISCOUNT' ? (t.discountRate * 10) + ' 折' : '兑换 ' + t.discountCents + ' 点' }}
            </div>
            <el-tag v-if="t.dailyPerLimit > 0" size="small" type="warning" style="margin-left:6px">天天可领</el-tag>
            <el-button size="small" style="margin-top:8px" @click="claim(t)">领取</el-button>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-bottom:14px">
      <h3>会员激活码兑换</h3>
      <el-input v-model="cdkCode" placeholder="输入 12 位激活码，如 XXXX-XXXX-XXXX" style="max-width:360px" @keyup.enter="redeem" />
      <el-button type="primary" :loading="cdkBusy" style="margin-left:10px" @click="redeem">立即兑换</el-button>
    </el-card>

    <el-card style="margin-bottom:14px">
      <h3>积分任务中心</h3>
      <el-table :data="tasks" size="small">
        <el-table-column prop="name" label="任务" min-width="160" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }"><el-tag size="small" :type="row.daily ? 'success' : 'info'">{{ row.daily ? '每日' : '一次性' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="rewardPoints" label="奖励点数" width="90" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><el-tag size="small" :type="row.done ? 'info' : 'warning'">{{ row.done ? '已完成' : '待完成' }}</el-tag></template>
        </el-table-column>
        <el-table-column width="110">
          <template #default="{ row }">
            <el-button v-if="!row.done" size="small" type="primary" @click="completeTask(row)">完成</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card>
      <h3>我的优惠券</h3>
      <el-table :data="mine" size="small">
        <el-table-column prop="id" label="券ID" width="80" />
        <el-table-column prop="templateId" label="模板" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ ['未使用', '已使用', '已过期'][row.status] }}</template>
        </el-table-column>
        <el-table-column prop="expireTime" label="有效期至" />
      </el-table>
    </el-card>

    <!-- 渠道支付弹窗（D14：微信扫码 / 支付宝跳转 / 沙箱即时） -->
    <el-dialog v-model="payOpen" title="订单支付" width="360px" :close-on-click-modal="false" @closed="stopPoll">
      <div style="text-align:center">
        <template v-if="payMode === 'QR'">
          <img v-if="qrData" :src="qrData" alt="微信支付二维码" style="width:240px;height:240px" />
          <p class="pay-tip">请使用微信扫码支付，支付完成后自动生效</p>
        </template>
        <template v-else-if="payMode === 'REDIRECT'">
          <p class="pay-tip">已跳转支付宝完成支付；若未跳转
            <el-link type="primary" :href="payUrl" target="_blank">点此前往支付</el-link>
          </p>
        </template>
        <p v-else class="pay-tip">{{ payHint }}</p>
        <p v-if="payPolling" class="pay-tip">等待支付结果…</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import http from '../api/request'

const ent = ref({})
const plans = ref([])
const coupons = ref([])
const mine = ref([])
const tasks = ref([])
const cdkCode = ref('')
const cdkBusy = ref(false)

onMounted(async () => {
  ent.value = await http.get('/member/me')
  plans.value = await http.get('/member/plans')
  coupons.value = await http.get('/coupons/available')
  mine.value = await http.get('/coupons/mine')
  tasks.value = await http.get('/trade/tasks').catch(() => [])
})

async function buy(p) {
  const d = await http.post('/member/orders', { skuType: 'MEMBER', skuRef: p.planId, quantity: 1 },
    { headers: { 'X-Idempotency-Key': crypto.randomUUID() } })
  await pay(d.orderNo)
}

// ---------- 渠道支付（D14，docs/14 O-4）：prepay 凭证分流 → 沙箱即时 / 微信扫码轮询 / 支付宝跳转 ----------
const payOpen = ref(false), payMode = ref('MOCK_NOTIFY'), payUrl = ref(''), payHint = ref(''),
      qrData = ref(''), payPolling = ref(false), payTimer = ref(null)
let pollDeadline = 0

async function pay(orderNo) {
  const d = await http.post('/payments/' + orderNo + '/prepay', {})
  payOpen.value = true
  payUrl.value = d.payUrl || ''
  payHint.value = d.hint || ''
  if (d.mode === 'QR') {
    payMode.value = 'QR'
    qrData.value = await QRCode.toDataURL(d.codeUrl, { width: 240 })
    startPoll(orderNo)
  } else if (d.mode === 'REDIRECT') {
    payMode.value = 'REDIRECT'
    startPoll(orderNo)
    if (d.payUrl) window.open(d.payUrl, '_blank')
  } else {
    // 沙箱：mock-notify 即时回调（仅 provider=MOCK 可用；生产由渠道回调）
    await http.post('/payments/mock-notify', { orderNo })
    payMode.value = 'MOCK_NOTIFY'
    payHint.value = '支付成功（沙箱），会员已生效'
    stopPoll()
    ent.value = await http.get('/member/me')
  }
}

function startPoll(orderNo) {
  stopPoll()
  payPolling.value = true
  pollDeadline = Date.now() + 2 * 60 * 1000
  payTimer.value = setInterval(async () => {
    if (Date.now() > pollDeadline) { stopPoll(); ElMessage.warning('支付等待超时，支付成功后权益将自动生效'); return }
    try {
      const s = await http.get('/payments/' + orderNo + '/status')
      if (s.status === 'PAID') {
        stopPoll()
        payOpen.value = false
        ElMessage.success('支付成功，会员已生效')
        ent.value = await http.get('/member/me')
      }
    } catch (_) {}
  }, 2500)
}

function stopPoll() {
  if (payTimer.value) { clearInterval(payTimer.value); payTimer.value = null }
  payPolling.value = false
}
async function claim(t) {
  await http.post('/coupons/' + t.id + '/claim')
  ElMessage.success('领取成功')
  mine.value = await http.get('/coupons/mine')
}
async function checkin() {
  try {
    await http.post('/trade/checkin')
    ElMessage.success('签到成功，+1 点')
    ent.value = await http.get('/member/me')
  } catch (_) {}
}
async function redeem() {
  if (!cdkCode.value.trim()) { ElMessage.warning('请输入激活码'); return }
  cdkBusy.value = true
  try {
    const d = await http.post('/trade/cdk/redeem', { code: cdkCode.value.trim() })
    const r = d.reward || {}
    ElMessage.success('兑换成功：' + (r.type === 'MEMBER_DAYS' ? `会员 ${r.days} 天`
      : r.type === 'POINTS' ? `点数 +${r.points}` : `优惠券「${r.template}」`))
    cdkCode.value = ''
    ent.value = await http.get('/member/me')
  } catch (_) {} finally { cdkBusy.value = false }
}
async function completeTask(t) {
  const d = await http.post('/trade/tasks/' + t.taskKey + '/complete')
  if (d.duplicate) ElMessage.info('今日已完成该任务')
  else ElMessage.success(`完成「${t.name}」，+${d.rewardPoints} 点`)
  tasks.value = await http.get('/trade/tasks')
  ent.value = await http.get('/member/me')
}
</script>
