import client, { data } from './client';

export const usersApi = {
  login: (email, password) => data(client.post('/api/users/login', { email, password })),
  register: (body) => data(client.post('/api/users/register', body)),
  get: (id) => data(client.get(`/api/users/${id}`)),
  list: () => data(client.get('/api/users')),
  update: (id, body) => data(client.put(`/api/users/${id}`, body)),
  remove: (id) => data(client.delete(`/api/users/${id}`)),
};
