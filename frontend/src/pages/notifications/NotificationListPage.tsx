import { useState, useEffect } from 'react';
import { List, Button, Badge, Space, App, Empty } from 'antd';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';

interface NotifItem {
  id: number;
  type: string;
  payload: string;
  read: boolean;
  createdAt: string;
}

export default function NotificationListPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [notifs, setNotifs] = useState<NotifItem[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchNotifs = async () => {
    setLoading(true);
    try {
      const { data } = await apiClient.get('/notifications', { params: { unread: false } });
      setNotifs(data.data?.records || []);
    } catch {}
    setLoading(false);
  };

  useEffect(() => { fetchNotifs(); }, []);

  const markRead = async (id: number) => {
    try {
      await apiClient.put(`/notifications/${id}/read`);
      fetchNotifs();
    } catch {}
  };

  const handleDelete = async (ids: number[]) => {
    try {
      await apiClient.delete('/notifications', { params: { ids: ids.join(',') } });
      message.success('Deleted');
      fetchNotifs();
    } catch {}
  };

  const renderPayload = (item: NotifItem) => {
    try {
      const p = JSON.parse(item.payload);
      return p.fileName || p.message || item.payload;
    } catch { return item.payload; }
  };

  return (
    <List
      loading={loading}
      dataSource={notifs}
      locale={{ emptyText: <Empty description={t('notif.unread')} /> }}
      renderItem={(item) => (
        <List.Item actions={[
          !item.read && <Button size="small" onClick={() => markRead(item.id)}>{t('notif.markRead')}</Button>,
          <Button size="small" danger onClick={() => handleDelete([item.id])}>{t('notif.delete')}</Button>,
        ].filter(Boolean)}>
          <List.Item.Meta title={<Space><Badge dot={!item.read} />{item.type}</Space>}
            description={renderPayload(item)} />
        </List.Item>
      )}
    />
  );
}
