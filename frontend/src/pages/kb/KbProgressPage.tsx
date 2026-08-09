import { useState, useEffect, useCallback } from 'react';
import { Table, Progress, Tag, Button } from 'antd';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';

interface FailedFile {
  fileName: string;
  reason: string;
}

interface KbProgress {
  kbId: string;
  fileId: string;
  folderName: string;
  totalFiles: number;
  processedFiles: number;
  status: string;
  failedFiles: FailedFile[];
}

export default function KbProgressPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<KbProgress[]>([]);
  const [loading, setLoading] = useState(false);

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const { data: res } = await apiClient.get('/kb/progress');
      setData(res.data || []);
    } catch {
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    fetch();
    const timer = setInterval(fetch, 5000);
    return () => clearInterval(timer);
  }, [fetch]);

  const statusTag = (status: string) => {
    const colorMap: Record<string, string> = {
      ready: 'green',
      processing: 'blue',
      partial: 'orange',
      failed: 'red',
    };
    return <Tag color={colorMap[status] || 'default'}>{t('kb.' + status)}</Tag>;
  };

  const columns = [
    { title: t('kb.folderName'), dataIndex: 'folderName', key: 'folderName' },
    { title: t('kb.totalFiles'), dataIndex: 'totalFiles', key: 'totalFiles' },
    { title: t('kb.processedFiles'), dataIndex: 'processedFiles', key: 'processedFiles' },
    {
      title: t('kb.progress'), key: 'progress', render: (_: unknown, r: KbProgress) => {
        const pct = r.totalFiles > 0 ? Math.round((r.processedFiles / r.totalFiles) * 100) : 0;
        return <Progress percent={pct} size="small" />;
      },
    },
    { title: t('kb.status'), dataIndex: 'status', key: 'status', render: (s: string) => statusTag(s) },
  ];

  const expandedRowRender = (record: KbProgress) => {
    if (!record.failedFiles || record.failedFiles.length === 0) return null;
    return (
      <Table
        rowKey="fileName"
        columns={[
          { title: t('file.name'), dataIndex: 'fileName', key: 'fileName' },
          { title: t('kb.failedFiles'), dataIndex: 'reason', key: 'reason' },
        ]}
        dataSource={record.failedFiles}
        pagination={false}
        size="small"
      />
    );
  };

  return (
    <div>
      <h2 className="text-xl font-bold mb-4">{t('kb.progress')}</h2>
      <Table
        columns={columns}
        dataSource={data}
        rowKey="kbId"
        loading={loading}
        expandable={{ expandedRowRender }}
        pagination={false}
      />
    </div>
  );
}
