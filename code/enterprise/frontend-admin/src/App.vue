<template>
  <!-- 超管端：登录 + 六大模块（广告位/题目/用户/公告/设置/审计），对接 examforge-admin / examforge-user -->
  <div v-if="!token" class="login">
    <el-card style="width:400px">
      <h2>智卷云 · 超级管理后台</h2>
      <el-input v-model="lg.mobile" placeholder="手机号" style="margin-bottom:10px" />
      <el-input v-model="lg.password" type="password" placeholder="密码" show-password />
      <el-input v-model="lg.totp" placeholder="双因子动态码（已启用 TOTP 时必填，6 位）" style="margin-top:10px" />
      <el-button type="primary" style="width:100%;margin-top:14px" @click="login">登录</el-button>
      <p class="tip">种子账号 13000000000 / Admin@123456（SUPER_ADMIN）</p>
    </el-card>
  </div>

  <el-container v-else style="min-height:100vh">
    <el-aside width="200px" class="side">
      <div class="brand">智卷云 · 后台</div>
      <el-menu :default-active="pane" @select="p => (pane = p)">
        <el-menu-item index="dash">📊 数据看板</el-menu-item>
        <el-menu-item index="ads">📢 广告位管理</el-menu-item>
        <el-menu-item index="notices">📣 公告推送</el-menu-item>
        <el-menu-item index="settings">⚙️ 系统设置</el-menu-item>
        <el-menu-item index="coupons">🎟️ 优惠券模板</el-menu-item>
        <el-menu-item index="auditq">📝 题目审核</el-menu-item>
        <el-menu-item index="feedback">🎫 纠错工单</el-menu-item>
        <el-menu-item index="auditres">📚 资源审核</el-menu-item>
        <el-menu-item index="contracts">🤝 创作者签约</el-menu-item>
        <el-menu-item index="copyright">⚖️ 版权工单</el-menu-item>
        <el-menu-item index="schools">🏫 学校订阅</el-menu-item>
        <el-menu-item index="security">🔐 安全设置</el-menu-item>
        <el-menu-item index="audit">🛡 操作审计</el-menu-item>
      </el-menu>
      <el-button style="margin:14px;width:calc(100% - 28px)" @click="logout">退出</el-button>
    </el-aside>

    <el-main>
      <!-- 看板 -->
      <div v-if="pane === 'dash'">
        <h2>数据看板（跨服务聚合，docs/16 §2）</h2>
        <div class="kpi">
          <el-card v-for="(v, k) in kpi" :key="k" shadow="hover">
            <b style="font-size:26px;color:var(--primary)">{{ v }}</b>
            <div style="font-size:12.5px;color:var(--text-3)">{{ k }}</div>
          </el-card>
        </div>
      </div>
      <!-- 广告位 -->
      <div v-if="pane === 'ads'">
        <h2>广告位管理（空素材 = 前台占位框）</h2>
        <el-card style="margin-bottom:14px">
          <el-select v-model="ad.position" style="width:200px">
            <el-option v-for="p in positions" :key="p" :label="p" :value="p" />
          </el-select>
          <el-input v-model="ad.title" placeholder="标题" style="width:180px;margin-left:8px" />
          <el-input v-model="ad.imageUrl" placeholder="素材图片 URL（可留空）" style="width:280px;margin-left:8px" />
          <el-input v-model="ad.linkUrl" placeholder="跳转链接" style="width:240px;margin-left:8px" />
          <el-select v-model="ad.audience" style="width:110px;margin-left:8px">
            <el-option label="全部" value="ALL" /><el-option label="教师端" value="TEACHER" /><el-option label="学生端" value="STUDENT" />
          </el-select>
          <el-button type="primary" style="margin-left:8px" @click="createAd">创建（SSRF 校验）</el-button>
        </el-card>
        <el-table :data="ads" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="position" label="位置" width="160" />
          <el-table-column prop="title" label="标题" />
          <el-table-column label="素材" width="120">
            <template #default="{ row }">{{ row.imageUrl ? '已配置' : '占位中' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">{{ row.status ? '在投' : '下线' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="toggleAd(row)">{{ row.status ? '下线' : '上线' }}</el-button>
              <el-button size="small" type="danger" @click="delAd(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 公告 -->
      <div v-if="pane === 'notices'">
        <h2>公告推送</h2>
        <el-card style="margin-bottom:14px">
          <el-input v-model="notice.title" placeholder="标题" style="width:280px;margin-right:8px" />
          <el-input v-model="notice.content" placeholder="内容" style="width:420px;margin-right:8px" />
          <el-button type="primary" @click="createNotice">发布</el-button>
        </el-card>
        <el-table :data="notices" size="small">
          <el-table-column prop="id" label="ID" width="60" /><el-table-column prop="title" label="标题" />
          <el-table-column prop="content" label="内容" /><el-table-column prop="created_at" label="时间" width="170" />
        </el-table>
      </div>

      <!-- 设置 -->
      <div v-if="pane === 'settings'">
        <h2>系统设置（Logo/站名/备案号 —— 前台占位自动替换）</h2>
        <el-card style="max-width:640px">
          <el-form label-width="120px">
            <el-form-item label="站名"><el-input v-model="settings.site_name" /></el-form-item>
            <el-form-item label="Logo URL"><el-input v-model="settings.logo_url" placeholder="留空 = 前台显示占位" /></el-form-item>
            <el-form-item label="备案号"><el-input v-model="settings.beian" /></el-form-item>
            <el-form-item label="客服电话"><el-input v-model="settings.service_phone" /></el-form-item>
            <el-button type="primary" @click="saveSettings">保存</el-button>
          </el-form>
        </el-card>
      </div>

      <!-- 券模板 -->
      <div v-if="pane === 'coupons'">
        <h2>优惠券模板（docs/14 C-2：限量/限领/有效期）</h2>
        <el-card style="margin-bottom:14px">
          <el-input v-model="tpl.name" placeholder="券名" style="width:160px;margin-right:8px" />
          <el-select v-model="tpl.type" style="width:150px;margin-right:8px">
            <el-option label="满减" value="FULL_REDUCTION" /><el-option label="折扣" value="DISCOUNT" /><el-option label="点数券" value="POINTS" />
          </el-select>
          <el-input v-model="tpl.discountCents" placeholder="面值(分)" style="width:100px;margin-right:8px" />
          <el-input v-model="tpl.minSpendCents" placeholder="门槛(分)" style="width:100px;margin-right:8px" />
          <el-input v-model="tpl.total" placeholder="总量" style="width:90px;margin-right:8px" />
          <el-input v-model="tpl.perLimit" placeholder="每人限领" style="width:100px;margin-right:8px" />
          <el-input v-model="tpl.validDays" placeholder="有效天数" style="width:100px;margin-right:8px" />
          <el-button type="primary" @click="createTpl">创建</el-button>
        </el-card>
        <el-table :data="templates" size="small">
          <el-table-column prop="id" label="ID" width="60" /><el-table-column prop="name" label="名称" />
          <el-table-column prop="type" label="类型" width="140" /><el-table-column prop="discountCents" label="面值(分)" width="90" />
          <el-table-column label="已领/总量" width="110"><template #default="{ row }">{{ row.granted }}/{{ row.total }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="80"><template #default="{ row }">{{ row.status ? '上架' : '下架' }}</template></el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="toggleTpl(row)">{{ row.status ? '下架' : '上架' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 题目审核 -->
      <div v-if="pane === 'auditq'">
        <h2>题目审核工作流（EDITOR 终审：通过上架 / 驳回下架）</h2>
        <el-radio-group v-model="auditStatus" style="margin-bottom:12px" @change="loadAuditQ">
          <el-radio-button :value="1">审核中</el-radio-button>
          <el-radio-button :value="2">已上架</el-radio-button>
          <el-radio-button :value="3">已驳回</el-radio-button>
        </el-radio-group>
        <el-table :data="auditQ" size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="type" label="题型" width="90" />
          <el-table-column prop="stem" label="题干" show-overflow-tooltip />
          <el-table-column prop="kpNames" label="知识点" width="180" />
          <el-table-column prop="author" label="作者" width="140" />
          <el-table-column prop="reviewer" label="审核" width="160" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <template v-if="row.status === 1">
                <el-button size="small" type="success" @click="doAudit(row.id, true)">通过</el-button>
                <el-button size="small" type="danger" @click="doAudit(row.id, false)">驳回</el-button>
              </template>
              <span v-else class="meta">{{ row.status === 2 ? '在售' : '已下架' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 纠错工单 -->
      <div v-if="pane === 'feedback'">
        <h2>纠错工单（采纳奖励点数：解析类 +5 / 其他 +2）</h2>
        <el-radio-group v-model="fbStatus" style="margin-bottom:12px" @change="loadFeedback">
          <el-radio-button :value="0">待处理</el-radio-button>
          <el-radio-button :value="1">已采纳</el-radio-button>
          <el-radio-button :value="2">已驳回</el-radio-button>
        </el-radio-group>
        <el-table :data="feedbacks" size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="questionId" label="题目" width="90" />
          <el-table-column prop="targetType" label="类型" width="140" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
          <el-table-column prop="rewardPoints" label="奖励" width="80" />
          <el-table-column prop="handler" label="处理人" width="120" />
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <template v-if="row.status === 0">
                <el-input v-model="row._remark" size="small" placeholder="处理备注" style="width:130px;margin-right:6px" />
                <el-button size="small" type="success" @click="resolveFb(row, true)">采纳</el-button>
                <el-button size="small" type="danger" @click="resolveFb(row, false)">驳回</el-button>
              </template>
              <span v-else class="meta">{{ row.status === 1 ? '已采纳' : '已驳回' }} · {{ row.resolveRemark }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 资源审核 -->
      <div v-if="pane === 'auditres'">
        <h2>资源审核（上架 / 下架 / 驳回，docs/26 F-XKW-01）</h2>
        <el-radio-group v-model="resStatus" style="margin-bottom:12px" @change="loadRes">
          <el-radio-button :value="0">待审核</el-radio-button>
          <el-radio-button :value="1">已上架</el-radio-button>
          <el-radio-button :value="2">已下架</el-radio-button>
          <el-radio-button :value="3">已驳回</el-radio-button>
        </el-radio-group>
        <el-table :data="resources" size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="title" label="标题" show-overflow-tooltip />
          <el-table-column prop="category" label="类别" width="90" />
          <el-table-column prop="level" label="等级" width="80" />
          <el-table-column label="价格" width="80">
            <template #default="{ row }">{{ row.level === 'FREE' ? '免费' : '¥' + ((row.priceCents || 0) / 100).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="author" label="作者" width="110" />
          <el-table-column label="浏览/下载" width="100">
            <template #default="{ row }">{{ row.browseCount }}/{{ row.downloadCount }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <template v-if="row.status === 0">
                <el-button size="small" type="success" @click="setRes(row, 1)">上架</el-button>
                <el-button size="small" type="danger" @click="setRes(row, 3)">驳回</el-button>
              </template>
              <el-button v-else-if="row.status === 1" size="small" @click="setRes(row, 2)">下架</el-button>
              <el-button v-else-if="row.status === 2 || row.status === 3" size="small" type="success" @click="setRes(row, 1)">重新上架</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 创作者签约 -->
      <div v-if="pane === 'contracts'">
        <h2>创作者签约（主体 / 分成比例 / 结算周期；docs/26 §6，签约比例优先于全局默认）</h2>
        <el-card style="margin-bottom:14px">
          <el-input-number v-model="contractForm.creatorUserId" :min="1" placeholder="创作者ID" style="width:140px;margin-right:8px" />
          <el-input v-model="contractForm.subject" placeholder="签约主体（实名/笔名/机构）" style="width:220px;margin-right:8px" />
          <el-input-number v-model="contractForm.ratePct" :min="0" :max="100" style="width:120px;margin-right:8px" />
          <span class="tip">%分成比例</span>
          <el-button type="primary" style="margin-left:12px" @click="createContract">签约生效</el-button>
        </el-card>
        <el-table :data="contracts" size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="creatorUserId" label="创作者" width="90" />
          <el-table-column prop="subject" label="签约主体" />
          <el-table-column label="分成比例" width="90"><template #default="{ row }">{{ row.ratePct }}%</template></el-table-column>
          <el-table-column prop="settleCycle" label="结算周期" width="100" />
          <el-table-column label="状态" width="90"><template #default="{ row }">{{ row.status === 'ACTIVE' ? '生效中' : '已结束' }}</template></el-table-column>
          <el-table-column label="合同期" width="300">
            <template #default="{ row }">{{ (row.startAt || '').replace('T', ' ') }} ~ {{ row.endAt ? row.endAt.replace('T', ' ') : '长期有效' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button v-if="row.status === 'ACTIVE'" size="small" type="danger" @click="endContract(row)">解约</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 学校订阅 -->
      <div v-if="pane === 'schools'">
        <h2>学校订阅（B 端合同开通；订阅期内教师全员享会员权益，docs/26 §7）</h2>
        <el-card style="margin-bottom:14px">
          <el-input v-model="schoolForm.name" placeholder="学校名称" style="width:220px;margin-right:8px" />
          <el-input-number v-model="schoolForm.adminUserId" :min="1" placeholder="校管理员ID" style="width:160px;margin-right:8px" />
          <el-input-number v-model="schoolForm.seatLimit" :min="0" placeholder="教师席位(0=不限)" style="width:170px;margin-right:8px" />
          <el-select v-model="schoolForm.months" style="width:110px;margin-right:8px">
            <el-option label="12 个月" :value="12" /><el-option label="24 个月" :value="24" /><el-option label="36 个月" :value="36" />
          </el-select>
          <el-button type="primary" @click="openSchool">合同开通</el-button>
        </el-card>
        <el-table :data="schools" size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="学校" />
          <el-table-column prop="adminUserId" label="管理员" width="90" />
          <el-table-column label="教师/席位" width="110">
            <template #default="{ row }">{{ row.teachers }} / {{ row.seatLimit < 0 ? '不限' : row.seatLimit }}</template>
          </el-table-column>
          <el-table-column prop="memberUntil" label="订阅到期" width="170">
            <template #default="{ row }">{{ (row.memberUntil || '').replace('T', ' ') }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.active ? 'success' : 'danger'">
                {{ row.active ? '生效中' : (row.status === 'OPEN' ? '已到期' : '已关闭') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="renewSchool(row)">续订12月</el-button>
              <el-button v-if="row.status === 'OPEN'" size="small" type="danger" @click="closeSchool(row)">关闭</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 安全设置（管理端 TOTP 双因子，docs/20 等保二级） -->
      <div v-if="pane === 'security'">
        <h2>安全设置 · 管理端双因子（TOTP，docs/20 §6 等保二级）</h2>
        <el-alert v-if="totp.enabled" type="success" :closable="false" title="双因子已启用：登录时需输入 Authenticator 动态码" style="margin-bottom:14px" />
        <template v-if="!totp.enabled">
          <el-card v-if="!totp.secret" style="margin-bottom:14px">
            <el-button type="primary" @click="totpSetup">生成绑定密钥</el-button>
            <span class="tip" style="margin-left:12px">启用后每次登录需输入 6 位动态码</span>
          </el-card>
          <el-card v-else style="margin-bottom:14px">
            <p style="margin:0 0 8px"><b>密钥（在 Authenticator 中手动录入，类型：基于时间）：</b></p>
            <p style="font-family:monospace;font-size:16px;letter-spacing:2px">{{ totp.secret }}</p>
            <p class="tip" style="word-break:break-all">{{ totp.otpauthUri }}</p>
            <el-input v-model="totp.code" placeholder="输入 Authenticator 显示的 6 位动态码" style="width:280px;margin-top:8px" />
            <el-button type="primary" style="margin-left:8px" @click="totpEnable">验证并启用</el-button>
            <el-button @click="totpSetup">重新生成</el-button>
          </el-card>
        </template>
        <el-card v-else>
          <el-button type="danger" @click="totpDisable">解绑双因子</el-button>
          <span class="tip" style="margin-left:12px">解绑后重新 setup 即可再次启用</span>
        </el-card>
      </div>

      <!-- 版权工单 -->
      <div v-if="pane === 'copyright'">
        <h2>版权异议 / 申诉工单（异议 → 下架复核；docs/26 F-XKW-14）</h2>
        <el-descriptions :column="6" border size="small" style="margin-bottom:14px">
          <el-descriptions-item label="工单总数">{{ sla.total ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="待处理">{{ sla.open ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="已处理">{{ sla.closed ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="平均处理时长">{{ sla.avgResolveHours ?? '-' }} 小时</el-descriptions-item>
          <el-descriptions-item label="超时未处理">
            <span :style="(sla.overdueOpen || 0) > 0 ? 'color:#dc2626;font-weight:700' : ''">{{ sla.overdueOpen ?? '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="超时率">{{ sla.overdueRatePct ?? '-' }}%（SLA {{ sla.slaHours ?? 72 }}h）</el-descriptions-item>
        </el-descriptions>
        <el-radio-group v-model="appealStatus" style="margin-bottom:12px" @change="loadAppeals">
          <el-radio-button value="OPEN">待处理</el-radio-button>
          <el-radio-button value="RESOLVED">已解决</el-radio-button>
          <el-radio-button value="REJECTED">已驳回</el-radio-button>
          <el-radio-button value="WITHDRAWN">已撤回</el-radio-button>
        </el-radio-group>
        <el-table :data="appeals" size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="targetType" label="对象" width="90" />
          <el-table-column prop="targetId" label="对象ID" width="80" />
          <el-table-column prop="appealType" label="类型" width="100" />
          <el-table-column prop="content" label="描述" show-overflow-tooltip />
          <el-table-column prop="contact" label="联系方式" width="120" />
          <el-table-column prop="handler" label="处理人" width="100" />
          <el-table-column label="操作" width="230">
            <template #default="{ row }">
              <template v-if="row.status === 'OPEN'">
                <el-input v-model="row._remark" size="small" placeholder="处理备注" style="width:120px;margin-right:6px" />
                <el-button size="small" type="success" @click="handleAppeal(row, 'RESOLVE')">解决</el-button>
                <el-button size="small" type="danger" @click="handleAppeal(row, 'REJECT')">驳回</el-button>
              </template>
              <span v-else class="meta">{{ row.status }} · {{ row.handleRemark }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 审计 -->
      <div v-if="pane === 'audit'">
        <h2>操作审计（只读）</h2>
        <el-table :data="audits" size="small">
          <el-table-column prop="id" label="ID" width="60" /><el-table-column prop="actor" label="操作人" width="120" />
          <el-table-column prop="action" label="动作" width="140" /><el-table-column prop="detail" label="详情" />
          <el-table-column prop="ip" label="IP" width="130" /><el-table-column prop="created_at" label="时间" width="170" />
        </el-table>
      </div>
    </el-main>
  </el-container>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const http = axios.create({ baseURL: '/api/v1', timeout: 15000 })
http.interceptors.request.use(c => { c.headers.Authorization = 'Bearer ' + (localStorage.getItem('examforge_admin_token') || ''); return c })
http.interceptors.response.use(r => {
  if (r.data.code !== 0) { ElMessage.error(r.data.message); throw new Error(r.data.message) }
  return r.data.data
}, e => { ElMessage.error(e.response?.data?.message || '网络异常'); throw e })

const token = ref(localStorage.getItem('examforge_admin_token') || '')
const pane = ref('dash')
const kpi = ref({})
async function loadDash() {
  kpi.value = await api('/admin/dashboard')
}
const lg = reactive({ mobile: '13000000000', <REDACTED_SECRET>, totp: '' })
const positions = ['home_hero', 'home_banner', 'sidebar_teacher', 'sidebar_student', 'list_inline', 'detail_footer', 'login_promo']
const ad = reactive({ position: 'home_banner', title: '', imageUrl: '', linkUrl: '', audience: 'ALL', status: 1 })
const ads = ref([]), notices = ref([]), audits = ref([]), templates = ref([]), auditQ = ref([]), feedbacks = ref([])
const resources = ref([]), appeals = ref([])
const contracts = ref([])
const contractForm = reactive({ creatorUserId: null, subject: '', ratePct: 50 })
const schools = ref([])
const schoolForm = reactive({ name: '', adminUserId: null, seatLimit: 0, months: 12 })
const totp = reactive({ enabled: false, secret: '', otpauthUri: '', code: '' })
const auditStatus = ref(1), fbStatus = ref(0), resStatus = ref(0), appealStatus = ref('OPEN')
const tpl = reactive({ name: '', type: 'FULL_REDUCTION', discountCents: 100, minSpendCents: 0, total: 100, perLimit: 1, validDays: 30 })
const notice = reactive({ title: '', content: '' })
const settings = reactive({ site_name: '', logo_url: '', beian: '', service_phone: '' })

async function login() {
  let d
  try {
    d = await axios.post('/api/v1/auth/admin-login', { ...lg, totp: lg.totp || undefined }).then(r => r.data.data)
  } catch (e) {
    const msg = e?.response?.data?.message || ''
    if (msg.includes('NEED_TOTP')) {
      try {
        const r2 = await ElMessageBox.prompt('该账号已启用管理端双因子，请输入 Authenticator 6 位动态码', '两步验证', {
          confirmButtonText: '验证登录', cancelButtonText: '取消', inputPattern: /^\d{6}$/, inputErrorMessage: '请输入 6 位数字动态码'
        })
        d = await axios.post('/api/v1/auth/admin-login', { ...lg, totp: r2.value }).then(r => r.data.data)
      } catch (_) { return }
    } else return
  }
  token.value = d.accessToken
  localStorage.setItem('examforge_admin_token', token.value)
  lg.totp = ''
  loadAll()
  try { loadTotp() } catch (_) {}
}
function logout() { token.value = ''; localStorage.removeItem('examforge_admin_token') }
async function loadAll() { loadDash(); loadAds(); loadNotices(); loadAudits(); loadSettings(); loadTemplates(); loadAuditQ(); loadFeedback(); loadRes(); loadAppeals(); loadSla(); loadContracts(); loadSchools(); loadTotp() }

// ---- 创作者签约（examforge-resource /api/v1/resources/admin/creator）----
async function loadContracts() { contracts.value = (await http.get('/resources/admin/creator/contracts', { params: { page: 1, size: 30 } })).records }

// ---- 学校订阅（examforge-school /api/v1/schools/admin，T-26h）----
async function loadSchools() { schools.value = await http.get('/schools/admin') }
async function openSchool() {
  if (!schoolForm.name || !schoolForm.adminUserId) { ElMessage.warning('学校名称与校管理员ID必填'); return }
  await http.post('/schools/admin', {
    name: schoolForm.name, adminUserId: schoolForm.adminUserId,
    seatLimit: schoolForm.seatLimit || undefined, months: schoolForm.months
  })
  ElMessage.success('已开通，校管理员可在用户端「我的学校」批量添加成员')
  loadSchools()
}
async function renewSchool(row) { await http.post('/schools/admin/' + row.id + '/renew', { months: 12 }); ElMessage.success('已续订 12 个月'); loadSchools() }
async function closeSchool(row) { await http.post('/schools/admin/' + row.id + '/close'); loadSchools() }

// ---- 安全设置（管理端 TOTP 双因子，docs/20 §6）----
async function loadTotp() {
  try {
    const me = await axios.get('/api/v1/auth/totp/state', { headers: { Authorization: 'Bearer ' + (localStorage.getItem('examforge_admin_token') || '') } })
    totp.enabled = me.data?.data?.enabled || false
  } catch (_) { totp.enabled = false }
}
async function totpSetup() {
  try {
    const d = await http.post('/auth/totp/setup')
    totp.secret = d.secret; totp.otpauthUri = d.otpauthUri; totp.code = ''
  } catch (_) {}
}
async function totpEnable() {
  if (!/^\d{6}$/.test(totp.code)) { ElMessage.warning('请输入 6 位动态码'); return }
  try {
    await http.post('/auth/totp/enable', { code: totp.code })
    ElMessage.success('双因子已启用，下次登录需输入动态码')
    totp.enabled = true; totp.secret = ''
  } catch (_) {}
}
async function totpDisable() {
  try { await http.post('/auth/totp/disable'); totp.enabled = false; totp.secret = ''; ElMessage.success('已解绑') } catch (_) {}
}
async function createContract() {
  if (!contractForm.creatorUserId || !contractForm.subject) { ElMessage.warning('创作者ID与签约主体必填'); return }
  await http.post('/resources/admin/creator/contract', { ...contractForm })
  ElMessage.success('已签约生效，分成比例即时覆盖全局默认')
  contractForm.subject = ''
  loadContracts()
}
async function endContract(row) {
  await http.put('/resources/admin/creator/contracts/' + row.id + '/end')
  ElMessage.success('已解约，分成回落全局默认比例')
  loadContracts()
}

// ---- 资源审核（examforge-resource /api/v1/resources/admin）----
async function loadRes() { resources.value = (await http.get('/resources/admin/items', { params: { status: resStatus.value, page: 1, size: 30 } })).records }
async function setRes(row, status) {
  await http.put('/resources/admin/items/' + row.id + '/status', { status })
  ElMessage.success(status === 1 ? '已上架' : status === 3 ? '已驳回' : '已下架')
  loadRes()
}
// ---- 版权工单 ----
const sla = ref({})
async function loadSla() { try { sla.value = await http.get('/resources/admin/appeals/sla') } catch { sla.value = {} } }
async function loadAppeals() { appeals.value = (await http.get('/resources/admin/appeals', { params: { status: appealStatus.value, page: 1, size: 30 } })).records }
async function handleAppeal(row, action) {
  await http.put('/resources/admin/appeals/' + row.id + '/handle', { action, remark: row._remark || '' })
  ElMessage.success(action === 'RESOLVE' ? '已解决' : '已驳回')
  loadAppeals(); loadSla()
}
async function loadFeedback() { feedbacks.value = (await http.get('/questions/admin/feedback', { params: { status: fbStatus.value, pageSize: 30 } })).list }
async function resolveFb(row, adopt) {
  await http.put('/questions/admin/feedback/' + row.id + '/resolve', { adopt, remark: row._remark || '' })
  ElMessage.success(adopt ? '已采纳并发放奖励点数' : '已驳回')
  loadFeedback()
}
async function loadAuditQ() { auditQ.value = (await http.get('/questions/admin/list', { params: { status: auditStatus.value, pageSize: 30 } })).list }
async function doAudit(id, approve) {
  await http.put('/questions/admin/' + id + '/audit', { approve })
  ElMessage.success(approve ? '已通过并上架' : '已驳回')
  loadAuditQ()
}
async function loadTemplates() { templates.value = await http.get('/coupons/admin/templates') }
async function createTpl() {
  await http.post('/coupons/admin/templates', {
    name: tpl.name, type: tpl.type, discountCents: Number(tpl.discountCents),
    minSpendCents: Number(tpl.minSpendCents), total: Number(tpl.total),
    perLimit: Number(tpl.perLimit), validDays: Number(tpl.validDays), status: 1
  })
  ElMessage.success('已创建'); loadTemplates()
}
async function toggleTpl(row) {
  await http.put('/coupons/admin/templates/' + row.id + '/status', { status: row.status ? 0 : 1 })
  loadTemplates()
}
async function loadAds() { ads.value = (await http.get('/admin/ads')).list }
async function loadNotices() { notices.value = (await http.get('/admin/notices')).list }
async function loadAudits() { audits.value = (await http.get('/admin/audit?limit=50')).list }
async function loadSettings() { Object.assign(settings, await http.get('/settings')) }
async function createAd() { await http.post('/admin/ads', { ...ad }); ElMessage.success('已创建'); loadAds() }
async function toggleAd(row) { await http.put('/admin/ads/' + row.id, { ...row, status: row.status ? 0 : 1 }); loadAds() }
async function delAd(id) { await http.delete('/admin/ads/' + id); loadAds() }
async function createNotice() { await http.post('/admin/notices', { ...notice }); loadNotices() }
async function saveSettings() { await http.put('/admin/settings', { ...settings }); ElMessage.success('已保存，前台占位自动替换') }
onMounted(() => { if (token.value) loadAll() })
</script>

<style>
body { margin: 0; font-family: "PingFang SC", "Microsoft YaHei", sans-serif; background: #f5f7fb; }
.side { background: #0f172a; }
.side .brand { color: #fff; font-weight: 800; padding: 18px 14px; }
.side .el-menu { border: none; background: transparent; }
.side .el-menu-item { color: #cbd5e1; }
.side .el-menu-item.is-active { background: #2563eb; color: #fff; }
.tip { font-size: 12px; color: #9ca3af; }
</style>
