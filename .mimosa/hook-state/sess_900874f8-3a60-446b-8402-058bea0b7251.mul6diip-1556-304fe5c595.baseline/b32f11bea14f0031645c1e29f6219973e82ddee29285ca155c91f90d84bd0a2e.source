import { createRouter, createWebHistory } from 'vue-router'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('./views/Home.vue') },
    { path: '/login', component: () => import('./views/Login.vue') },
    { path: '/questions', component: () => import('./views/QuestionList.vue') },
    { path: '/questions/:id', component: () => import('./views/QuestionDetail.vue') },
    { path: '/papers', component: () => import('./views/PaperWorkbench.vue') },
    { path: '/vip', component: () => import('./views/Vip.vue') },
    { path: '/practice', component: () => import('./views/Practice.vue') },
    { path: '/aisearch', component: () => import('./views/AiSearch.vue') },
    { path: '/resources', component: () => import('./views/Resources.vue') },
    { path: '/me', component: () => import('./views/MySpace.vue') },
    { path: '/figures', component: () => import('./views/FigureEditor.vue') },
    { path: '/paper-library', component: () => import('./views/PaperLibrary.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})
