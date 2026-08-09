import { createBrowserRouter, Navigate } from 'react-router-dom';
import MainLayout from '../components/layout/MainLayout';
import AuthGuard from '../components/auth/AuthGuard';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import FileListPage from '../pages/files/FileListPage';
import RecycleBinPage from '../pages/files/RecycleBinPage';
import SearchPage from '../pages/files/SearchPage';
import ShareManagementPage from '../pages/shares/ShareManagementPage';
import ShareAccessPage from '../pages/shares/ShareAccessPage';
import NotificationListPage from '../pages/notifications/NotificationListPage';
import KbProgressPage from '../pages/kb/KbProgressPage';
import RagChatPage from '../pages/rag/RagChatPage';
import NotFoundPage from '../pages/NotFoundPage';

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/share/:code', element: <ShareAccessPage /> },
  {
    path: '/',
    element: <AuthGuard><MainLayout /></AuthGuard>,
    children: [
      { index: true, element: <Navigate to="/files" replace /> },
      { path: 'files', element: <FileListPage /> },
      { path: 'recycle', element: <RecycleBinPage /> },
      { path: 'search', element: <SearchPage /> },
      { path: 'shares', element: <ShareManagementPage /> },
      { path: 'notifications', element: <NotificationListPage /> },
      { path: 'kb/progress', element: <KbProgressPage /> },
      { path: 'rag/chat', element: <RagChatPage /> },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]);

export default router;
