import api from './axios';

export const getPostVolume = (params) => api.get('/analytics/post-volume', { params });
export const getPostStatusDistribution = (params) => api.get('/analytics/post-status-distribution', { params });
export const getClaimSuccessRate = (params) => api.get('/analytics/claim-success-rate', { params });
export const getAverageHandlingTime = (params) => api.get('/analytics/avg-handling-time', { params });
export const getReviewerActivity = (params) => api.get('/analytics/reviewer-activity', { params });
export const getApprovalRate = (params) => api.get('/analytics/approval-rate', { params });
export const getTakedownTrend = (params) => api.get('/analytics/takedown-trend', { params });

export default {
  getPostVolume,
  getPostStatusDistribution,
  getClaimSuccessRate,
  getAverageHandlingTime,
  getReviewerActivity,
  getApprovalRate,
  getTakedownTrend
};
