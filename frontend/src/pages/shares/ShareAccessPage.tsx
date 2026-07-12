import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Card, Input, Button, App, Spin, Result } from 'antd';
import { DownloadOutlined, LockOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import axios from 'axios';

interface ShareInfo {
  fileName: string;
  fileSize: number;
  isDir: boolean;
  expireAt: string | null;
  maxDownloads: number | null;
  downloadCount: number;
}

export default function ShareAccessPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const { code } = useParams<{ code: string }>();
  const [loading, setLoading] = useState(true);
  const [shareInfo, setShareInfo] = useState<ShareInfo | null>(null);
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [downloadUrl, setDownloadUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!code) return;
    axios.get(`/api/shares/${code}/info`)
      .then(({ data }) => {
        setShareInfo(data.data);
        setLoading(false);
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
    if (!code) return;
    setSubmitting(true);
    try {
      const { data } = await axios.post(`/api/shares/${code}/access`, {
        password: password || undefined,
      });
      setDownloadUrl(data.data.downloadUrl);
      message.success(t('share.accessSuccess'));
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

  return (
    <div className="flex justify-center items-center min-h-screen bg-gray-50 dark:bg-gray-900">
      <Card className="w-full max-w-md shadow-lg">
        <div className="text-center mb-6">
          <h2 className="text-xl font-semibold">{shareInfo.fileName}</h2>
          <p className="text-gray-500 mt-2">
            {shareInfo.isDir ? t('share.folderShare') : formatSize(shareInfo.fileSize)}
          </p>
        </div>

        {!downloadUrl ? (
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
        ) : (
          <div className="text-center">
            <p className="text-green-600 mb-4">{t('share.verified')}</p>
            <Button
              type="primary"
              size="large"
              icon={<DownloadOutlined />}
              onClick={() => window.open(downloadUrl, '_blank')}
            >
              {t('common.download')}
            </Button>
          </div>
        )}

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
