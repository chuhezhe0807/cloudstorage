import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { App } from 'antd';
import './i18n';
import './index.css';
import router from './router';
import ErrorBoundary from './components/auth/ErrorBoundary';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <App>
        <RouterProvider router={router} />
      </App>
    </ErrorBoundary>
  </StrictMode>
);
