import { useState, useEffect } from 'react';
import { Table, Button, Space, App } from 'antd';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';

interface FileItem {
  id: string;
  name: string;
  size: number;
  deletedAt: string;
}

export default function RecycleBinPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [files, setFiles] = useState<FileItem[]>([]);
  const [loading, setLoading] = useState(false);

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

  const columns = [
    { title: t('file.name'), dataIndex: 'name', key: 'name' },
    { title: t('file.modified'), dataIndex: 'deletedAt', key: 'deletedAt' },
    { title: 'Actions', key: 'actions', render: (_: unknown, r: FileItem) => (
        <Space>
          <Button size="small" onClick={() => handleRestore(r.id)}>{t('common.restore')}</Button>
          <Button size="small" danger onClick={() => handlePermanentDelete(r.id)}>{t('common.delete')}</Button>
        </Space>
      )},
  ];

  return <Table columns={columns} dataSource={files} rowKey="id" loading={loading} />;
}
