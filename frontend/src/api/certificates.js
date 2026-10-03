import client, { data } from './client';

export const certificatesApi = {
  byStudent: (studentId) => data(client.get(`/api/certificates/student/${studentId}`)),
  // Public endpoint: never send a (possibly expired) token, and never trigger the 401 → logout flow.
  verify: (code) => data(client.get(`/api/certificates/verify/${encodeURIComponent(code)}`, { skipAuth: true })),
  /** The PDF endpoint needs the Authorization header, so fetch as a blob and trigger a download. */
  async download(cert) {
    const res = await client.get(`/api/certificates/${cert.id}/download`, { responseType: 'blob' });
    const blob = new Blob([res.data], { type: 'application/pdf' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `certificate-${cert.certificateNumber || cert.id}.pdf`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  },
};
