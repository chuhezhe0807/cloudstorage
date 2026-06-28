import { useState, useRef } from 'react';
import { Modal, Upload, Progress, List, Button, App } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import { useAuthStore } from '../../store/authStore';

const { Dragger } = Upload;
const CHUNK_SIZE = 5 * 1024 * 1024;

interface Props {
  open: boolean;
  parentId: number;
  onClose: () => void;
  onSuccess: () => void;
}

export default function FileUploadModal({ open, parentId, onClose, onSuccess }: Props) {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [files, setFiles] = useState<{ name: string; progress: number; status: string }[]>([]);
  const cancelRef = useRef(false);

  const uploadFile = async (file: File) => {
    const idx = files.length;
    setFiles((prev) => [...prev, { name: file.name, progress: 0, status: 'uploading' }]);

    try {
      // 1. 计算 SHA-256
      const hash = await computeSHA256(file);
      // 2. 秒传校验
      try {
        const { status } = await apiClient.post('/storage/check-hash', {
          hash, fileName: file.name, fileSize: file.size,
        });
        if (status === 200) {
          setFiles((prev) => prev.map((f, i) => i === idx ? { ...f, progress: 100, status: 'done' } : f));
          onSuccess();
          return;
        }
      } catch {}

      // 3. 初始化分片上传
      const { data } = await apiClient.post('/storage/upload/init', {
        fileName: file.name, totalSize: file.size, hash, chunkSize: CHUNK_SIZE,
      });
      const { uploadId, chunkUrls } = data.data;

      // 4. 并发上传分片
      for (let i = 0; i < chunkUrls.length; i++) {
        if (cancelRef.current) return;
        const start = i * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, file.size);
        const blob = file.slice(start, end);
        await fetch(chunkUrls[i].url, { method: 'PUT', body: blob });

        const progress = Math.round(((i + 1) / chunkUrls.length) * 90);
        setFiles((prev) => prev.map((f, fi) => fi === idx ? { ...f, progress } : f));
      }

      // 5. 通知合并
      await apiClient.post(`/storage/upload/${uploadId}/complete`);
      setFiles((prev) => prev.map((f, i) => i === idx ? { ...f, progress: 100, status: 'done' } : f));
      onSuccess();

    } catch (err) {
      setFiles((prev) => prev.map((f, i) => i === idx ? { ...f, status: 'error' } : f));
      message.error(`Upload failed: ${file.name}`);
    }
  };

  return (
    <Modal open={open} title={t('common.upload')} onCancel={() => { cancelRef.current = true; onClose(); }} footer={null} width={600}>
      <Dragger multiple beforeUpload={(file) => { uploadFile(file); return false; }} showUploadList={false}>
        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
        <p>Click or drag files here to upload</p>
      </Dragger>
      <List
        dataSource={files}
        renderItem={(f) => (
          <List.Item>
            <div className="w-full">
              <div>{f.name}</div>
              <Progress percent={f.progress} status={f.status === 'error' ? 'exception' : f.status === 'done' ? 'success' : 'active'} />
            </div>
          </List.Item>
        )}
      />
    </Modal>
  );
}

async function computeSHA256(file: File): Promise<string> {
  const buffer = await file.arrayBuffer();
  const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
}
