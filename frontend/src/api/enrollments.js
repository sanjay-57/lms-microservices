import client, { data } from './client';

export const enrollmentsApi = {
  enroll: (courseId) => data(client.post('/api/enrollments', { courseId })),
  byStudent: (studentId) => data(client.get(`/api/enrollments/student/${studentId}`)),
  byCourse: (courseId) => data(client.get(`/api/enrollments/course/${courseId}`)),
  get: (id) => data(client.get(`/api/enrollments/${id}`)),
  cancel: (id) => data(client.put(`/api/enrollments/${id}/cancel`)),
};
