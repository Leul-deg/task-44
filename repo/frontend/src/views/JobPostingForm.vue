<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';
import jobsApi from '@/api/jobs';
import { useDictionaryStore } from '@/stores/dictionaries';

const props = defineProps({
  mode: { type: String, default: 'create' },
  jobId: { type: String, default: null }
});

const router = useRouter();
const dictionaryStore = useDictionaryStore();
const formRef = ref();
const cities = ref([]);
const loading = ref(false);
const newTag = ref('');

const form = reactive({
  title: '',
  description: '',
  categoryId: null,
  state: '',
  locationId: null,
  payType: 'HOURLY',
  settlementType: 'WEEKLY',
  payAmount: 20,
  headcount: 1,
  weeklyHours: 20,
  contactPhone: '',
  tags: [],
  validityStart: dayjs().format('YYYY-MM-DD'),
  validityEnd: dayjs().add(30, 'day').format('YYYY-MM-DD')
});

const isEdit = computed(() => props.mode === 'edit');
const categories = computed(() => dictionaryStore.categories);
const formTitle = computed(() => (isEdit.value ? 'Edit Job Posting' : 'Create Job Posting'));
const payBounds = computed(() => {
  if (form.payType === 'HOURLY') return { min: 12, max: 75, label: 'Hourly pay must be $12–$75' };
  return { min: 12, max: 5000, label: 'Flat pay must be $12–$5,000' };
});

const rules = {
  title: [
    { required: true, message: 'Title is required', trigger: 'change' },
    { min: 5, max: 200, message: 'Title must be between 5 and 200 characters', trigger: 'change' }
  ],
  description: [
    { required: true, message: 'Description is required', trigger: 'change' },
    { min: 20, max: 5000, message: 'Description must be between 20 and 5000 characters', trigger: 'change' }
  ],
  categoryId: [{ required: true, message: 'Category is required', trigger: 'change' }],
  state: [{ required: true, message: 'State is required', trigger: 'change' }],
  locationId: [{ required: true, message: 'City is required', trigger: 'change' }],
  payAmount: [
    { required: true, message: 'Pay amount is required', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (value < payBounds.value.min || value > payBounds.value.max) {
          callback(new Error(payBounds.value.label));
        } else {
          callback();
        }
      },
      trigger: 'change'
    }
  ],
  headcount: [{ required: true, message: 'Headcount required', trigger: 'change' }],
  weeklyHours: [{ required: true, message: 'Weekly hours required', trigger: 'change' }],
  validityStart: [{ required: true, message: 'Start date required', trigger: 'change' }],
  validityEnd: [{ required: true, message: 'End date required', trigger: 'change' }]
};

const disableEndDate = (time) => {
  const start = dayjs(form.validityStart || dayjs().format('YYYY-MM-DD'));
  const max = start.add(90, 'day').endOf('day');
  const min = start.startOf('day');
  return time.getTime() < min.valueOf() || time.getTime() > max.valueOf() || time.getTime() < dayjs().startOf('day').valueOf();
};

const disableStartDate = (time) => time.getTime() < dayjs().startOf('day').valueOf();

const loadCities = async (state) => {
  if (!state) {
    cities.value = [];
    form.locationId = null;
    return;
  }
  cities.value = await dictionaryStore.loadCities(state);
  if (!cities.value.some((city) => city.id === form.locationId)) {
    form.locationId = null;
  }
};

watch(() => form.state, (state) => {
  loadCities(state);
});
watch(() => form.payType, () => {
  if (formRef.value) formRef.value.validateField('payAmount');
});

watch(() => form.validityStart, (value) => {
  if (!value) return;
  const max = dayjs(value).add(90, 'day');
  if (dayjs(form.validityEnd).isAfter(max)) {
    form.validityEnd = max.format('YYYY-MM-DD');
  }
});

const addTag = () => {
  const tag = newTag.value.trim();
  if (!tag) return;
  if (form.tags.length >= 10) {
    ElMessage.warning('Maximum of 10 tags allowed');
    return;
  }
  if (!form.tags.includes(tag)) {
    form.tags.push(tag);
  }
  newTag.value = '';
};

const removeTag = (tag) => {
  form.tags = form.tags.filter((t) => t !== tag);
};

const buildPayload = () => ({
  title: form.title,
  description: form.description,
  categoryId: form.categoryId,
  locationId: form.locationId,
  payType: form.payType,
  settlementType: form.settlementType,
  payAmount: Number(form.payAmount),
  headcount: Number(form.headcount),
  weeklyHours: Number(form.weeklyHours),
  contactPhone: form.contactPhone,
  tags: form.tags,
  validityStart: form.validityStart,
  validityEnd: form.validityEnd
});

const persistDraft = async () => {
  await formRef.value.validate();
  const payload = buildPayload();
  loading.value = true;
  try {
    if (isEdit.value && props.jobId) {
      await jobsApi.updateJob(props.jobId, payload);
      ElMessage.success('Draft saved');
      return props.jobId;
    }
    const { data } = await jobsApi.createJob(payload);
    ElMessage.success('Draft created');
    await router.replace(`/employer/postings/${data.id}/edit`);
    return data.id;
  } finally {
    loading.value = false;
  }
};

const handleSave = async () => {
  await persistDraft();
};

const handlePreview = async () => {
  const id = await persistDraft();
  router.push(`/employer/postings/${id}/preview`);
};

const handleSubmit = async () => {
  const id = await persistDraft();
  try {
    await ElMessageBox.confirm('This will send the posting for reviewer approval. Continue?', 'Submit for Review', {
      confirmButtonText: 'Submit',
      cancelButtonText: 'Cancel'
    });
  } catch {
    return;
  }
  await jobsApi.submitJob(id);
  ElMessage.success('Submitted for review');
  router.push(`/employer/postings/${id}`);
};

const loadJob = async () => {
  if (!isEdit.value || !props.jobId) return;
  loading.value = true;
  try {
    const { data } = await jobsApi.getJob(props.jobId);
    form.title = data.title;
    form.description = data.description;
    form.categoryId = data.categoryId;
    form.state = data.locationState;
    await loadCities(form.state);
    form.locationId = data.locationId;
    form.payType = data.payType;
    form.settlementType = data.settlementType;
    form.payAmount = Number(data.payAmount);
    form.headcount = data.headcount;
    form.weeklyHours = Number(data.weeklyHours);
    form.contactPhone = data.contactPhone ?? '';
    form.tags = [...(data.tags ?? [])];
    form.validityStart = data.validityStart;
    form.validityEnd = data.validityEnd;
  } finally {
    loading.value = false;
  }
};

onMounted(async () => {
  await dictionaryStore.ensureCategories();
  await dictionaryStore.ensureStates();
  if (isEdit.value) {
    await loadJob();
  }
});

watch(() => props.jobId, () => {
  if (isEdit.value) {
    loadJob();
  }
});
</script>

<template>
  <section class="page-card">
    <div class="list-header">
      <div>
        <h1>{{ formTitle }}</h1>
        <p class="muted">Provide thorough details so reviewers can approve faster.</p>
      </div>
    </div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :validate-on-rule-change="false">
      <el-row :gutter="24">
        <el-col :md="12" :xs="24">
          <el-form-item label="Job Title" prop="title">
            <el-input v-model="form.title" placeholder="Shift Lead - Coffee Bar" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="Description" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              rows="8"
              maxlength="5000"
              show-word-limit
              placeholder="Describe responsibilities, requirements, and perks"
            />
          </el-form-item>
          <el-form-item label="Category" prop="categoryId">
            <el-select v-model="form.categoryId" placeholder="Select category">
              <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="State" prop="state">
            <el-select v-model="form.state" placeholder="Select state">
              <el-option v-for="state in dictionaryStore.states" :key="state" :label="state" :value="state" />
            </el-select>
          </el-form-item>
          <el-form-item label="City" prop="locationId">
            <el-select v-model="form.locationId" placeholder="Select city" :disabled="!form.state">
              <el-option v-for="city in cities" :key="city.id" :label="city.city" :value="city.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :md="12" :xs="24">
          <el-form-item label="Pay Type">
            <el-radio-group v-model="form.payType">
              <el-radio-button label="HOURLY">Hourly</el-radio-button>
              <el-radio-button label="FLAT">Flat</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="Pay Amount" prop="payAmount">
            <el-input-number v-model="form.payAmount" :min="payBounds.min" :max="payBounds.max" :step="1" :precision="2" />
          </el-form-item>
          <el-form-item label="Settlement Type">
            <el-radio-group v-model="form.settlementType">
              <el-radio-button label="WEEKLY">Weekly</el-radio-button>
              <el-radio-button label="END_OF_SHIFT">End of Shift</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="Headcount" prop="headcount">
            <el-input-number v-model="form.headcount" :min="1" :max="500" />
          </el-form-item>
          <el-form-item label="Weekly Hours" prop="weeklyHours">
            <el-input-number v-model="form.weeklyHours" :min="1" :max="80" :step="1" :precision="1" />
          </el-form-item>
          <el-form-item label="Contact Phone">
            <el-input v-model="form.contactPhone" placeholder="(555) 123-4567" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Tags">
        <div class="tag-editor">
          <div>
            <el-tag
              v-for="tag in form.tags"
              :key="tag"
              closable
              @close="removeTag(tag)"
              type="info"
            >
              {{ tag }}
            </el-tag>
          </div>
          <el-input
            v-model="newTag"
            placeholder="Add tag"
            class="tag-input"
            @keyup.enter.prevent="addTag"
            @blur="addTag"
          />
        </div>
      </el-form-item>
      <el-row :gutter="16">
        <el-col :md="12" :xs="24">
          <el-form-item label="Validity Start" prop="validityStart">
            <el-date-picker v-model="form.validityStart" type="date" value-format="YYYY-MM-DD" :disabled-date="disableStartDate" />
          </el-form-item>
        </el-col>
        <el-col :md="12" :xs="24">
          <el-form-item label="Validity End" prop="validityEnd">
            <el-date-picker v-model="form.validityEnd" type="date" value-format="YYYY-MM-DD" :disabled-date="disableEndDate" />
          </el-form-item>
        </el-col>
      </el-row>
      <div class="form-actions">
        <el-button @click="router.back()">Cancel</el-button>
        <el-button @click="handleSave" :loading="loading">Save Draft</el-button>
        <el-button @click="handlePreview" :loading="loading">Save & Preview</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading">Submit for Review</el-button>
      </div>
    </el-form>
  </section>
</template>

<style scoped>
.tag-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tag-input {
  max-width: 240px;
}

.form-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.list-header {
  margin-bottom: 16px;
}

::v-deep(.el-tag) {
  margin-right: 8px;
}
</style>
