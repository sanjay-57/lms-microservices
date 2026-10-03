import { Link } from 'react-router-dom';
import EmptyState from '../components/EmptyState';

export default function NotFound() {
  return (
    <EmptyState icon="🧭" title="Page not found">
      <Link to="/">Back to home</Link>
    </EmptyState>
  );
}
