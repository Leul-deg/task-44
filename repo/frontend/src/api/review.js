import api from './axios';

export const fetchDashboard = () => api.get('/review/dashboard');
export const fetchQueue = (params) => api.get('/review/queue', { params });
export const getReviewJob = (id) => api.get(`/review/jobs/${id}`);
export const getReviewDiff = (id) => api.get(`/review/jobs/${id}/diff`);
export const getReviewActions = (id) => api.get(`/review/jobs/${id}/actions`);
export const approveJob = (id, payload) => api.post(`/review/jobs/${id}/approve`, payload);
export const rejectJob = (id, payload) => api.post(`/review/jobs/${id}/reject`, payload);
export const takedownJob = (id, payload) => api.post(`/review/jobs/${id}/takedown`, payload);

export default {
  fetchDashboard,
  fetchQueue,
  getReviewJob,
  getReviewDiff,
  getReviewActions,
  approveJob,
  rejectJob,
  takedownJob
};
