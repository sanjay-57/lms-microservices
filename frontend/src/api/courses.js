import client, { data } from './client';

export const coursesApi = {
  list: () => data(client.get('/api/courses')),
  get: (id) => data(client.get(`/api/courses/${id}`)),
  byInstructor: (instructorId) => data(client.get(`/api/courses/instructor/${instructorId}`)),
  create: (body) => data(client.post('/api/courses', body)),
  update: (id, body) => data(client.put(`/api/courses/${id}`, body)),
  remove: (id) => data(client.delete(`/api/courses/${id}`)),
};
