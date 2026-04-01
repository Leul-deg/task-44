import api from './axios';

export const fetchJobs = (params) => api.get('/jobs', { params });
export const fetchSummary = () => api.get('/jobs/summary');
export const getJob = (id) => api.get(`/jobs/${id}`);
export const createJob = (payload) => api.post('/jobs', payload);
export const updateJob = (id, payload) => api.put(`/jobs/${id}`, payload);
export const submitJob = (id) => api.post(`/jobs/${id}/submit`);
export const publishJob = (id, payload) => api.post(`/jobs/${id}/publish`, payload);
export const unpublishJob = (id) => api.post(`/jobs/${id}/unpublish`);
export const previewJob = (id) => api.get(`/jobs/${id}/preview`);
export const fetchHistory = (id) => api.get(`/jobs/${id}/history`);

export default {
  fetchJobs,
  fetchSummary,
  getJob,
  createJob,
  updateJob,
  submitJob,
  publishJob,
  unpublishJob,
  previewJob,
  fetchHistory
};
