import api from './axios';

export const getAlerts = (params) => api.get('/alerts', { params });
export const getUnreadCount = () => api.get('/alerts/unread-count');
export const markAlertRead = (id) => api.put(`/alerts/${id}/read`);
export const acknowledgeAlert = (id) => api.put(`/alerts/${id}/acknowledge`);

export default {
  getAlerts,
  getUnreadCount,
  markAlertRead,
  acknowledgeAlert
};
