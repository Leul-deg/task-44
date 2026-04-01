<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import dashboardsApi from '@/api/dashboards';
import dayjs from 'dayjs';
import VChart from 'vue-echarts';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart, BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent, DatasetComponent } from 'echarts/components';

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, DatasetComponent]);

const metricsOptions = [
  { value: 'post_volume', label: 'Post Volume' },
  { value: 'claim_count', label: 'Claim Count' },
  { value: 'review_count', label: 'Review Count' },
  { value: 'approval_rate', label: 'Approval Rate' },
  { value: 'handling_time', label: 'Handling Time' },
  { value: 'takedown_count', label: 'Takedown Count' }
];

const dimensionOptions = [
  { value: 'date_daily', label: 'Date (Daily)', type: 'date' },
  { value: 'date_weekly', label: 'Date (Weekly)', type: 'date' },
  { value: 'date_monthly', label: 'Date (Monthly)', type: 'date' },
  { value: 'status', label: 'Status', type: 'category' },
  { value: 'category', label: 'Category', type: 'category' },
  { value: 'location_state', label: 'Location (State)', type: 'category' },
  { value: 'reviewer', label: 'Reviewer', type: 'category' }
];

const statuses = ['DRAFT', 'PENDING_REVIEW', 'APPROVED', 'PUBLISHED', 'REJECTED', 'UNPUBLISHED', 'TAKEN_DOWN', 'APPEAL_PENDING'];

const router = useRouter();
const route = useRoute();
const configId = ref(route.params.id);
const mode = computed(() => route.query.mode === 'view' ? 'view' : 'edit');
const readonly = computed(() => mode.value === 'view');

const form = reactive({
  name: '',
  metrics: [],
  dimension: 'date_daily',
  filters: {
    dateRange: [],
    statuses: []
  }
});

const activeStep = ref(0);
const previewData = ref([]);
const previewLoading = ref(false);
let previewTimer = null;

const loadConfig = async () => {
  if (!configId.value) return;
  const { data } = await dashboardsApi.getDashboard(configId.value);
  form.name = data.name;
  form.metrics = data.metricsJson || [];
  form.dimension = data.dimensionsJson || 'date_daily';
  form.filters = data.filtersJson || { dateFrom: null, dateTo: null, statuses: [] };
  if (data.filtersJson) {
    const parsed = typeof data.filtersJson === 'string' ? JSON.parse(data.filtersJson) : data.filtersJson;
    form.filters.dateRange = parsed.dateFrom && parsed.dateTo
      ? [new Date(parsed.dateFrom), new Date(parsed.dateTo)]
      : [];
    form.filters.statuses = parsed.statuses || [];
  }
  activeStep.value = 0;
  triggerPreview();
};

const triggerPreview = () => {
  clearTimeout(previewTimer);
  previewTimer = setTimeout(fetchPreview, 450);
};

const fetchPreview = async () => {
  if (!form.metrics.length) {
    previewData.value = [];
    return;
  }
  previewLoading.value = true;
  try {
    const payload = buildPreviewPayload();
    const { data } = await dashboardsApi.previewDashboard(payload);
    previewData.value = data;
  } finally {
    previewLoading.value = false;
  }
};

const buildPreviewPayload = () => {
  const filters = {};
  if (form.filters.dateRange.length === 2) {
    filters.dateFrom = dayjs(form.filters.dateRange[0]).toISOString();
    filters.dateTo = dayjs(form.filters.dateRange[1]).toISOString();
  }
  if (form.filters.statuses.length) {
    filters.statuses = form.filters.statuses;
  }
  return {
    name: form.name,
    metricsJson: form.metrics,
    dimensionsJson: form.dimension,
    filtersJson: filters
  };
};

watch(() => [form.metrics, form.dimension, form.filters.dateRange, form.filters.statuses], () => {
  triggerPreview();
}, { deep: true });

watch(() => route.params.id, (newId) => {
  configId.value = newId;
  loadConfig();
});

onMounted(() => {
  if (configId.value) {
    loadConfig();
  }
});

const nextStep = () => {
  if (activeStep.value === 0 && !form.name.trim()) {
    ElMessage.warning('Name is required');
    return;
  }
  if (activeStep.value === 1 && form.metrics.length === 0) {
    ElMessage.warning('Select at least one metric');
    return;
  }
  if (activeStep.value === 2 && !form.dimension) {
    ElMessage.warning('Select a dimension');
    return;
  }
  if (activeStep.value < 3) {
    activeStep.value += 1;
  }
};

const prevStep = () => {
  if (activeStep.value > 0) activeStep.value -= 1;
};

const saveDashboard = async () => {
  const payload = buildPreviewPayload();
  payload.name = form.name;
  let response;
  if (configId.value) {
    response = await dashboardsApi.updateDashboard(configId.value, payload);
  } else {
    response = await dashboardsApi.createDashboard(payload);
    configId.value = response.data.id;
  }
  ElMessage.success('Dashboard saved');
  fetchPreview();
  router.push('/analytics');
};

const exportCsv = async () => {
  if (!configId.value) {
    ElMessage.warning('Save the dashboard before exporting');
    return;
  }
  const payload = buildPreviewPayload();
  const { data } = await dashboardsApi.exportDashboard(configId.value, { masked: true }, payload);
  const blob = new Blob([data], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', `${form.name || 'dashboard'}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};

const chartType = computed(() => form.dimension.startsWith('date') ? 'line' : 'bar');
const chartOptions = computed(() => {
  if (!previewData.value.length || !form.metrics.length) {
    return {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: [] },
      yAxis: { type: 'value' },
      series: []
    };
  }
  const xData = previewData.value.map((row) => row.dimension);
  const series = form.metrics.map((metric) => ({
    name: metric,
    type: chartType.value,
    data: previewData.value.map((row) => Number(row[metric] ?? 0)),
    stack: chartType.value === 'bar' ? 'metric' : undefined
  }));
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, data: form.metrics },
    xAxis: { type: 'category', data: xData },
    yAxis: { type: 'value' },
    series
  };
});

const backToCenter = () => router.push('/analytics');
</script>

<template>
  <section class="page-card">
    <div class="toolbar">
      <div>
        <h1>Dashboard Builder</h1>
        <p class="muted">Compose tailored analytics based on metrics and dimensions.</p>
      </div>
      <el-button @click="backToCenter">Back</el-button>
    </div>
    <el-steps :active="activeStep" finish-status="success">
      <el-step title="Name" />
      <el-step title="Metrics" />
      <el-step title="Dimension" />
      <el-step title="Filters" />
    </el-steps>
    <div class="step-content">
      <div v-if="activeStep === 0">
        <el-form label-position="top">
          <el-form-item label="Dashboard Name">
            <el-input v-model="form.name" :disabled="readonly" />
          </el-form-item>
        </el-form>
      </div>
      <div v-else-if="activeStep === 1">
        <el-checkbox-group v-model="form.metrics" :disabled="readonly">
          <el-checkbox v-for="metric in metricsOptions" :key="metric.value" :label="metric.value">
            {{ metric.label }}
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <div v-else-if="activeStep === 2">
        <el-radio-group v-model="form.dimension" :disabled="readonly">
          <el-radio v-for="dim in dimensionOptions" :key="dim.value" :label="dim.value">
            {{ dim.label }}
          </el-radio>
        </el-radio-group>
      </div>
      <div v-else>
        <el-form label-position="top">
          <el-form-item label="Date Range">
            <el-date-picker v-model="form.filters.dateRange" type="daterange" range-separator="→" start-placeholder="Start" end-placeholder="End" :disabled="readonly" />
          </el-form-item>
          <el-form-item label="Statuses">
            <el-select v-model="form.filters.statuses" multiple placeholder="Select statuses" :disabled="readonly">
              <el-option v-for="status in statuses" :key="status" :label="status" :value="status" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
    </div>
    <div class="step-actions">
      <el-button @click="prevStep" :disabled="activeStep === 0">Previous</el-button>
      <el-button v-if="activeStep < 3" type="primary" @click="nextStep">Next</el-button>
      <el-button v-else type="primary" @click="saveDashboard" :disabled="readonly">Save</el-button>
      <el-button type="success" @click="exportCsv" :disabled="!configId">Export CSV</el-button>
    </div>
    <div class="chart-wrapper" v-loading="previewLoading" v-if="previewData.length">
      <VChart :option="chartOptions" autoresize style="height: 360px" />
    </div>
    <el-empty v-else description="Preview will appear here once you select metrics." />
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.step-content {
  margin: 24px 0;
}

.step-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.chart-wrapper {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 10px 30px rgba(6, 34, 79, 0.1);
}

.muted {
  color: #6b7280;
}
</style>
