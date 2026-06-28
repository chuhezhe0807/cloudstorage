import { useState, useEffect, useCallback } from 'react';
import { Table, Button, Breadcrumb, Space, Modal, Input, App } from 'antd';
import { HomeOutlined, UploadOutlined, FolderAddOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import FileUploadModal from '../../components/upload/FileUploadModal';

interface FileItem {
  id: number;
  name: string;
  isDir: boolean;
  size: number;
  updatedAt: string;
}

export default function FileListPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [files, setFiles] = useState<FileItem[]>([]);
  const [parentId, setParentId] = useState<number>(0);
  const [breadcrumb, setBreadcrumb] = useState<{ id: number; name: string }[]>([{ id: 0, name: 'Root' }]);
  const [loading, setLoading] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [mkdirOpen, setMkdirOpen] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');
  const [renameOpen, setRenameOpen] = useState(false);
  const [renameId, setRenameId] = useState<number | null>(null);
  const [renameName, setRenameName] = useState('');

  const fetchFiles = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await apiClient.get('/files', { params: { parentId } });
      setFiles(data.data || []);
    } catch {
      message.error('Failed to load files');
    }
    setLoading(false);
  }, [parentId]);

  useEffect(() => {
    fetchFiles();
  }, [fetchFiles]);

  const enterFolder = (id: number, name: string) => {
    setParentId(id);
    setBreadcrumb((prev) => [...prev, { id, name }]);
  };

  const navigateBreadcrumb = (index: number) => {
    const item = breadcrumb[index];
    setParentId(item.id);
    setBreadcrumb((prev) => prev.slice(0, index + 1));
  };

  const handleMkdir = async () => {
    try {
      await apiClient.post('/files/mkdir', { parentId, name: newFolderName });
      message.success('Folder created');
      setMkdirOpen(false);
      setNewFolderName('');
      fetchFiles();
    } catch {}
  };

  const handleRename = async () => {
    if (!renameId) return;
    try {
      await apiClient.patch(`/files/${renameId}/rename`, { newName: renameName });
      message.success('Renamed');
      setRenameOpen(false);
      fetchFiles();
    } catch {}
  };

  const handleDelete = async (id: number) => {
    try {
      await apiClient.delete(`/files/${id}`);
      message.success('Deleted');
      fetchFiles();
    } catch {}
  };

  const handleDownload = async (id: number) => {
    try {
      const { data } = await apiClient.get(`/storage/download/${id}`);
      window.open(data.data?.downloadUrl || `/api/storage/download/${id}`, '_blank');
    } catch {
      message.error('Download failed');
    }
  };

  const columns = [
    { title: t('file.name'), dataIndex: 'name', key: 'name', render: (_: string, r: FileItem) =>
        r.isDir ? <Button type="link" onClick={() => enterFolder(r.id, r.name)}>{r.name}</Button> : r.name },
    { title: t('file.size'), dataIndex: 'size', key: 'size', render: (v: number) => v > 0 ? formatSize(v) : '-' },
    { title: t('file.modified'), dataIndex: 'updatedAt', key: 'updatedAt' },
    { title: 'Actions', key: 'actions', render: (_: unknown, r: FileItem) => (
        <Space>
          <Button size="small" onClick={() => { setRenameId(r.id); setRenameName(r.name); setRenameOpen(true); }}>{t('common.rename')}</Button>
          <Button size="small" danger onClick={() => handleDelete(r.id)}>{t('common.delete')}</Button>
          {!r.isDir && <Button size="small" onClick={() => handleDownload(r.id)}>{t('common.download')}</Button>}
        </Space>
      )},
  ];

  return (
    <div>
      <Breadcrumb items={breadcrumb.map((b, i) => ({ title: <a onClick={() => navigateBreadcrumb(i)}>{b.name}</a> }))} />
      <div className="my-3 flex gap-2">
        <Button icon={<UploadOutlined />} onClick={() => setUploadOpen(true)}>{t('common.upload')}</Button>
        <Button icon={<FolderAddOutlined />} onClick={() => setMkdirOpen(true)}>{t('common.newFolder')}</Button>
      </div>
      <Table columns={columns} dataSource={files} rowKey="id" loading={loading} pagination={false} />

      <Modal open={mkdirOpen} title={t('common.newFolder')} onOk={handleMkdir} onCancel={() => setMkdirOpen(false)}>
        <Input value={newFolderName} onChange={(e) => setNewFolderName(e.target.value)} placeholder="Folder name" />
      </Modal>
      <Modal open={renameOpen} title={t('common.rename')} onOk={handleRename} onCancel={() => setRenameOpen(false)}>
        <Input value={renameName} onChange={(e) => setRenameName(e.target.value)} />
      </Modal>
      <FileUploadModal open={uploadOpen} parentId={parentId} onClose={() => setUploadOpen(false)} onSuccess={fetchFiles} />
    </div>
  );
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
  return (bytes / 1073741824).toFixed(1) + ' GB';
}
