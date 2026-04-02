<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import reportApi from '@/api/reports';
import dashboardsApi from '@/api/dashboards';
import logger from '@/utils/logger';

const schedules = ref([]);
const dashboards = ref([]);
const loading = ref(false);
const presets = [
  { label: 'Daily at 2 AM', value: '0 0 2 * * *' },
  { label: 'Weekly Monday 8 AM', value: '0 0 8 * * MON' },
  { label: 'Monthly 1st midnight', value: '0 0 0 1 * *' },
  { label: 'Custom', value: 'custom' }
];
const selectedPreset = ref(presets[0].value);
const dialog = reactive({
  visible: false,
  scheduleId: null,
  dashboardConfigId: null,
  cronExpression: presets[0].value,
  isActive: true
});
const dialogTitle = computed(() => (dialog.scheduleId ? 'Edit Scheduled Report' : 'Schedule Report'));

const loadSchedules = async () => {
  loading.value = true;
  try {
    const [schedRes, dashboardsRes] = await Promise.all([reportApi.getScheduledReports(), dashboardsApi.getDashboards()]);
    schedules.value = schedRes.data;
    dashboards.value = dashboardsRes.data;
  } finally {
    loading.value = false;
  }
};

const resetDialog = () => {
  dialog.scheduleId = null;
  dialog.dashboardConfigId = null;
  dialog.cronExpression = presets[0].value;
  dialog.isActive = true;
  selectedPreset.value = presets[0].value;
};

const setPresetFromCron = (cron) => {
  const preset = presets.find((item) => item.value === cron);
  const target = preset ? preset.value : 'custom';
  if (selectedPreset.value !== target) {
    selectedPreset.value = target;
  }
};

const openDialog = (schedule = null) => {
  if (schedule) {
    dialog.scheduleId = schedule.id;
    dialog.dashboardConfigId = schedule.dashboardConfigId;
    dialog.cronExpression = schedule.cronExpression;
    dialog.isActive = schedule.isActive;
    setPresetFromCron(schedule.cronExpression);
  } else {
    resetDialog();
  }
  dialog.visible = true;
};

const closeDialog = () => {
  dialog.visible = false;
  resetDialog();
};

const save = async () => {
  if (!dialog.scheduleId && !dialog.dashboardConfigId) {
    ElMessage.warning('Select a dashboard');
    return;
  }
  try {
    if (dialog.scheduleId) {
      await reportApi.updateScheduledReport(dialog.scheduleId, {
        cronExpression: dialog.cronExpression,
        isActive: dialog.isActive
      });
      ElMessage.success('Schedule updated');
    } else {
      await reportApi.createScheduledReport({
        dashboardConfigId: dialog.dashboardConfigId,
        cronExpression: dialog.cronExpression
      });
      ElMessage.success('Scheduled report created');
    }
    closeDialog();
    loadSchedules();
  } catch (error) {
    logger.error('ReportScheduler', 'Failed to save schedule', error?.message);
  }
};

const toggleActive = async (schedule) => {
  await reportApi.updateScheduledReport(schedule.id, { isActive: schedule.isActive });
  ElMessage.success('Schedule updated');
  loadSchedules();
};

const remove = async (schedule) => {
  await ElMessageBox.confirm('Delete this schedule?', 'Delete');
  await reportApi.deleteScheduledReport(schedule.id);
  ElMessage.success('Schedule removed');
  loadSchedules();
};

watch(selectedPreset, (value) => {
  if (value !== 'custom') {
    dialog.cronExpression = value;
  }
});

watch(
  () => dialog.cronExpression,
  (value) => {
    const preset = presets.find((item) => item.value === value);
    if (!preset && selectedPreset.value !== 'custom') {
      selectedPreset.value = 'custom';
    } else if (preset && selectedPreset.value !== preset.value) {
      selectedPreset.value = preset.value;
    }
  }
);

onMounted(loadSchedules);
const humanize = (cron) => {
  if (cron === '0 0 2 * * *') return 'Daily at 2:00 AM';
  if (cron === '0 0 8 * * MON') return 'Weekly Monday 8:00 AM';
  if (cron === '0 0 0 1 * *') return 'Monthly 1st midnight';
  return cron;
};

const formatTimestamp = (value) => (value ? new Date(value).toLocaleString() : '-');
</script>

<template>
  <section class="page-card" v-loading="loading">
    <div class="toolbar">
      <div>
        <h1>Report Scheduler</h1>
        <p class="muted">Automate CSV exports from saved dashboards.</p>
      </div>
      <el-button type="primary" @click="openDialog">Schedule New Report</el-button>
    </div>
    <el-table :data="schedules">
      <el-table-column label="Dashboard" prop="dashboardName" />
      <el-table-column label="Schedule">
        <template #default="{ row }">{{ humanize(row.cronExpression) }}</template>
      </el-table-column>
      <el-table-column label="Last Run">
        <template #default="{ row }">{{ formatTimestamp(row.lastRunAt) }}</template>
      </el-table-column>
      <el-table-column label="Next Run">
        <template #default="{ row }">{{ formatTimestamp(row.nextRunAt) }}</template>
      </el-table-column>
      <el-table-column label="Active" width="120">
        <template #default="{ row }">
          <el-switch v-model="row.isActive" @change="() => toggleActive(row)" />
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="200">
        <template #default="{ row }">
          <el-button type="primary" text @click="openDialog(row)">Edit</el-button>
          <el-button type="danger" link @click="remove(row)">Delete</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialogTitle" width="440px">
      <el-form label-position="top">
        <el-form-item label="Dashboard">
          <el-select v-model="dialog.dashboardConfigId" placeholder="Select dashboard" :disabled="!!dialog.scheduleId">
            <el-option v-for="dash in dashboards" :key="dash.id" :label="dash.name" :value="dash.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Preset Schedule">
          <el-radio-group v-model="selectedPreset">
            <el-radio v-for="preset in presets" :key="preset.value" :label="preset.value">{{ preset.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Cron Expression">
          <el-input v-model="dialog.cronExpression" />
        </el-form-item>
        <el-form-item label="Status">
          <el-switch v-model="dialog.isActive" active-text="Active" inactive-text="Paused" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDialog">Cancel</el-button>
        <el-button type="primary" @click="save">Save</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.muted {
  color: #6b7280;
}
</style>
