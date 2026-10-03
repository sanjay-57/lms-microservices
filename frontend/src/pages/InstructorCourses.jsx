import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { coursesApi } from '../api/courses';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import CourseCard from '../components/CourseCard';
import EmptyState from '../components/EmptyState';
import Spinner from '../components/Spinner';

export default function InstructorCourses() {
  const { user } = useAuth();
  const toast = useToast();
  const [courses, setCourses] = useState(null);

  useEffect(() => {
    const req = user.role === 'ADMIN' ? coursesApi.list() : coursesApi.byInstructor(user.id);
    req.then((list) => setCourses(user.role === 'ADMIN' ? list : list.filter((c) => c.instructorId === user.id)))
      .catch((err) => { setCourses([]); toast.apiError(err); });
  }, [user, toast]);

  if (courses === null) return <Spinner />;
  const count = (s) => courses.filter((c) => c.status === s).length;

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>My courses</h1>
          <p className="muted">Create courses, add modules, lessons and assessments, and track your students.</p>
        </div>
        <Link to="/instructor/courses/new" className="btn btn-primary">+ New course</Link>
      </div>
      <div className="stats">
        <div className="stat card"><span className="stat-value">{courses.length}</span><span className="stat-label">Total</span></div>
        <div className="stat card"><span className="stat-value">{count('PUBLISHED')}</span><span className="stat-label">Published</span></div>
        <div className="stat card"><span className="stat-value">{count('DRAFT')}</span><span className="stat-label">Drafts</span></div>
      </div>
      {courses.length === 0 ? (
        <EmptyState icon="🧑‍🏫" title="You haven't created any courses yet">
          <Link to="/instructor/courses/new">Create your first course</Link>
        </EmptyState>
      ) : (
        <div className="grid cards">
          {courses.map((c) => (
            <CourseCard key={c.id} course={c} showStatus footer={
              <>
                <Link className="btn btn-primary btn-sm" to={`/manage/${c.id}`}>Manage</Link>
                <Link className="btn btn-ghost btn-sm" to={`/instructor/courses/${c.id}/edit`}>Edit</Link>
              </>
            } />
          ))}
        </div>
      )}
    </div>
  );
}
