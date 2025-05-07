<template>
  <div class="ad-slot" :data-size="size" v-if="!ads.length">
    广告位 {{ position }}<br><small>{{ size }} · 后台可配</small>
  </div>
  <a v-else v-for="ad in ads" :key="ad.id" :href="ad.linkUrl" target="_blank" rel="noopener">
    <img :src="ad.imageUrl" :alt="ad.title" class="ad-img">
  </a>
</template>

<script setup>
// WP-5 占位体系组件：后台无素材 → 虚线占位框；有素材 → 自动替换（docs/12）
import { onMounted, ref } from 'vue'
import http from '../api/request'

const props = defineProps({ position: String, size: { type: String, default: '建议 1200×88' } })
const ads = ref([])

onMounted(async () => {
  try { ads.value = (await http.get('/ads', { params: { position: props.position } })).list } catch (_) {}
})
</script>

<style scoped>
.ad-slot {
  border: 1.5px dashed #d6a76c; border-radius: 12px; background: #fffaf2; color: #b07a2a;
  font-size: 12.5px; text-align: center; padding: 14px; margin: 12px auto; max-width: 1200px;
}
.ad-img { width: 100%; border-radius: 12px; display: block; }
</style>
