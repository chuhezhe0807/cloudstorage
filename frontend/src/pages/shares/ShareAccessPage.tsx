import { useState, useEffect, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { Card, Input, Button, App, Spin, Result, Tree, Typography, Space } from 'antd';
import { DownloadOutlined, LockOutlined, FolderOutlined, FileOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';

interface ShareInfo {
  fileName: string;
  fileSize: string;
  isDir: boolean;
  hasPassword: boolean;
  expireAt: string | null;
  maxDownloads: number | null;
  downloadCount: number;
}

interface ShareFileNode {
  id: string;
  name: string;
  dir: boolean;
  size: string;
  children?: ShareFileNode[];
}

interface ShareAccessData {
  fileId: string;
  fileName: string;
  fileSize: string;
  dir: boolean;
  downloadUrl?: string;
  children?: ShareFileNode[];
  remainingDownloads?: number;
}

interface TreeNode {
  key: string;
  title: React.ReactNode;
  isDir: boolean;
  children?: TreeNode[];
}

export default function ShareAccessPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const { code } = useParams<{ code: string }>();
  const [loading, setLoading] = useState(true);
  const [shareInfo, setShareInfo] = useState<ShareInfo | null>(null);
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [accessData, setAccessData] = useState<ShareAccessData | null>(null);
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const doAccess = async (pwd?: string) => {
    if (!code) return;
    setSubmitting(true);
    try {
      const { data } = await apiClient.post(`/shares/${code}/access`, {
        password: pwd || undefined,
      });
      setAccessData(data.data);
      if (pwd) message.success(t('share.accessSuccess'));
    } catch (err: any) {
      const status = err.response?.status;
      const errCode = err.response?.data?.code;
      if (status === 429 || errCode === 'SHARE_LOCKED') {
        message.error(t('share.locked'));
      } else if (status === 410) {
        setError('share.expired');
      } else {
        message.error(t('share.wrongPassword'));
      }
    }
    setSubmitting(false);
  };

  useEffect(() => {
    if (!code) return;
    apiClient.get(`/shares/${code}/info`)
      .then(({ data }) => {
        const info = data.data;
        setShareInfo(info);
        setLoading(false);
        if (!info.hasPassword) {
          doAccess();
        }
      })
      .catch((err) => {
        const status = err.response?.status;
        if (status === 410) {
          setError('share.expired');
        } else if (status === 404) {
          setError('share.notFound');
        } else {
          setError('share.loadFailed');
        }
        setLoading(false);
      });
  }, [code]);

  const handleAccess = async () => {
    await doAccess(password);
  };

  const treeData = useMemo(() => {
    if (!accessData?.children) return [];
    const convert = (nodes: ShareFileNode[]): TreeNode[] =>
      nodes.map((node) => ({
        key: node.id,
        title: (
          <Space>
            {node.dir ? <FolderOutlined style={{ color: '#faad14' }} /> : <FileOutlined />}
            <span>{node.name}</span>
            {!node.dir && <span style={{ color: '#999', fontSize: 12 }}>{formatSize(Number(node.size))}</span>}
          </Space>
        ),
        isDir: node.dir,
        children: node.children ? convert(node.children) : undefined,
      }));
    return convert(accessData.children);
  }, [accessData]);

  const getTopLevelCheckedKeys = (keys: React.Key[]): React.Key[] => {
    const keySet = new Set(keys.map(String));
    const parentMap = new Map<string, string>();

    const buildParentMap = (nodes: TreeNode[], parentKey?: string) => {
      for (const node of nodes) {
        if (parentKey) {
          parentMap.set(node.key, parentKey);
        }
        if (node.children) {
          buildParentMap(node.children, node.key);
        }
      }
    };
    buildParentMap(treeData);

    return keys.filter((key) => {
      const k = String(key);
      let current = parentMap.get(k);
      while (current) {
        if (keySet.has(current)) return false;
        current = parentMap.get(current);
      }
      return true;
    });
  };

  const handleDownload = async () => {
    if (!code || checkedKeys.length === 0) return;
    setDownloading(true);
    try {
      const topLevelKeys = getTopLevelCheckedKeys(checkedKeys);
      const fileIds = topLevelKeys.map((k) => String(k));
      const response = await apiClient.post(`/shares/${code}/download`, { fileIds }, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `share_${code}.zip`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      message.success(t('share.downloadSuccess'));
    } catch {
      message.error(t('share.downloadFailed'));
    }
    setDownloading(false);
  };

  if (loading) {
    return <div className="flex justify-center items-center min-h-screen"><Spin size="large" /></div>;
  }

  if (error) {
    return (
      <div className="flex justify-center items-center min-h-screen">
        <Result
          status="warning"
          title={t(error)}
          subTitle={t('share.invalidHint')}
        />
      </div>
    );
  }

  if (!shareInfo) return null;

  const showPasswordForm = shareInfo.hasPassword && !accessData;
  const showFileDownload = accessData && !accessData.dir;
  const showDirTree = accessData && accessData.dir;

  return (
    <div className="flex justify-center items-center min-h-screen bg-gray-50 dark:bg-gray-900">
      <Card className="w-full max-w-lg shadow-lg">
        <div className="text-center mb-4">
          <h2 className="text-xl font-semibold">{shareInfo.fileName}</h2>
          <p className="text-gray-500 mt-1">
            {shareInfo.isDir ? t('share.folderShare') : formatSize(Number(shareInfo.fileSize))}
          </p>
        </div>

        {showPasswordForm ? (
          <div>
            <Input
              prefix={<LockOutlined />}
              placeholder={t('share.password')}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onPressEnter={handleAccess}
              className="mb-4"
            />
            <Button
              type="primary"
              block
              loading={submitting}
              onClick={handleAccess}
            >
              {t('share.access')}
            </Button>
          </div>
        ) : submitting && !accessData ? (
          <div className="text-center py-4"><Spin /></div>
        ) : showDirTree ? (
          <div>
            <Typography.Text strong className="mb-2 block">{t('share.selectFiles')}</Typography.Text>
            <div className="max-h-80 overflow-auto border rounded p-2 mb-4">
              {treeData.length > 0 ? (
                <Tree
                  checkable
                  treeData={treeData}
                  checkedKeys={checkedKeys}
                  onCheck={(keys) => setCheckedKeys(keys as React.Key[])}
                />
              ) : (
                <Typography.Text type="secondary" className="block text-center py-4">{t('share.emptyFolder')}</Typography.Text>
              )}
            </div>
            <Button
              type="primary"
              block
              icon={<DownloadOutlined />}
              loading={downloading}
              disabled={checkedKeys.length === 0}
              onClick={handleDownload}
            >
              {t('share.downloadSelected')} ({checkedKeys.length})
            </Button>
          </div>
        ) : showFileDownload ? (
          <div className="text-center">
            <Button
              type="primary"
              size="large"
              icon={<DownloadOutlined />}
              onClick={() => window.open(accessData!.downloadUrl, '_blank')}
            >
              {t('common.download')}
            </Button>
          </div>
        ) : null}

        <div className="mt-4 text-center text-sm text-gray-400">
          {shareInfo.maxDownloads && (
            <span>{t('share.downloads')}: {shareInfo.downloadCount}/{shareInfo.maxDownloads}</span>
          )}
        </div>
      </Card>
    </div>
  );
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
  return (bytes / 1073741824).toFixed(1) + ' GB';
}
