import client, { data } from './client';

export const notificationsApi = {
  byUser: (userId) => data(client.get(`/api/notifications/user/${userId}`)),
  unreadCount: (userId) => data(client.get(`/api/notifications/user/${userId}/unread-count`)),
  markRead: (id) => data(client.put(`/api/notifications/${id}/read`)),
};
