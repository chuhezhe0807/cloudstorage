import { useState, useEffect, useCallback, Suspense, lazy } from 'react';
import { Table, Button, Breadcrumb, Space, Modal, Input, App, DatePicker, Spin } from 'antd';
import { HomeOutlined, UploadOutlined, FolderAddOutlined, ShareAltOutlined, EyeOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import FileUploadModal from '../../components/upload/FileUploadModal';

const FilePreviewModal = lazy(() => import('../../components/file/FilePreviewModal'));

interface FileItem {
  id: string;
  name: string;
  isDir: boolean;
  size: number;
  updatedAt: string;
}

export default function FileListPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [files, setFiles] = useState<FileItem[]>([]);
  const [parentId, setParentId] = useState<string>('0');
  const [breadcrumb, setBreadcrumb] = useState<{ id: string; name: string }[]>([{ id: '0', name: 'Root' }]);
  const [loading, setLoading] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [mkdirOpen, setMkdirOpen] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');
  const [renameOpen, setRenameOpen] = useState(false);
  const [renameId, setRenameId] = useState<string | null>(null);
  const [renameName, setRenameName] = useState('');
  const [shareOpen, setShareOpen] = useState(false);
  const [shareFileId, setShareFileId] = useState<string | null>(null);
  const [sharePassword, setSharePassword] = useState('');
  const [shareExpireAt, setShareExpireAt] = useState<string | null>(null);
  const [shareMaxDownloads, setShareMaxDownloads] = useState<number | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewFile, setPreviewFile] = useState<FileItem | null>(null);

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

  const enterFolder = (id: string, name: string) => {
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

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: t('common.confirmDelete'),
      onOk: async () => {
        try {
          await apiClient.delete(`/files/${id}`);
          message.success('Deleted');
          fetchFiles();
        } catch {}
      },
    });
  };

  const handleDownload = async (id: string, isDir: boolean) => {
    try {
      if (isDir) {
        const response = await apiClient.post('/storage/download-batch', { fileIds: [id] }, {
          responseType: 'blob',
        });
        const url = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', 'download.zip');
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
      } else {
        const { data } = await apiClient.get(`/storage/download/${id}`);
        window.open(data.data?.downloadUrl || `/api/storage/download/${id}`, '_blank');
      }
    } catch {
      message.error('Download failed');
    }
  };

  const openShareModal = (id: string) => {
    setShareFileId(id);
    setSharePassword('');
    setShareExpireAt(null);
    setShareMaxDownloads(null);
    setShareOpen(true);
  };

  const handleShare = async () => {
    if (!shareFileId) return;
    try {
      const { data } = await apiClient.post('/shares', {
        fileId: shareFileId,
        password: sharePassword || undefined,
        expireAt: shareExpireAt || undefined,
        maxDownloads: shareMaxDownloads || undefined,
      });
      const shareCode = data.data?.code;
      const shareUrl = `${window.location.origin}/share/${shareCode}`;
      navigator.clipboard.writeText(shareUrl);
      message.success(t('share.createSuccess'));
      setShareOpen(false);
    } catch {
      message.error(t('share.createFailed'));
    }
  };

  const columns = [
    { title: t('file.name'), dataIndex: 'name', key: 'name', render: (_: string, r: FileItem) =>
        r.isDir ? <Button type="link" onClick={() => enterFolder(r.id, r.name)}>{r.name}</Button> : r.name },
    { title: t('file.size'), dataIndex: 'size', key: 'size', render: (v: number) => v > 0 ? formatSize(v) : '-' },
    { title: t('file.modified'), dataIndex: 'updatedAt', key: 'updatedAt' },
    { title: 'Actions', key: 'actions', render: (_: unknown, r: FileItem) => (
        <Space>
          {!r.isDir && <Button size="small" icon={<EyeOutlined />} onClick={() => { setPreviewFile(r); setPreviewOpen(true); }}>{t('common.preview')}</Button>}
          <Button size="small" onClick={() => { setRenameId(r.id); setRenameName(r.name); setRenameOpen(true); }}>{t('common.rename')}</Button>
          <Button size="small" icon={<ShareAltOutlined />} onClick={() => openShareModal(r.id)}>{t('common.share')}</Button>
          <Button size="small" danger onClick={() => handleDelete(r.id)}>{t('common.delete')}</Button>
          {r.isDir && <Button size="small" onClick={() => handleDownload(r.id, true)}>{t('common.download')}</Button>}
          {(!r.isDir) && <Button size="small" onClick={() => handleDownload(r.id, false)}>{t('common.download')}</Button>}
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
      <Modal open={shareOpen} title={t('share.create')} onOk={handleShare} onCancel={() => setShareOpen(false)}>
        <Input
          placeholder={t('share.password') + ' (' + t('share.optional') + ')'}
          value={sharePassword}
          onChange={(e) => setSharePassword(e.target.value)}
          className="mb-3"
        />
        <DatePicker
          showTime
          placeholder={t('share.expire')}
          onChange={(d) => setShareExpireAt(d?.toISOString() || null)}
          className="mb-3 w-full"
        />
        <Input
          type="number"
          placeholder={t('share.maxDownloads')}
          onChange={(e) => setShareMaxDownloads(Number(e.target.value) || null)}
        />
      </Modal>
      <FileUploadModal open={uploadOpen} parentId={parentId} onClose={() => setUploadOpen(false)} onSuccess={fetchFiles} />
      {previewOpen && (
        <Suspense fallback={<div className="flex justify-center" style={{ minHeight: 200 }}><Spin /></div>}>
          <FilePreviewModal open={previewOpen} file={previewFile} onClose={() => setPreviewOpen(false)} onDownload={handleDownload} />
        </Suspense>
      )}
    </div>
  );
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
  return (bytes / 1073741824).toFixed(1) + ' GB';
}
