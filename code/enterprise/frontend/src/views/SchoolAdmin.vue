<template>
  <div>
    <el-card style="max-width:860px;margin:20px auto">
      <h3>🏫 我的学校（校管理员）</h3>
      <template v-if="school">
        <el-descriptions :column="3" border size="small" style="margin-top:10px">
          <el-descriptions-item label="学校">{{ school.name }}</el-descriptions-item>
          <el-descriptions-item label="订阅状态">
            <el-tag size="small" :type="school.active ? 'success' : 'danger'">
              {{ school.active ? '生效中' : (school.status === 'OPEN' ? '已到期' : '已关闭') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="订阅到期">{{ (school.memberUntil || '').replace('T', ' ') }}</el-descriptions-item>
          <el-descriptions-item label="教师 / 席位">{{ school.teachers }} / {{ school.seatLimit < 0 ? '不限' : school.seatLimit }}</el-descriptions-item>
          <el-descriptions-item label="剩余席位">{{ school.seatLeft < 0 ? '不限' : school.seatLeft }}</el-descriptions-item>
        </el-descriptions>
        <el-alert type="info" :closable="false" style="margin-top:10px"
                  title="订阅期内，学校教师自动享会员权益（导出判价、AI 配额等按会员口径执行）" />
      </template>
      <el-empty v-else description="暂无学校：请联系平台运营为你的账号开通学校（B 端合同开通）" />
    </el-card>

    <el-card v-if="school" style="max-width:860px;margin:0 auto 20px">
      <h3>成员管理</h3>
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
        <el-input v-model="membersText" type="textarea" :rows="2" placeholder="教师用户ID，逗号或换行分隔，如 1001,1002（单次最多 100）"
                  style="flex:1;min-width:280px" />
        <el-radio-group v-model="addRole">
          <el-radio value="TEACHER">教师</el-radio>
          <el-radio value="STUDENT">学生</el-radio>
        </el-radio-group>
        <el-button type="primary" :loading="busy" @click="addMembers">批量加入</el-button>
      </div>
      <el-table :data="members" size="small" style="margin-top:12px">
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.role === 'TEACHER' ? 'success' : 'info'">
              {{ row.role === 'TEACHER' ? '教师' : '学生' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="joinedAt" label="加入时间" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" type="danger" text @click="removeMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/request'

// 学校订阅（T-26h，docs/26 §7）：校管理员 = 超管开通时指定的用户
const school = ref(null), members = ref([]), membersText = ref(''), addRole = ref('TEACHER'), busy = ref(false)

onMounted(load)

async function load() {
  try {
    school.value = await http.get('/schools/mine')
    members.value = await http.get('/schools/mine/members')
  } catch (_) { school.value = null }
}

async function addMembers() {
  const ids = membersText.value.split(/[,,\s]+/).map(s => s.trim()).filter(Boolean).map(Number)
  if (!ids.length) { ElMessage.warning('请输入成员用户ID'); return }
  busy.value = true
  try {
    const d = await http.post('/schools/mine/members', { members: ids.map(id => ({ userId: id, role: addRole.value })) })
    ElMessage.success(`已加入 ${d.added} 人${d.skipped ? `，跳过 ${d.skipped} 人（重复/不存在/席位已满）` : ''}`)
    membersText.value = ''
    load()
  } catch (_) {} finally { busy.value = false }
}

async function removeMember(row) {
  try { await http.delete('/schools/mine/members/' + row.userId); load() } catch (_) {}
}
</script>
