import { useState, useEffect, useRef, useCallback } from 'react';
import { Modal, Spin, Image, Empty, Button, Tabs, App } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { renderAsync } from 'docx-preview';
import * as XLSX from 'xlsx';
import { Highlight, themes } from 'prism-react-renderer';
import apiClient from '../../api/client';
import { useThemeStore } from '../../store/themeStore';

export interface FilePreviewItem {
  id: string;
  name: string;
  isDir: boolean;
  size: number;
  updatedAt: string;
}

interface Props {
  open: boolean;
  file: FilePreviewItem | null;
  onClose: () => void;
  onDownload: (id: string, isDir: boolean) => void;
}

type PreviewType =
  | 'image'
  | 'pdf'
  | 'markdown'
  | 'text'
  | 'docx'
  | 'xlsx'
  | 'pptx'
  | 'office-online'
  | 'video'
  | 'audio'
  | 'code'
  | 'unsupported';

const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'ico'];
const MARKDOWN_EXTS = ['md', 'markdown'];
const TEXT_EXTS = ['txt', 'log', 'csv', 'ini', 'conf', 'env', 'properties', 'toml'];
const CODE_EXTS = [
  'py', 'js', 'ts', 'jsx', 'tsx', 'java', 'c', 'cpp', 'h', 'go', 'rs',
  'sql', 'css', 'html', 'xml', 'json', 'yaml', 'yml', 'sh', 'bat', 'ps1',
  'gradle', 'rb', 'php', 'kt', 'scala', 'swift', 'vue', 'scss', 'less',
  'dockerfile', 'makefile', 'lua', 'r', 'dart', 'proto',
];
const VIDEO_EXTS = ['mp4', 'webm', 'ogg', 'mov'];
const AUDIO_EXTS = ['mp3', 'wav', 'flac', 'aac', 'm4a'];

const EXT_TO_LANG: Record<string, string> = {
  js: 'javascript', ts: 'typescript', jsx: 'jsx', tsx: 'tsx',
  py: 'python', java: 'java', c: 'c', cpp: 'cpp', h: 'c',
  go: 'go', rs: 'rust', sql: 'sql', css: 'css',
  html: 'markup', xml: 'markup', json: 'json',
  yaml: 'yaml', yml: 'yaml', sh: 'bash', bat: 'bash',
  ps1: 'powershell', gradle: 'groovy', rb: 'ruby', php: 'php',
  kt: 'kotlin', scala: 'scala', swift: 'swift', vue: 'markup',
  scss: 'scss', less: 'less', dockerfile: 'docker', makefile: 'makefile',
  lua: 'lua', r: 'r', dart: 'dart', proto: 'protobuf',
};

function getCodeLanguage(name: string): string {
  const ext = getExt(name);
  return EXT_TO_LANG[ext] || 'clike';
}

function getExt(name: string): string {
  const idx = name.lastIndexOf('.');
  return idx >= 0 ? name.slice(idx + 1).toLowerCase() : '';
}

function getPreviewType(name: string): PreviewType {
  const ext = getExt(name);
  if (IMAGE_EXTS.includes(ext)) return 'image';
  if (ext === 'pdf') return 'pdf';
  if (MARKDOWN_EXTS.includes(ext)) return 'markdown';
  if (TEXT_EXTS.includes(ext)) return 'text';
  if (CODE_EXTS.includes(ext)) return 'code';
  if (ext === 'docx') return 'docx';
  if (ext === 'xlsx' || ext === 'xls') return 'xlsx';
  if (ext === 'pptx') return 'pptx';
  if (ext === 'doc' || ext === 'ppt') return 'office-online';
  if (VIDEO_EXTS.includes(ext)) return 'video';
  if (AUDIO_EXTS.includes(ext)) return 'audio';
  return 'unsupported';
}

export default function FilePreviewModal({ open, file, onClose, onDownload }: Props) {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const appTheme = useThemeStore((s) => s.theme);
  const [loading, setLoading] = useState(false);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [textContent, setTextContent] = useState('');
  const [docxData, setDocxData] = useState<ArrayBuffer | null>(null);
  const [xlsxSheets, setXlsxSheets] = useState<{ name: string; html: string }[]>([]);
  const docxContainerRef = useRef<HTMLDivElement>(null);

  const previewType = file ? getPreviewType(file.name) : 'unsupported';

  const fetchPreview = useCallback(async () => {
    if (!file) return;
    setLoading(true);
    setPreviewUrl(null);
    setTextContent('');
    setDocxData(null);
    setXlsxSheets([]);
    try {
      const { data } = await apiClient.get(`/storage/download/${file.id}`);
      const url: string | undefined = data.data?.downloadUrl;
      if (!url) throw new Error('No download URL');
      setPreviewUrl(url);

      if (previewType === 'markdown' || previewType === 'text' || previewType === 'code') {
        const resp = await fetch(url);
        const text = await resp.text();
        setTextContent(text);
      } else if (previewType === 'docx') {
        const resp = await fetch(url);
        const buf = await resp.arrayBuffer();
        setDocxData(buf);
      } else if (previewType === 'xlsx') {
        const resp = await fetch(url);
        const buf = await resp.arrayBuffer();
        const workbook = XLSX.read(buf, { type: 'array' });
        const sheets = workbook.SheetNames.map((name: string) => ({
          name,
          html: XLSX.utils.sheet_to_html(workbook.Sheets[name]),
        }));
        setXlsxSheets(sheets);
      }
    } catch {
      message.error(t('preview.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [file]);

  useEffect(() => {
    if (open && file) {
      fetchPreview();
    }
  }, [open, file, fetchPreview]);

  useEffect(() => {
    if (docxData && docxContainerRef.current && previewType === 'docx' && !loading) {
      docxContainerRef.current.innerHTML = '';
      renderAsync(docxData, docxContainerRef.current, undefined, {
        className: 'docx-preview',
        inWrapper: true,
        ignoreWidth: false,
        ignoreHeight: false,
        breakPages: true,
        experimental: true,
      }).catch(() => {
        message.error(t('preview.renderFailed'));
      });
    }
  }, [docxData, loading, previewType, message, t]);

  const renderPreview = () => {
    if (loading) {
      return (
        <div className="flex justify-center items-center" style={{ minHeight: 400 }}>
          <Spin size="large" />
        </div>
      );
    }

    switch (previewType) {
      case 'image':
        if (!previewUrl) return <Empty description={t('preview.noContent')} />;
        return (
          <div className="flex justify-center">
            <Image src={previewUrl} alt={file?.name} style={{ maxHeight: '70vh', objectFit: 'contain' }} />
          </div>
        );

      case 'pdf':
        if (!previewUrl) return <Empty description={t('preview.noContent')} />;
        return (
          <iframe src={previewUrl} style={{ width: '100%', height: '70vh', border: 'none' }} title="PDF Preview" />
        );

      case 'markdown':
        return (
          <div className="markdown-preview" style={{ maxHeight: '70vh', overflow: 'auto', padding: '16px 24px' }}>
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{textContent}</ReactMarkdown>
          </div>
        );

      case 'text':
        return (
          <pre className="text-preview" style={{ maxHeight: '70vh', overflow: 'auto', padding: '16px', background: '#f5f5f5', borderRadius: 8, whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontSize: 13, lineHeight: 1.6 }}>
            {textContent}
          </pre>
        );

      case 'code': {
        if (!textContent) return <Empty description={t('preview.noContent')} />;
        const lang = getCodeLanguage(file?.name || '');
        const prismTheme = appTheme === 'dark' ? themes.vsDark : themes.github;
        return (
          <Highlight theme={prismTheme} code={textContent} language={lang}>
            {({ className, style, tokens, getLineProps, getTokenProps }) => (
              <pre
                className={className}
                style={{ ...style, maxHeight: '70vh', overflow: 'auto', padding: '16px', margin: 0, fontSize: 13, lineHeight: 1.6, fontFamily: "'SF Mono', Consolas, Monaco, monospace", borderRadius: 8 }}
              >
                {tokens.map((line, i) => (
                  <div key={i} {...getLineProps({ line })}>
                    <span style={{ display: 'inline-block', width: '3em', textAlign: 'right', paddingRight: '1em', marginRight: '0.5em', userSelect: 'none', opacity: 0.4 }}>
                      {i + 1}
                    </span>
                    {line.map((token, key) => (
                      <span key={key} {...getTokenProps({ token })} />
                    ))}
                  </div>
                ))}
              </pre>
            )}
          </Highlight>
        );
      }

      case 'docx':
        return <div ref={docxContainerRef} style={{ maxHeight: '70vh', overflow: 'auto' }} />;

      case 'xlsx':
        if (xlsxSheets.length === 0) return <Empty description={t('preview.noContent')} />;
        if (xlsxSheets.length === 1) {
          return (
            <div
              className="xlsx-preview"
              style={{ maxHeight: '70vh', overflow: 'auto' }}
              dangerouslySetInnerHTML={{ __html: xlsxSheets[0].html }}
            />
          );
        }
        return (
          <Tabs
            items={xlsxSheets.map((s, i) => ({
              key: String(i),
              label: s.name,
              children: (
                <div
                  className="xlsx-preview"
                  style={{ maxHeight: '63vh', overflow: 'auto' }}
                  dangerouslySetInnerHTML={{ __html: s.html }}
                />
              ),
            }))}
          />
        );

      case 'pptx':
      case 'office-online':
        if (!previewUrl) return <Empty description={t('preview.noContent')} />;
        return (
          <div>
            <iframe
              src={`https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(previewUrl)}`}
              style={{ width: '100%', height: '70vh', border: 'none' }}
              title="Office Preview"
            />
            <div style={{ textAlign: 'center', color: '#999', marginTop: 8, fontSize: 12 }}>
              {t('preview.officeViewerHint')}
            </div>
          </div>
        );

      case 'video':
        if (!previewUrl) return <Empty description={t('preview.noContent')} />;
        return (
          <div className="flex justify-center">
            <video src={previewUrl} controls style={{ maxHeight: '70vh', maxWidth: '100%' }} />
          </div>
        );

      case 'audio':
        if (!previewUrl) return <Empty description={t('preview.noContent')} />;
        return (
          <div className="flex justify-center" style={{ padding: '80px 0' }}>
            <audio src={previewUrl} controls style={{ width: '100%', maxWidth: 500 }} />
          </div>
        );

      default:
        return (
          <Empty description={t('preview.unsupported')}>
            <Button icon={<DownloadOutlined />} onClick={() => file && onDownload(file.id, false)}>
              {t('common.download')}
            </Button>
          </Empty>
        );
    }
  };

  const isLargeModal = ['image', 'pdf', 'docx', 'xlsx', 'pptx', 'office-online', 'video'].includes(previewType);

  return (
    <Modal
      open={open}
      title={file?.name || t('preview.title')}
      onCancel={onClose}
      footer={
        file && previewType !== 'unsupported' ? (
          <div className="flex justify-between">
            <Button icon={<DownloadOutlined />} onClick={() => onDownload(file.id, false)}>
              {t('common.download')}
            </Button>
            <Button onClick={onClose}>{t('common.cancel')}</Button>
          </div>
        ) : null
      }
      width={isLargeModal ? 1000 : 480}
      destroyOnClose
    >
      {renderPreview()}
    </Modal>
  );
}
