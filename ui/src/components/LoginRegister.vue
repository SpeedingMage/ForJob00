<template>
  <div class="auth-container">
    <div class="auth-card">
      <h1 class="auth-title">AI 股票分析面板</h1>
      <p class="auth-subtitle">登录或注册以继续</p>

      <div class="mode-tabs">
        <span :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</span>
        <span :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</span>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="submit">
        <el-form-item prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" size="large" clearable />
        </el-form-item>

        <el-form-item v-if="mode === 'register'" prop="code">
          <div class="code-row">
            <el-input v-model="form.code" placeholder="验证码" size="large" />
            <el-button :disabled="codeCountdown > 0" @click="sendCode" size="large">
              {{ codeCountdown > 0 ? codeCountdown + 's' : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" class="submit-btn" @click="submit">
            {{ mode === 'login' ? '登 录' : '注 册' }}
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon class="error-alert" />
      <el-alert v-if="successMsg" :title="successMsg" type="success" show-icon class="error-alert" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import axios from 'axios'

const emit = defineEmits(['login-success'])

const mode = ref('login')
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const codeCountdown = ref(0)
const formRef = ref()

const form = reactive({
  phone: '',
  password: '',
  code: ''
})

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
}

const switchMode = (m) => {
  mode.value = m
  errorMsg.value = ''
  successMsg.value = ''
  form.code = ''
}

const sendCode = async () => {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    errorMsg.value = '请先输入正确的手机号'
    return
  }
  errorMsg.value = ''
  try {
    const res = await axios.post('/api/auth/send-code', { phone: form.phone })
    if (res.data.success) {
      successMsg.value = '验证码已发送，请查看 IDEA/终端控制台'
      codeCountdown.value = 60
      const timer = setInterval(() => {
        codeCountdown.value--
        if (codeCountdown.value <= 0) clearInterval(timer)
      }, 1000)
    } else {
      errorMsg.value = res.data.message
    }
  } catch (e) {
    errorMsg.value = '请求失败，请重试'
  }
}

const submit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  errorMsg.value = ''
  successMsg.value = ''

  try {
    const url = mode.value === 'login' ? '/api/auth/login' : '/api/auth/register'
    const body = mode.value === 'login'
      ? { phone: form.phone, password: form.password }
      : { phone: form.phone, password: form.password, code: form.code }

    const res = await axios.post(url, body)
    if (res.data.success) {
      if (mode.value === 'register') {
        successMsg.value = '注册成功，请登录'
        switchMode('login')
      } else {
        emit('login-success', form.phone)
      }
    } else {
      errorMsg.value = res.data.message
    }
  } catch (e) {
    errorMsg.value = '请求失败，请检查网络'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}

.auth-card {
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}

.auth-title {
  text-align: center;
  font-size: 24px;
  color: #303133;
  margin: 0 0 4px;
}

.auth-subtitle {
  text-align: center;
  color: #909399;
  font-size: 13px;
  margin: 0 0 24px;
}

.mode-tabs {
  display: flex;
  margin-bottom: 24px;
  border-bottom: 2px solid #ebeef5;
}

.mode-tabs span {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  cursor: pointer;
  color: #909399;
  font-size: 15px;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
}

.mode-tabs span.active {
  color: #409eff;
  border-bottom-color: #409eff;
  font-weight: 600;
}

.code-row {
  display: flex;
  gap: 10px;
}

.code-row .el-input {
  flex: 1;
}

.code-row .el-button {
  white-space: nowrap;
}

.submit-btn {
  width: 100%;
}

.error-alert {
  margin-top: 12px;
}
</style>
