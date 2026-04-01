import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

export const defaultRouteForRole = (role) => {
  switch (role) {
    case 'ADMIN':
      return '/admin/dashboard';
    case 'REVIEWER':
      return '/reviewer/dashboard';
    case 'EMPLOYER':
      return '/employer/dashboard';
    default:
      return '/login';
  }
};

const baseMeta = { requiresAuth: true, roles: ['EMPLOYER', 'REVIEWER', 'ADMIN'] };

const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue'), meta: baseMeta },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { requiresAuth: false } },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/change-password', name: 'change-password', component: () => import('../views/ChangePasswordView.vue'), meta: baseMeta },
  { path: '/employer/dashboard', name: 'employer-dashboard', component: () => import('../views/EmployerDashboardView.vue'), meta: { requiresAuth: true, roles: ['EMPLOYER'] } },
  { path: '/employer/postings', name: 'employer-postings', component: () => import('../views/EmployerPostingsView.vue'), meta: { requiresAuth: true, roles: ['EMPLOYER'] } },
  { path: '/employer/create', name: 'employer-create', component: () => import('../views/JobPostingForm.vue'), meta: { requiresAuth: true, roles: ['EMPLOYER'] }, props: { mode: 'create' } },
  { path: '/employer/postings/:id/edit', name: 'employer-posting-edit', component: () => import('../views/JobPostingForm.vue'), meta: { requiresAuth: true, roles: ['EMPLOYER'] }, props: (route) => ({ mode: 'edit', jobId: route.params.id }) },
  { path: '/employer/postings/:id', name: 'employer-posting-detail', component: () => import('../views/JobPostingDetailView.vue'), meta: { requiresAuth: true, roles: ['EMPLOYER', 'ADMIN', 'REVIEWER'] } },
  { path: '/employer/postings/:id/preview', name: 'employer-posting-preview', component: () => import('../views/JobPostingPreviewView.vue'), meta: { requiresAuth: true, roles: ['EMPLOYER'] } },
  { path: '/employer/claims', name: 'employer-claims', component: () => import('../views/EmployerClaimsView.vue'), meta: { requiresAuth: true, roles: ['EMPLOYER'] } },
  { path: '/reviewer/dashboard', name: 'reviewer-dashboard', component: () => import('../views/ReviewerDashboardView.vue'), meta: { requiresAuth: true, roles: ['REVIEWER'] } },
  { path: '/reviewer/queue', name: 'reviewer-queue', component: () => import('../views/ReviewerQueueView.vue'), meta: { requiresAuth: true, roles: ['REVIEWER'] } },
  { path: '/reviewer/queue/:id', name: 'reviewer-queue-detail', component: () => import('../views/ReviewDetailView.vue'), meta: { requiresAuth: true, roles: ['REVIEWER'] } },
  { path: '/reviewer/appeals', name: 'reviewer-appeals', component: () => import('../views/AppealQueueView.vue'), meta: { requiresAuth: true, roles: ['REVIEWER'] } },
  { path: '/reviewer/appeals/:id', name: 'reviewer-appeal-detail', component: () => import('../views/AppealDetailView.vue'), meta: { requiresAuth: true, roles: ['REVIEWER'] } },
  { path: '/admin/dashboard', name: 'admin-dashboard', component: () => import('../views/AdminDashboardView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/admin/users', name: 'admin-users', component: () => import('../views/AdminUsersView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/admin/categories', name: 'admin-categories', component: () => import('../views/AdminCategoriesView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/admin/locations', name: 'admin-locations', component: () => import('../views/AdminLocationsView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/admin/claims', name: 'admin-claims', component: () => import('../views/AdminClaimsView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/admin/tickets', name: 'admin-tickets', component: () => import('../views/AdminTicketsView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/admin/alerts', name: 'admin-alerts', component: () => import('../views/AlertInbox.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/admin/audit', name: 'admin-audit', component: () => import('../views/AdminAuditView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/admin/backups', name: 'admin-backups', component: () => import('../views/AdminBackupsView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/admin/quarantine', name: 'admin-quarantine', component: () => import('../views/AdminQuarantineView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/analytics', name: 'analytics-center', component: () => import('../views/AnalyticsCenter.vue'), meta: { requiresAuth: true, roles: ['REVIEWER', 'ADMIN'] } },
  { path: '/analytics/builder', name: 'dashboard-builder', component: () => import('../views/DashboardBuilder.vue'), meta: { requiresAuth: true, roles: ['REVIEWER', 'ADMIN'] } },
  { path: '/analytics/builder/:id', name: 'dashboard-builder-edit', component: () => import('../views/DashboardBuilder.vue'), meta: { requiresAuth: true, roles: ['REVIEWER', 'ADMIN'] } },
  { path: '/analytics/reports', name: 'analytics-reports', component: () => import('../views/ReportScheduler.vue'), meta: { requiresAuth: true, roles: ['REVIEWER', 'ADMIN'] } },
  { path: '/analytics/exports', name: 'analytics-exports', component: () => import('../views/ReportExports.vue'), meta: { requiresAuth: true, roles: ['REVIEWER', 'ADMIN'] } },
  { path: '/:pathMatch(.*)*', redirect: '/login', meta: { requiresAuth: false } }
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
});

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore();
  if (!authStore.initialized) {
    await authStore.restoreSession();
  }
  const requiresAuth = to.meta.requiresAuth !== false;
  if (requiresAuth && !authStore.isLoggedIn) {
    return next('/login');
  }
  if (to.path === '/login' && authStore.isLoggedIn) {
    return next(defaultRouteForRole(authStore.role));
  }
  if (to.meta.roles && authStore.role && !to.meta.roles.includes(authStore.role)) {
    return next(defaultRouteForRole(authStore.role));
  }
  next();
});

export default router;
