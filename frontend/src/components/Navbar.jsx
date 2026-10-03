import { useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import useUnreadCount from './useUnreadCount';
import StatusChip from './StatusChip';

const LINKS = {
  STUDENT: [
    { to: '/courses', label: 'Catalog' },
    { to: '/my-learning', label: 'My Learning' },
    { to: '/certificates', label: 'Certificates' },
  ],
  INSTRUCTOR: [
    { to: '/instructor', label: 'My Courses' },
    { to: '/instructor/courses/new', label: 'New Course' },
    { to: '/courses', label: 'Catalog' },
  ],
  ADMIN: [
    { to: '/admin', label: 'Dashboard' },
    { to: '/admin/users', label: 'Users' },
    { to: '/courses', label: 'All Courses' },
  ],
};

export default function Navbar() {
  const { user, isAuthenticated, logout } = useAuth();
  const unread = useUnreadCount(isAuthenticated ? user?.id : null);
  const [open, setOpen] = useState(false);
  const links = isAuthenticated ? LINKS[user.role] || [] : [];
  const close = () => setOpen(false);

  return (
    <header className="navbar">
      <div className="navbar-inner container">
        <Link to="/" className="brand" onClick={close}>
          <span className="brand-mark">L</span>
          <span>LearnHub <small>LMS</small></span>
        </Link>
        <button className="nav-toggle" aria-label="Toggle menu" onClick={() => setOpen((o) => !o)}>☰</button>
        <nav className={`nav-links ${open ? 'open' : ''}`}>
          {links.map((l) => (
            <NavLink key={l.to} to={l.to} end onClick={close}>{l.label}</NavLink>
          ))}
          <NavLink to="/verify" onClick={close}>Verify</NavLink>
          {isAuthenticated ? (
            <>
              <NavLink to="/notifications" className="nav-bell" onClick={close} title="Notifications">
                <span aria-hidden="true">🔔</span>
                <span className="sr-only">Notifications</span>
                {unread > 0 && <span className="badge">{unread > 99 ? '99+' : unread}</span>}
              </NavLink>
              <div className="nav-user">
                <div className="avatar">{(user.name || '?').charAt(0).toUpperCase()}</div>
                <div className="nav-user-text">
                  <span className="nav-user-name">{user.name}</span>
                  <StatusChip status={user.role} />
                </div>
              </div>
              <button className="btn btn-ghost btn-sm" onClick={() => { close(); logout(); }}>Log out</button>
            </>
          ) : (
            <>
              <NavLink to="/login" onClick={close}>Log in</NavLink>
              <Link to="/register" className="btn btn-primary btn-sm" onClick={close}>Sign up</Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
