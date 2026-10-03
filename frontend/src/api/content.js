import client, { data } from './client';

export const contentApi = {
  modulesByCourse: (courseId) => data(client.get(`/api/modules/course/${courseId}`)),
  createModule: (body) => data(client.post('/api/modules', body)),
  deleteModule: (id) => data(client.delete(`/api/modules/${id}`)),
  lessonsByModule: (moduleId) => data(client.get(`/api/lessons/module/${moduleId}`)),
  createLesson: (body) => data(client.post('/api/lessons', body)),
  deleteLesson: (id) => data(client.delete(`/api/lessons/${id}`)),
  completeModule: (moduleId) => data(client.post(`/api/modules/${moduleId}/complete`)),
  completedModules: (courseId) => data(client.get(`/api/modules/course/${courseId}/completed`)),
};
