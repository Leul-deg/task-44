<script setup>
import { computed } from 'vue';
import { useAuthStore } from '../stores/auth';
import {
  Briefcase,
  EditPen,
  DocumentCopy,
  Checked,
  WarningFilled,
  BellFilled,
  Tickets,
  CollectionTag,
  LocationFilled,
  DataLine,
  FolderChecked,
  List,
  UserFilled
} from '@element-plus/icons-vue';

const authStore = useAuthStore();

const roleMenus = {
  EMPLOYER: [
    { label: 'Dashboard', path: '/employer/dashboard', icon: Briefcase },
    { label: 'Postings', path: '/employer/postings', icon: DocumentCopy },
    { label: 'Create Job', path: '/employer/create', icon: EditPen },
    { label: 'Claims & Tickets', path: '/employer/claims', icon: WarningFilled }
  ],
  REVIEWER: [
    { label: 'Dashboard', path: '/reviewer/dashboard', icon: Checked },
    { label: 'Review Queue', path: '/reviewer/queue', icon: List },
    { label: 'Appeals', path: '/reviewer/appeals', icon: Tickets }
  ],
  ADMIN: [
    { label: 'Dashboard', path: '/admin/dashboard', icon: DataLine },
    { label: 'Users', path: '/admin/users', icon: UserFilled },
    { label: 'Categories', path: '/admin/categories', icon: CollectionTag },
    { label: 'Locations', path: '/admin/locations', icon: LocationFilled },
    { label: 'Claims', path: '/admin/claims', icon: WarningFilled },
    { label: 'Tickets', path: '/admin/tickets', icon: Tickets },
    { label: 'Alerts', path: '/admin/alerts', icon: BellFilled },
    { label: 'Audit Log', path: '/admin/audit', icon: FolderChecked },
    { label: 'Quarantine', path: '/admin/quarantine', icon: WarningFilled },
    { label: 'Backups', path: '/admin/backups', icon: DocumentCopy }
  ]
};

const analyticsMenu = [
  { label: 'Analytics', path: '/analytics', icon: DataLine },
  { label: 'Reports', path: '/analytics/reports', icon: DocumentCopy }
];

const menuItems = computed(() => {
  const role = authStore.role || 'EMPLOYER';
  const roleSpecific = roleMenus[role] ?? roleMenus.EMPLOYER;
  const analytics = role === 'ADMIN' || role === 'REVIEWER' ? analyticsMenu : [];
  return [...roleSpecific, ...analytics];
});
</script>

<template>
  <div class="sidebar">
    <div class="sidebar__branding">
      <h1>ShiftWorks</h1>
      <p>JobOps Console</p>
    </div>
    <el-menu
      class="sidebar__menu"
      :router="true"
      background-color="transparent"
      text-color="#dbe7ff"
      active-text-color="#4fc3f7"
    >
      <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<style scoped>
.sidebar {
  padding: 24px 12px;
  min-height: 100vh;
  color: #dbe7ff;
}

.sidebar__branding {
  padding: 0 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  margin-bottom: 16px;
}

.sidebar__branding h1 {
  margin: 0;
  font-size: 20px;
  letter-spacing: 0.08em;
}

.sidebar__branding p {
  margin: 4px 0 0;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.3em;
}

.sidebar__menu {
  border-right: none;
}
</style>
