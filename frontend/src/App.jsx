import { Route, Routes } from 'react-router-dom';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import Home from './pages/Home';
import Login from './pages/Login';
import Register from './pages/Register';
import Catalog from './pages/Catalog';
import MyLearning from './pages/MyLearning';
import CoursePage from './pages/CoursePage';
import Certificates from './pages/Certificates';
import Verify from './pages/Verify';
import Notifications from './pages/Notifications';
import InstructorCourses from './pages/InstructorCourses';
import CourseForm from './pages/CourseForm';
import ManageCourse from './pages/ManageCourse';
import AdminDashboard from './pages/AdminDashboard';
import AdminUsers from './pages/AdminUsers';
import NotFound from './pages/NotFound';

const STAFF = ['INSTRUCTOR', 'ADMIN'];

export default function App() {
  return (
    <div className="app">
      <Navbar />
      <main className="container main">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/verify" element={<Verify />} />
          <Route path="/verify/:code" element={<Verify />} />

          <Route path="/courses" element={<ProtectedRoute><Catalog /></ProtectedRoute>} />
          <Route path="/notifications" element={<ProtectedRoute><Notifications /></ProtectedRoute>} />

          <Route path="/my-learning" element={<ProtectedRoute roles={['STUDENT']}><MyLearning /></ProtectedRoute>} />
          <Route path="/learn/:courseId" element={<ProtectedRoute roles={['STUDENT']}><CoursePage /></ProtectedRoute>} />
          <Route path="/certificates" element={<ProtectedRoute roles={['STUDENT']}><Certificates /></ProtectedRoute>} />

          <Route path="/instructor" element={<ProtectedRoute roles={STAFF}><InstructorCourses /></ProtectedRoute>} />
          <Route path="/instructor/courses/new" element={<ProtectedRoute roles={STAFF}><CourseForm /></ProtectedRoute>} />
          <Route path="/instructor/courses/:courseId/edit" element={<ProtectedRoute roles={STAFF}><CourseForm /></ProtectedRoute>} />
          <Route path="/manage/:courseId" element={<ProtectedRoute roles={STAFF}><ManageCourse /></ProtectedRoute>} />

          <Route path="/admin" element={<ProtectedRoute roles={['ADMIN']}><AdminDashboard /></ProtectedRoute>} />
          <Route path="/admin/users" element={<ProtectedRoute roles={['ADMIN']}><AdminUsers /></ProtectedRoute>} />

          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
      <footer className="footer">
        <div className="container">Scalable Microservices mini project · Spring Boot · Eureka · API Gateway · RabbitMQ · CQRS & Event Sourcing</div>
      </footer>
    </div>
  );
}
