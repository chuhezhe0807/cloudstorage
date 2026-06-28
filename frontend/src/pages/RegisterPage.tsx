import { Form, Input, Button, App, Card } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import apiClient from '../api/client';

export default function RegisterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { message } = App.useApp();

  const onFinish = async (values: { account: string; password: string; confirmPassword: string }) => {
    if (values.password !== values.confirmPassword) {
      message.error('Passwords do not match');
      return;
    }
    try {
      await apiClient.post('/auth/register', { account: values.account, password: values.password });
      message.success(t('auth.registerSuccess'));
      navigate('/login');
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Registration failed');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100 dark:bg-gray-900">
      <Card title={t('auth.register')} className="w-96">
        <Form onFinish={onFinish}>
          <Form.Item name="account" rules={[{ required: true, min: 3 }]}>
            <Input prefix={<UserOutlined />} placeholder={t('auth.account')} />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, min: 6 }]}>
            <Input.Password prefix={<LockOutlined />} placeholder={t('auth.password')} />
          </Form.Item>
          <Form.Item name="confirmPassword" rules={[{ required: true, min: 6 }]}>
            <Input.Password prefix={<LockOutlined />} placeholder={t('auth.confirmPassword')} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              {t('auth.registerBtn')}
            </Button>
          </Form.Item>
          <div className="text-center">
            <Link to="/login">{t('auth.hasAccount')}</Link>
          </div>
        </Form>
      </Card>
    </div>
  );
}
