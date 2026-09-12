import { useState, useEffect, Suspense, lazy } from 'react';
import { Table, Button, Space, Modal, Input, App, DatePicker, Spin } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import type { FilePreviewItem } from '../../components/file/FilePreviewModal';

const FilePreviewModal = lazy(() => import('../../components/file/FilePreviewModal'));

interface ShareItem {
  id: string;
  fileId: number;
  code: string;
  fileName: string;
  isDir: boolean;
  hasPassword: boolean;
  downloadCount: number;
  maxDownloads: number | null;
  expireAt: string | null;
  status: string;
}

export default function ShareManagementPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [shares, setShares] = useState<ShareItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [shareFileId, setShareFileId] = useState('');
  const [sharePassword, setSharePassword] = useState('');
  const [shareExpireAt, setShareExpireAt] = useState<string | null>(null);
  const [shareMaxDownloads, setShareMaxDownloads] = useState<number | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [editPassword, setEditPassword] = useState('');
  const [editExpireAt, setEditExpireAt] = useState<string | null>(null);
  const [editMaxDownloads, setEditMaxDownloads] = useState<number | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewFile, setPreviewFile] = useState<FilePreviewItem | null>(null);

  const fetchShares = async () => {
    setLoading(true);
    try {
      const { data } = await apiClient.get('/shares');
      setShares(data.data || []);
    } catch {}
    setLoading(false);
  };

  useEffect(() => { fetchShares(); }, []);

  const handleCreate = async () => {
    try {
      await apiClient.post('/shares', {
        fileId: shareFileId,
        password: sharePassword || undefined,
        expireAt: shareExpireAt || undefined,
        maxDownloads: shareMaxDownloads || undefined,
      });
      message.success('Share created');
      setCreateOpen(false);
      fetchShares();
    } catch {}
  };

  const handleCancel = async (id: string) => {
    try {
      await apiClient.delete(`/shares/${id}`);
      message.success('Share cancelled');
      fetchShares();
    } catch {}
  };

  const openEditModal = (item: ShareItem) => {
    setEditId(item.id);
    setEditPassword('');
    setEditExpireAt(item.expireAt);
    setEditMaxDownloads(item.maxDownloads);
    setEditOpen(true);
  };

  const handleUpdate = async () => {
    if (!editId) return;
    try {
      await apiClient.patch(`/shares/${editId}`, {
        password: editPassword || undefined,
        expireAt: editExpireAt || undefined,
        maxDownloads: editMaxDownloads,
      });
      message.success('Share updated');
      setEditOpen(false);
      fetchShares();
    } catch {
      message.error('Update failed');
    }
  };

  const handleDownload = async (id: string) => {
    try {
      const { data } = await apiClient.get(`/storage/download/${id}`);
      window.open(data.data?.downloadUrl || `/api/storage/download/${id}`, '_blank');
    } catch {
      message.error('Download failed');
    }
  };

  const openPreview = (r: ShareItem) => {
    setPreviewFile({ id: String(r.fileId), name: r.fileName, isDir: r.isDir, size: 0, updatedAt: '' });
    setPreviewOpen(true);
  };

  const columns = [
    { title: 'File', dataIndex: 'fileName' },
    { title: t('share.code'), dataIndex: 'code' },
    { title: 'Password', dataIndex: 'hasPassword', render: (v: boolean) => v ? 'Yes' : 'No' },
    { title: 'Downloads', render: (_: unknown, r: ShareItem) => `${r.downloadCount}${r.maxDownloads ? '/' + r.maxDownloads : ''}` },
    { title: t('share.expire'), dataIndex: 'expireAt' },
    { title: 'Status', dataIndex: 'status' },
    { title: 'Actions', key: 'actions', render: (_: unknown, r: ShareItem) => (
        <Space>
          {!r.isDir && <Button size="small" icon={<EyeOutlined />} onClick={() => openPreview(r)}>{t('common.preview')}</Button>}
          <Button size="small" onClick={() => { navigator.clipboard.writeText(`${window.location.origin}/share/${r.code}`); message.success('Link copied'); }}>{t('common.copy')}</Button>
          {r.status === 'active' && <Button size="small" onClick={() => openEditModal(r)}>{t('common.edit')}</Button>}
          {r.status === 'active' && <Button size="small" danger onClick={() => handleCancel(r.id)}>{t('common.delete')}</Button>}
        </Space>
      )},
  ];

  return (
    <div>
      <Button type="primary" onClick={() => setCreateOpen(true)} className="mb-4">{t('share.create')}</Button>
      <Table columns={columns} dataSource={shares} rowKey="id" loading={loading} />
      <Modal open={createOpen} title={t('share.create')} onOk={handleCreate} onCancel={() => setCreateOpen(false)}>
        <Input placeholder="File ID" value={shareFileId} onChange={(e) => setShareFileId(e.target.value)} className="mb-3" />
        <Input placeholder={t('share.password') + ' (' + t('share.optional') + ')'} value={sharePassword} onChange={(e) => setSharePassword(e.target.value)} className="mb-3" />
        <DatePicker showTime placeholder={t('share.expire')} onChange={(d) => setShareExpireAt(d?.toISOString() || null)} className="mb-3 w-full" />
        <Input type="number" placeholder={t('share.maxDownloads')} onChange={(e) => setShareMaxDownloads(Number(e.target.value) || null)} />
      </Modal>
      <Modal open={editOpen} title={t('share.update')} onOk={handleUpdate} onCancel={() => setEditOpen(false)}>
        <Input
          placeholder={t('share.password') + ' (' + t('share.leaveEmpty') + ')'}
          value={editPassword}
          onChange={(e) => setEditPassword(e.target.value)}
          className="mb-3"
        />
        <DatePicker
          showTime
          placeholder={t('share.expire')}
          onChange={(d) => setEditExpireAt(d?.toISOString() || null)}
          className="mb-3 w-full"
        />
        <Input
          type="number"
          placeholder={t('share.maxDownloads')}
          value={editMaxDownloads ?? ''}
          onChange={(e) => setEditMaxDownloads(e.target.value ? Number(e.target.value) : null)}
        />
      </Modal>
      {previewOpen && (
        <Suspense fallback={<div className="flex justify-center" style={{ minHeight: 200 }}><Spin /></div>}>
          <FilePreviewModal open={previewOpen} file={previewFile} onClose={() => setPreviewOpen(false)} onDownload={handleDownload} />
        </Suspense>
      )}
    </div>
  );
}
