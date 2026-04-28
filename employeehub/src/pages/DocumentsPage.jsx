import { useState, useEffect, useCallback, useRef } from 'react';
import TopBar from '../components/TopBar';
import { getMyDocuments, uploadDocument } from '../api/EmployeeService';

const DOC_TYPES = ['ID_DOCUMENT', 'CONTRACT', 'CERTIFICATE', 'PAYSLIP', 'OTHER'];
const DOC_LABELS = { ID_DOCUMENT: 'ID Document', CONTRACT: 'Contract', CERTIFICATE: 'Certificate', PAYSLIP: 'Payslip', OTHER: 'Other' };
const TYPE_ICONS = {
  ID_DOCUMENT: 'bi-person-vcard', CONTRACT: 'bi-file-earmark-text',
  CERTIFICATE: 'bi-award', PAYSLIP: 'bi-receipt', OTHER: 'bi-file-earmark',
};
const STATUS_COLOR = { VERIFIED: 'active', PENDING: 'pending', REJECTED: 'inactive' };

export default function DocumentsPage() {
  const [tab, setTab]         = useState('documents');
  const [docs, setDocs]       = useState([]);
  const [docType, setDocType] = useState('');
  const [file, setFile]       = useState(null);
  const [uploading, setUploading] = useState(false);
  const [feedback, setFeedback]   = useState(null);
  const fileRef = useRef();

  const load = useCallback(async () => {
    try {
      const res = await getMyDocuments();
      setDocs(res.data?.data ?? []);
    } catch {
      // silently fail
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleUpload = async e => {
    e.preventDefault();
    if (!file || !docType) return;
    setUploading(true);
    setFeedback(null);
    try {
      await uploadDocument(docType, file);
      setFeedback({ type: 'success', msg: 'Document uploaded successfully.' });
      setFile(null);
      setDocType('');
      if (fileRef.current) fileRef.current.value = '';
      await load();
      setTimeout(() => { setFeedback(null); setTab('documents'); }, 1500);
    } catch (err) {
      setFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Upload failed.' });
    } finally {
      setUploading(false);
    }
  };

  const fmtSize = bytes => bytes ? `${(bytes / 1024).toFixed(0)} KB` : '—';

  return (
    <>
      <TopBar title='Documents' breadcrumb='Employee Hub / Documents' />
      <div className='page'>

        <div className='profile-tabs' style={{ marginBottom: '1.5rem' }}>
          {[['documents', 'My Documents'], ['upload', 'Upload Document']].map(([key, label]) => (
            <button key={key} className={`profile-tab${tab === key ? ' active' : ''}`} onClick={() => setTab(key)}>
              {label}
            </button>
          ))}
        </div>

        {/* ── My Documents ── */}
        {tab === 'documents' && (
          <div className='card'>
            <div className='card__header'>
              <span className='card__title'>My Documents</span>
              <button className='btn btn-sm' onClick={() => setTab('upload')}><i className='bi bi-upload'></i> Upload</button>
            </div>
            <div className='table-wrap'>
              <table>
                <thead><tr><th>Document</th><th>Type</th><th>Size</th><th>Uploaded</th><th>Status</th></tr></thead>
                <tbody>
                  {docs.length === 0 ? (
                    <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No documents uploaded yet.</td></tr>
                  ) : docs.map(doc => (
                    <tr key={doc.id}>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                          <i className={`bi ${TYPE_ICONS[doc.documentType] ?? 'bi-file-earmark'}`} style={{ color: 'var(--brand)', fontSize: '1.1rem' }}></i>
                          <span style={{ fontWeight: 500 }}>{doc.fileName}</span>
                        </div>
                      </td>
                      <td style={{ color: 'var(--text-secondary)' }}>{DOC_LABELS[doc.documentType] ?? doc.documentType}</td>
                      <td style={{ color: 'var(--text-muted)' }}>{fmtSize(doc.fileSize)}</td>
                      <td style={{ color: 'var(--text-muted)' }}>{doc.createdAt?.split('T')[0]}</td>
                      <td><span className={`badge badge--${STATUS_COLOR[doc.status] ?? 'pending'}`}>{doc.status}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── Upload ── */}
        {tab === 'upload' && (
          <div className='card' style={{ maxWidth: 520 }}>
            <div className='card__header'><span className='card__title'>Upload Document</span></div>
            <div className='card__body'>
              {feedback && (
                <p className={`feedback feedback--${feedback.type}`}>
                  <i className={`bi ${feedback.type === 'success' ? 'bi-check-circle' : 'bi-exclamation-circle'}`}></i> {feedback.msg}
                </p>
              )}
              <form onSubmit={handleUpload}>
                <div className='form-group' style={{ marginBottom: '1rem' }}>
                  <label className='form-label'>Document Type</label>
                  <select className='form-control' value={docType} onChange={e => setDocType(e.target.value)} required>
                    <option value=''>— Select type —</option>
                    {DOC_TYPES.map(t => <option key={t} value={t}>{DOC_LABELS[t]}</option>)}
                  </select>
                </div>
                <div className='form-group' style={{ marginBottom: '1.25rem' }}>
                  <label className='form-label'>File</label>
                  <div className='doc-dropzone' onClick={() => fileRef.current?.click()}>
                    <i className='bi bi-cloud-upload'></i>
                    <p>{file ? file.name : 'Click to select a file'}</p>
                    <span>{file ? fmtSize(file.size) : 'PDF, JPG, PNG up to 10MB'}</span>
                    <input ref={fileRef} type='file' accept='.pdf,.jpg,.jpeg,.png' style={{ display: 'none' }} onChange={e => setFile(e.target.files[0])} />
                  </div>
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                  <button type='button' className='btn btn-ghost' onClick={() => { setFile(null); setDocType(''); }}>Clear</button>
                  <button type='submit' className='btn' disabled={uploading || !file || !docType}>
                    {uploading ? 'Uploading...' : <><i className='bi bi-upload'></i> Upload</>}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

      </div>
    </>
  );
}
