import { useCallback, useEffect, useState } from 'react';
import { usersApi } from '../api/users';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import Spinner from '../components/Spinner';
import StatusChip from '../components/StatusChip';
import { fmtDate } from '../utils';

export default function AdminUsers() {
  const { user: me } = useAuth();
  const toast = useToast();
  const [users, setUsers] = useState(null);
  const [q, setQ] = useState('');
  const [role, setRole] = useState('');

  const load = useCallback(() => {
    usersApi.list().then(setUsers).catch((err) => { setUsers([]); toast.apiError(err); });
  }, [toast]);
  useEffect(() => { load(); }, [load]);

  const changeRole = async (u, newRole) => {
    try {
      await usersApi.update(u.id, { role: newRole });
      toast.success(`${u.name} is now ${newRole}.`);
      load();
    } catch (err) { toast.apiError(err); }
  };

  const remove = async (u) => {
    if (!window.confirm(`Delete user ${u.email}?`)) return;
    try { await usersApi.remove(u.id); toast.info('User deleted.'); load(); } catch (err) { toast.apiError(err); }
  };

  if (users === null) return <Spinner />;
  const filtered = users.filter((u) => (!role || u.role === role) &&
    (!q || `${u.name} ${u.email}`.toLowerCase().includes(q.toLowerCase())));

  return (
    <div>
      <div className="page-head">
        <div><h1>Users</h1><p className="muted">{users.length} registered accounts.</p></div>
        <div className="toolbar">
          <input className="search" placeholder="Search name or email…" value={q} onChange={(e) => setQ(e.target.value)} />
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="">All roles</option><option>STUDENT</option><option>INSTRUCTOR</option><option>ADMIN</option>
          </select>
        </div>
      </div>
      <div className="card table-wrap">
        <table className="table">
          <thead><tr><th>ID</th><th>Name</th><th>Email</th><th>Role</th><th>Joined</th><th>Actions</th></tr></thead>
          <tbody>
            {filtered.map((u) => (
              <tr key={u.id}>
                <td>{u.id}</td>
                <td><strong>{u.name}</strong></td>
                <td>{u.email}</td>
                <td><StatusChip status={u.role} /></td>
                <td>{fmtDate(u.createdAt)}</td>
                <td>
                  {u.id !== me.id ? (
                    <div className="row gap">
                      <select className="sm" value={u.role} onChange={(e) => changeRole(u, e.target.value)}>
                        <option>STUDENT</option><option>INSTRUCTOR</option><option>ADMIN</option>
                      </select>
                      <button className="btn btn-danger-ghost btn-sm" onClick={() => remove(u)}>Delete</button>
                    </div>
                  ) : <span className="muted small">you</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
