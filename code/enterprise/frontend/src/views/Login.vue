<template>
  <div class="login">
    <el-card class="card">
      <h2>登录智卷云</h2>
      <el-form label-width="70px">
        <el-form-item label="手机号"><el-input v-model="mobile" maxlength="11" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="password" type="password" show-password /></el-form-item>
        <el-button type="primary" style="width:100%" @click="doLogin">登录</el-button>
        <el-button style="width:100%;margin:10px 0 0" @click="doRegister">注册（教师）</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/request'

const router = useRouter()
const mobile = ref(''), password = ref('')

async function doLogin() {
  const d = await http.post('/auth/login', { mobile: mobile.value, password: password.value })
  localStorage.setItem('examforge_token', d.accessToken)
  router.push('/')
}
async function doRegister() {
  const d = await http.post('/auth/register', { mobile: mobile.value, password: password.value, role: 'TEACHER' })
  localStorage.setItem('examforge_token', d.accessToken)
  router.push('/')
}
</script>

<style scoped>
.login { display: flex; justify-content: center; padding-top: 8vh; }
.card { width: 400px; }
</style>
