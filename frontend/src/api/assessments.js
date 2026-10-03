import client, { data } from './client';

export const assessmentsApi = {
  byCourse: (courseId) => data(client.get(`/api/assessments/course/${courseId}`)),
  get: (id) => data(client.get(`/api/assessments/${id}`)),
  create: (body) => data(client.post('/api/assessments', body)),
  questions: (id) => data(client.get(`/api/assessments/${id}/questions`)),
  addQuestion: (id, body) => data(client.post(`/api/assessments/${id}/questions`, body)),
  submit: (id, answers) => data(client.post(`/api/assessments/${id}/submit`, { answers })),
};
