import { useState, useEffect } from 'react';
import { Table, Button, Space, Modal, Input, App, DatePicker } from 'antd';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';

interface ShareItem {
  id: number;
  code: string;
  fileName: string;
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
        fileId: Number(shareFileId),
        password: sharePassword || undefined,
        expireAt: shareExpireAt || undefined,
        maxDownloads: shareMaxDownloads || undefined,
      });
      message.success('Share created');
      setCreateOpen(false);
      fetchShares();
    } catch {}
  };

  const handleCancel = async (id: number) => {
    try {
      await apiClient.delete(`/shares/${id}`);
      message.success('Share cancelled');
      fetchShares();
    } catch {}
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
          <Button size="small" onClick={() => { navigator.clipboard.writeText(r.code); message.success('Code copied'); }}>{t('common.copy')}</Button>
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
        <Input placeholder={t('share.password') + ' (optional)'} value={sharePassword} onChange={(e) => setSharePassword(e.target.value)} className="mb-3" />
        <DatePicker showTime placeholder={t('share.expire')} onChange={(d) => setShareExpireAt(d?.toISOString() || null)} className="mb-3 w-full" />
        <Input type="number" placeholder={t('share.maxDownloads')} onChange={(e) => setShareMaxDownloads(Number(e.target.value) || null)} />
      </Modal>
    </div>
  );
}
