import StatusChip from './StatusChip';
import { fmtPrice } from '../utils';

export default function CourseCard({ course, footer, showStatus = false }) {
  return (
    <article className="card course-card">
      <div className={`course-cover cover-${(course.id % 6) + 1}`}>
        <span className="course-cat">{course.category}</span>
        {showStatus && <StatusChip status={course.status} />}
      </div>
      <div className="course-body">
        <h3 title={course.title}>{course.title}</h3>
        <p className="muted small clamp-3">{course.description || 'No description.'}</p>
        <div className="course-meta">
          <span>⏱ {course.durationHours} h</span>
          <span className="price">{fmtPrice(course.price)}</span>
        </div>
      </div>
      {footer && <div className="course-foot">{footer}</div>}
    </article>
  );
}
