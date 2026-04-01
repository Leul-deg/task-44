import api from './axios';

export const getDashboards = () => api.get('/dashboards');
export const getDashboard = (id) => api.get(`/dashboards/${id}`);
export const createDashboard = (payload) => api.post('/dashboards', payload);
export const updateDashboard = (id, payload) => api.put(`/dashboards/${id}`, payload);
export const deleteDashboard = (id) => api.delete(`/dashboards/${id}`);
export const getDashboardData = (id, params) => api.get(`/dashboards/${id}/data`, { params });
export const exportDashboard = (id, params, payload) => api.post(`/dashboards/${id}/export`, payload, { params });
export const previewDashboard = (payload) => api.post('/dashboards/preview', payload);

export default {
  getDashboards,
  getDashboard,
  createDashboard,
  updateDashboard,
  deleteDashboard,
  getDashboardData,
  exportDashboard,
  previewDashboard
};
