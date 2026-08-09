import { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  Layout,
  Menu,
  Button,
  Switch,
  Dropdown,
  Badge,
  ConfigProvider,
  theme as antTheme,
} from 'antd';
import {
  FolderOutlined,
  DeleteOutlined,
  ShareAltOutlined,
  BellOutlined,
  SettingOutlined,
  LogoutOutlined,
  SunOutlined,
  MoonOutlined,
  CloudServerOutlined,
  MessageOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../store/authStore';
import { useThemeStore } from '../../store/themeStore';
import apiClient from '../../api/client';

const { Header, Sider, Content } = Layout;

export default function MainLayout() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const { theme, setTheme } = useThemeStore();

  const isDark = theme === 'dark';

  const menuItems = [
    { key: '/files', icon: <FolderOutlined />, label: t('nav.files') },
    { key: '/recycle', icon: <DeleteOutlined />, label: t('nav.recycle') },
    { key: '/shares', icon: <ShareAltOutlined />, label: t('nav.shares') },
    { key: '/notifications', icon: <BellOutlined />, label: t('nav.notifications') },
    { key: '/kb/progress', icon: <CloudServerOutlined />, label: t('nav.kbProgress') },
    { key: '/rag/chat', icon: <MessageOutlined />, label: t('nav.ragChat') },
  ];

  const switchLang = () => {
    const next = i18n.language === 'zh' ? 'en' : 'zh';
    i18n.changeLanguage(next);
  };

  const handleLogout = async () => {
    try {
      await apiClient.post('/auth/logout');
    } catch {}
    clearAuth();
    navigate('/login');
  };

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
      }}
    >
      <Layout className="min-h-screen">
        <Sider
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          theme={isDark ? 'dark' : 'light'}
        >
          <div className="h-12 flex items-center justify-center font-bold text-lg">
            {collapsed ? 'CS' : 'CloudStorage'}
          </div>
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
            theme={isDark ? 'dark' : 'light'}
          />
        </Sider>
        <Layout>
          <Header className="flex items-center justify-end gap-3 px-4 bg-transparent">
            <Button size="small" onClick={switchLang}>
              {t('lang.switch')}
            </Button>
            <Switch
              checkedChildren={<MoonOutlined />}
              unCheckedChildren={<SunOutlined />}
              checked={isDark}
              onChange={(v) => setTheme(v ? 'dark' : 'light')}
            />
            <Button
              type="text"
              icon={<LogoutOutlined />}
              onClick={handleLogout}
            >
              {t('auth.logout')}
            </Button>
          </Header>
          <Content className="p-6">
            <Outlet />
          </Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}
