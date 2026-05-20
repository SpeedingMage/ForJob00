<template>
  <LoginRegister v-if="!isLoggedIn" @login-success="onLoginSuccess" />
  <StockAnalysisPanel v-else :phone="phone" @logout="onLogout" />
</template>

<script setup>
import { ref } from 'vue'
import LoginRegister from '@/components/LoginRegister.vue'
import StockAnalysisPanel from '@/components/StockAnalysisPanel.vue'

const isLoggedIn = ref(false)
const phone = ref('')

const onLoginSuccess = (phoneNumber) => {
  phone.value = phoneNumber
  isLoggedIn.value = true
}

const onLogout = () => {
  isLoggedIn.value = false
  phone.value = ''
}
</script>

<style>
html, body {
  margin: 0;
  padding: 0;
}
</style>
