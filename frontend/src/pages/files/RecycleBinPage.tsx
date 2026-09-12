import { useState, useEffect, Suspense, lazy } from 'react';
import { Table, Button, Space, App, Spin } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';

const FilePreviewModal = lazy(() => import('../../components/file/FilePreviewModal'));

interface FileItem {
  id: string;
  name: string;
  isDir: boolean;
  size: number;
  deletedAt: string;
  updatedAt: string;
}

export default function RecycleBinPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [files, setFiles] = useState<FileItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewFile, setPreviewFile] = useState<FileItem | null>(null);

  const fetchRecycled = async () => {
    setLoading(true);
    try {
      const { data } = await apiClient.get('/files/recycle');
      setFiles(data.data?.records || []);
    } catch {
      message.error('Failed to load recycle bin');
    }
    setLoading(false);
  };

  useEffect(() => { fetchRecycled(); }, []);

  const handleRestore = async (id: string) => {
    try {
      await apiClient.put(`/files/${id}/restore`);
      message.success(t('common.restore'));
      fetchRecycled();
    } catch {}
  };

  const handlePermanentDelete = async (id: string) => {
    try {
      await apiClient.delete(`/files/${id}/permanent`);
      message.success('Permanently deleted');
      fetchRecycled();
    } catch {}
  };

  const handleDownload = async (id: string) => {
    try {
      const { data } = await apiClient.get(`/storage/download/${id}`);
      window.open(data.data?.downloadUrl || `/api/storage/download/${id}`, '_blank');
    } catch {
      message.error('Download failed');
    }
  };

  const columns = [
    { title: t('file.name'), dataIndex: 'name', key: 'name' },
    { title: t('file.modified'), dataIndex: 'deletedAt', key: 'deletedAt' },
    { title: 'Actions', key: 'actions', render: (_: unknown, r: FileItem) => (
        <Space>
          {!r.isDir && <Button size="small" icon={<EyeOutlined />} onClick={() => { setPreviewFile(r); setPreviewOpen(true); }}>{t('common.preview')}</Button>}
          <Button size="small" onClick={() => handleRestore(r.id)}>{t('common.restore')}</Button>
          <Button size="small" danger onClick={() => handlePermanentDelete(r.id)}>{t('common.delete')}</Button>
        </Space>
      )},
  ];

  return (
    <div>
      <Table columns={columns} dataSource={files} rowKey="id" loading={loading} />
      {previewOpen && (
        <Suspense fallback={<div className="flex justify-center" style={{ minHeight: 200 }}><Spin /></div>}>
          <FilePreviewModal open={previewOpen} file={previewFile} onClose={() => setPreviewOpen(false)} onDownload={handleDownload} />
        </Suspense>
      )}
    </div>
  );
}
