import { Form, Input, Button, App, Card } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import apiClient from '../api/client';
import { useAuthStore } from '../store/authStore';

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const setAuth = useAuthStore((s) => s.setAuth);

  const onFinish = async (values: { account: string; password: string }) => {
    try {
      const { data } = await apiClient.post('/auth/login', values);
      setAuth(data.data.accessToken, data.data.refreshToken, 0, 0);
      message.success(t('auth.login'));
      navigate('/files', { replace: true });
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Login failed');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100 dark:bg-gray-900">
      <Card title={t('auth.login')} className="w-96">
        <Form onFinish={onFinish}>
          <Form.Item name="account" rules={[{ required: true, message: t('auth.account') }]}>
            <Input prefix={<UserOutlined />} placeholder={t('auth.account')} />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: t('auth.password') }]}>
            <Input.Password prefix={<LockOutlined />} placeholder={t('auth.password')} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              {t('auth.loginBtn')}
            </Button>
          </Form.Item>
          <div className="text-center">
            <Link to="/register">{t('auth.noAccount')}</Link>
          </div>
        </Form>
      </Card>
    </div>
  );
}
