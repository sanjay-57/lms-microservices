import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { coursesApi } from '../api/courses';
import { enrollmentsApi } from '../api/enrollments';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import CourseCard from '../components/CourseCard';
import EmptyState from '../components/EmptyState';
import Spinner from '../components/Spinner';

const ACTIVE_STATUSES = ['ACTIVE', 'COMPLETED'];

export default function Catalog() {
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [courses, setCourses] = useState(null);
  const [enrollments, setEnrollments] = useState([]);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('');
  const [busyId, setBusyId] = useState(null);
  const isStudent = user.role === 'STUDENT';

  const load = useCallback(async () => {
    try {
      const [c, e] = await Promise.all([
        coursesApi.list(),
        isStudent ? enrollmentsApi.byStudent(user.id).catch(() => []) : Promise.resolve([]),
      ]);
      setCourses(c);
      setEnrollments(e);
    } catch (err) {
      setCourses([]);
      toast.apiError(err);
    }
  }, [isStudent, user.id, toast]);

  useEffect(() => { load(); }, [load]);

  const enrolledMap = useMemo(() => {
    const m = new Map();
    enrollments.filter((e) => ACTIVE_STATUSES.includes(e.status)).forEach((e) => m.set(e.courseId, e));
    return m;
  }, [enrollments]);

  const categories = useMemo(() => [...new Set((courses || []).map((c) => c.category).filter(Boolean))].sort(), [courses]);

  const filtered = (courses || []).filter((c) => {
    const q = query.trim().toLowerCase();
    return (!category || c.category === category) &&
      (!q || c.title.toLowerCase().includes(q) || (c.description || '').toLowerCase().includes(q));
  });

  const enroll = async (course) => {
    setBusyId(course.id);
    try {
      await enrollmentsApi.enroll(course.id);
      toast.success(`Enrolled in “${course.title}”. Progress is being initialised…`);
      navigate(`/learn/${course.id}`, { state: { justEnrolled: true } });
    } catch (err) {
      toast.apiError(err);
      load();
    } finally {
      setBusyId(null);
    }
  };

  const footerFor = (c) => {
    if (isStudent) {
      const e = enrolledMap.get(c.id);
      return e ? (
        <>
          <button className="btn btn-success-soft btn-sm" disabled>{e.status === 'COMPLETED' ? '✓ Completed' : '✓ Enrolled'}</button>
          <Link className="btn btn-primary btn-sm" to={`/learn/${c.id}`}>Open</Link>
        </>
      ) : (
        <button className="btn btn-primary btn-sm" disabled={busyId === c.id || c.status !== 'PUBLISHED'} onClick={() => enroll(c)}>
          {busyId === c.id ? 'Enrolling…' : 'Enroll'}
        </button>
      );
    }
    const canManage = user.role === 'ADMIN' || c.instructorId === user.id;
    return canManage ? <Link className="btn btn-outline btn-sm" to={`/manage/${c.id}`}>Manage</Link> : <span className="muted small">Instructor #{c.instructorId}</span>;
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>{user.role === 'ADMIN' ? 'All courses' : 'Course catalog'}</h1>
          <p className="muted">
            {isStudent ? 'Browse published courses and enroll to start learning.' :
              user.role === 'ADMIN' ? 'Every course in the system, including drafts and archived.' :
                'Published courses plus your own drafts.'}
          </p>
        </div>
        <div className="toolbar">
          <input className="search" placeholder="Search courses…" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">All categories</option>
            {categories.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </div>
      </div>

      {courses === null ? <Spinner /> : filtered.length === 0 ? (
        <EmptyState icon="📚" title="No courses found">Try a different search or category.</EmptyState>
      ) : (
        <div className="grid cards">
          {filtered.map((c) => <CourseCard key={c.id} course={c} showStatus={!isStudent} footer={footerFor(c)} />)}
        </div>
      )}
    </div>
  );
}
