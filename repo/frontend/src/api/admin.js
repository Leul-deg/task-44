import api from './axios';

// Users
export const getUsers = (params) => api.get('/admin/users', { params });
export const getUser = (id) => api.get(`/admin/users/${id}`);
export const createUser = (payload) => api.post('/admin/users', payload);
export const updateUser = (id, payload) => api.put(`/admin/users/${id}`, payload);
export const changeRole = (id, payload) => api.put(`/admin/users/${id}/role`, payload);
export const unlockUser = (id) => api.put(`/admin/users/${id}/unlock`);
export const resetPassword = (id) => api.put(`/admin/users/${id}/reset-password`);

// Categories
export const getAdminCategories = () => api.get('/admin/categories');
export const createCategory = (payload) => api.post('/admin/categories', payload);
export const updateCategory = (id, payload) => api.put(`/admin/categories/${id}`, payload);
export const deleteCategory = (id) => api.delete(`/admin/categories/${id}`);

// Locations
export const getAdminLocations = (params) => api.get('/admin/locations', { params });
export const createLocation = (payload) => api.post('/admin/locations', payload);
export const updateLocation = (id, payload) => api.put(`/admin/locations/${id}`, payload);
export const deleteLocation = (id) => api.delete(`/admin/locations/${id}`);

// Claims
export const getClaims = (params) => api.get('/claims', { params });
export const createClaim = (payload) => api.post('/claims', payload);
export const getClaim = (id) => api.get(`/claims/${id}`);
export const updateClaim = (id, payload) => api.put(`/claims/${id}`, payload);

// Tickets
export const getTickets = (params) => api.get('/tickets', { params });
export const createTicket = (payload) => api.post('/tickets', payload);
export const getTicket = (id) => api.get(`/tickets/${id}`);
export const updateTicket = (id, payload) => api.put(`/tickets/${id}`, payload);

// Stats
export const getAdminStats = () => api.get('/admin/stats/counts');
export const getPostVolume = (params) => api.get('/admin/stats/post-volume', { params });
export const getPostStatusDistribution = () => api.get('/admin/stats/post-status');

export default {
  getUsers,
  getUser,
  createUser,
  updateUser,
  changeRole,
  unlockUser,
  resetPassword,
  getAdminCategories,
  createCategory,
  updateCategory,
  deleteCategory,
  getAdminLocations,
  createLocation,
  updateLocation,
  deleteLocation,
  getClaims,
  createClaim,
  getClaim,
  updateClaim,
  getTickets,
  createTicket,
  getTicket,
  updateTicket,
  getAdminStats,
  getPostVolume,
  getPostStatusDistribution
};
