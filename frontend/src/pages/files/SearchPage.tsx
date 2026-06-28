import { useState } from 'react';
import { Input, Table, Select, Button, App } from 'antd';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import { SearchOutlined } from '@ant-design/icons';

interface FileItem {
  id: number;
  name: string;
  isDir: boolean;
  size: number;
  updatedAt: string;
}

export default function SearchPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState<FileItem[]>([]);
  const [loading, setLoading] = useState(false);

  const handleSearch = async () => {
    setLoading(true);
    try {
      const { data } = await apiClient.get('/files/search', { params: { keyword } });
      setResults(data.data?.records || []);
    } catch {
      message.error('Search failed');
    }
    setLoading(false);
  };

  const columns = [
    { title: t('file.name'), dataIndex: 'name' },
    { title: t('file.size'), dataIndex: 'size', render: (v: number) => v > 0 ? (v < 1048576 ? (v/1024).toFixed(1)+' KB' : (v/1048576).toFixed(1)+' MB') : '-' },
    { title: t('file.modified'), dataIndex: 'updatedAt' },
  ];

  return (
    <div>
      <div className="mb-4 flex gap-2">
        <Input value={keyword} onChange={(e) => setKeyword(e.target.value)}
               placeholder={t('common.search')} onPressEnter={handleSearch} style={{ width: 300 }} />
        <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>{t('common.search')}</Button>
      </div>
      <Table columns={columns} dataSource={results} rowKey="id" loading={loading} />
    </div>
  );
}
