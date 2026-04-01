import api from './axios';

export const fetchAppeals = (params) => api.get('/appeals', { params });
export const createAppeal = (payload) => api.post('/appeals', payload);
export const getAppeal = (id) => api.get(`/appeals/${id}`);
export const processAppeal = (id, payload) => api.post(`/appeals/${id}/process`, payload);

export default {
  fetchAppeals,
  createAppeal,
  getAppeal,
  processAppeal
};
