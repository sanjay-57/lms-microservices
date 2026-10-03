import client, { data } from './client';

export const progressApi = {
  byStudent: (studentId) => data(client.get(`/api/progress/student/${studentId}`)),
  one: (studentId, courseId) => data(client.get(`/api/progress/student/${studentId}/course/${courseId}`)),
  byCourse: (courseId) => data(client.get(`/api/progress/course/${courseId}`)),
  events: (aggregateId) => data(client.get(`/api/progress/events/${aggregateId}`)),
  rebuild: () => data(client.post('/api/progress/rebuild')),
};
