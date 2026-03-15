<template>
  <template v-if="route.meta.layout !== 'blank'">
    <AppHeader />
  </template>
  <router-view />
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'

const route = useRoute()
</script>
