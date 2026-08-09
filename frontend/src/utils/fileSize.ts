export function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
  return (bytes / 1073741824).toFixed(1) + ' GB';
}

export function getFolderSizeHintClass(totalSize: number): 'none' | 'small' | 'large' {
  const fiftyMb = 50 * 1024 * 1024;
  const fiveHundredMb = 500 * 1024 * 1024;
  if (totalSize > fiveHundredMb) return 'large';
  if (totalSize > fiftyMb) return 'small';
  return 'none';
}
