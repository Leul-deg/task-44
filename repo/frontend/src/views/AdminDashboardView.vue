<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import dayjs from 'dayjs';
import VChart from 'vue-echarts';
import analyticsApi from '@/api/analytics';
import { getAdminStats } from '@/api/admin';

const dateRange = ref([dayjs().subtract(29, 'day').toDate(), dayjs().toDate()]);
const loading = ref(false);

const postVolume = ref([]);
const postStatus = ref([]);
const approvalRate = ref({ rate: 0, approved: 0, total: 0 });
const avgHandling = ref({ averageHours: 0 });
const reviewerActivity = ref([]);
const claimSuccess = ref({ rate: 0, resolved: 0, total: 0 });
const stats = ref({ totalUsers: 0, activePostings: 0, pendingReviews: 0, openClaims: 0, openTickets: 0, unreadAlerts: 0 });

const fetchAnalytics = async () => {
  loading.value = true;
  try {
    const params = buildRangeParams();
    const [
      volumeRes,
      statusRes,
      approvalRes,
      handlingRes,
      reviewerRes,
      claimRes
    ] = await Promise.all([
      analyticsApi.getPostVolume(params),
      analyticsApi.getPostStatusDistribution(params),
      analyticsApi.getApprovalRate(params),
      analyticsApi.getAverageHandlingTime(params),
      analyticsApi.getReviewerActivity(params),
      analyticsApi.getClaimSuccessRate(params)
    ]);
    postVolume.value = volumeRes.data;
    postStatus.value = statusRes.data;
    approvalRate.value = approvalRes.data;
    avgHandling.value = handlingRes.data;
    reviewerActivity.value = reviewerRes.data;
    claimSuccess.value = claimRes.data;
  } finally {
    loading.value = false;
  }
};

const buildRangeParams = () => {
  const [start, end] = dateRange.value;
  return {
    from: dayjs(start).format('YYYY-MM-DD'),
    to: dayjs(end).format('YYYY-MM-DD')
  };
};

watch(dateRange, fetchAnalytics, { deep: true });
onMounted(() => {
  fetchAnalytics();
  fetchStats();
});

const fetchStats = async () => {
  const res = await getAdminStats();
  stats.value = res.data;
};

const totalPostings = computed(() => postStatus.value.reduce((sum, item) => sum + item.count, 0));
const publishedPostings = computed(() => postStatus.value.find((item) => item.status === 'PUBLISHED')?.count || 0);
const approvalRatePercent = computed(() => Math.round((approvalRate.value.rate || 0) * 1000) / 10);
const avgHandlingHours = computed(() => Math.round((avgHandling.value.averageHours || 0) * 10) / 10);

const metricCards = computed(() => ([
  { label: 'Total Users', value: stats.value.totalUsers },
  { label: 'Active Postings', value: stats.value.activePostings },
  { label: 'Pending Reviews', value: stats.value.pendingReviews },
  { label: 'Open Claims', value: stats.value.openClaims },
  { label: 'Approval Rate', value: approvalRatePercent.value + '%' },
  { label: 'Avg Handling Time', value: avgHandlingHours.value + 'h' }
]));

const statusColors = {
  DRAFT: '#909399',
  PENDING_REVIEW: '#E6A23C',
  APPROVED: '#67C23A',
  PUBLISHED: '#409EFF',
  REJECTED: '#F56C6C',
  UNPUBLISHED: '#909399',
  TAKEN_DOWN: '#F56C6C',
  APPEAL_PENDING: '#E6A23C'
};

const volumeOptions = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: postVolume.value.map((point) => point.date)
  },
  yAxis: { type: 'value' },
  series: [
    {
      type: 'line',
      smooth: true,
      areaStyle: {},
      data: postVolume.value.map((point) => point.count)
    }
  ]
}));

const statusOptions = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [
    {
      type: 'pie',
      radius: ['40%', '70%'],
      data: postStatus.value.map((item) => ({
        name: item.status,
        value: item.count,
        itemStyle: { color: statusColors[item.status] || '#409EFF' }
      }))
    }
  ]
}));

const reviewerDates = computed(() => {
  const set = new Set(reviewerActivity.value.map((point) => point.date));
  return Array.from(set).sort();
});

const reviewerSeries = computed(() => {
  const grouped = reviewerActivity.value.reduce((acc, point) => {
    if (!acc.has(point.reviewerId)) {
      acc.set(point.reviewerId, { name: point.username, data: new Map() });
    }
    acc.get(point.reviewerId).data.set(point.date, point.actions);
    return acc;
  }, new Map());

  const legend = [];
  const series = [];
  grouped.forEach((value, key) => {
    legend.push(value.name);
    series.push({
      name: value.name,
      type: 'bar',
      stack: 'reviewers',
      emphasis: { focus: 'series' },
      data: reviewerDates.value.map((date) => value.data.get(date) || 0)
    });
  });
  return { legend, series };
});

const reviewerOptions = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { bottom: 0, data: reviewerSeries.value.legend },
  xAxis: { type: 'category', data: reviewerDates.value },
  yAxis: { type: 'value' },
  series: reviewerSeries.value.series
}));

const claimTrend = computed(() => [{ date: 'Current', rate: claimSuccess.value.rate || 0 }]);

const claimOptions = computed(() => ({
  tooltip: { trigger: 'axis', formatter: (params) => `${params[0].axisValue}: ${(params[0].data.rate * 100).toFixed(1)}%` },
  xAxis: { type: 'category', data: claimTrend.value.map((point) => point.date) },
  yAxis: {
    type: 'value',
    axisLabel: {
      formatter: (val) => `${Math.round(val * 100)}%`
    }
  },
  series: [
    {
      type: 'line',
      smooth: true,
      data: claimTrend.value.map((point) => ({ value: point.rate, rate: point.rate })),
      areaStyle: {}
    }
  ]
}));
</script>

<template>
  <section class="page-card" v-loading="loading">
    <div class="header">
      <div>
        <h1>Admin Control Center</h1>
        <p class="muted">Monitor key signals across postings, reviews, and support.</p>
      </div>
    </div>
    <el-date-picker v-model="dateRange" type="daterange" range-separator="→" start-placeholder="Start" end-placeholder="End" />
    <el-row :gutter="16" class="metrics-grid cards-grid">
      <el-col v-for="card in metricCards" :key="card.label" :xs="24" :md="8">
        <div class="metric-card">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="24">
      <el-col :md="12" :xs="24">
        <div class="chart-card">
          <div class="chart-header">
            <h3>Post Volume (30 days)</h3>
          </div>
          <VChart :option="volumeOptions" autoresize style="height: 320px" />
        </div>
      </el-col>
      <el-col :md="12" :xs="24">
        <div class="chart-card">
          <div class="chart-header">
            <h3>Postings by Status</h3>
          </div>
          <VChart :option="statusOptions" autoresize style="height: 320px" />
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="24">
      <el-col :md="12" :xs="24">
        <div class="chart-card">
          <div class="chart-header">
            <h3>Reviewer Activity</h3>
          </div>
          <VChart :option="reviewerOptions" autoresize style="height: 320px" />
        </div>
      </el-col>
      <el-col :md="12" :xs="24">
        <div class="chart-card">
          <div class="chart-header">
            <h3>Claim Success Rate (Current)</h3>
            <p class="muted">Resolved: {{ claimSuccess.resolved }} / {{ claimSuccess.total }}</p>
          </div>
          <VChart :option="claimOptions" autoresize style="height: 320px" />
        </div>
      </el-col>
    </el-row>
  </section>
</template>

<style scoped>
.header {
  margin-bottom: 12px;
}

.metrics-grid {
  margin-bottom: 24px;
}

.metric-card {
  background: #0a1d37;
  border-radius: 16px;
  color: #f5f7ff;
  padding: 20px;
  margin-bottom: 16px;
}

.metric-card span {
  display: block;
  font-size: 13px;
  opacity: 0.8;
}

.metric-card strong {
  font-size: 32px;
}

.chart-card {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 10px 30px rgba(6, 34, 79, 0.08);
}

.chart-header {
  margin-bottom: 8px;
}

.muted {
  color: #6b7280;
}
</style>
