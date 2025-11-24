/**
 * 支持的日期格式选项
 */
export const DATE_FORMAT_OPTIONS = [
  { value: 'yyyy-MM-dd', label: 'YYYY-MM-DD (2025-01-31)' },
  { value: 'MM-dd-yyyy', label: 'MM-DD-YYYY (01-31-2025)' },
  { value: 'dd-MM-yyyy', label: 'DD-MM-YYYY (31-01-2025)' },
  { value: 'yyyy/MM/dd', label: 'YYYY/MM/DD (2025/01/31)' },
  { value: 'MM/dd/yyyy', label: 'MM/DD/YYYY (01/31/2025)' },
  { value: 'dd/MM/yyyy', label: 'DD/MM/YYYY (31/01/2025)' },
  { value: 'yyyy.MM.dd', label: 'YYYY.MM.DD (2025.01.31)' },
  { value: 'dd.MM.yyyy', label: 'DD.MM.YYYY (31.01.2025)' },
  { value: 'MMM dd, yyyy', label: 'MMM DD, YYYY (Jan 31, 2025)' },
  { value: 'dd MMM yyyy', label: 'DD MMM YYYY (31 Jan 2025)' },
];

export const DEFAULT_DATE_FORMAT = 'yyyy-MM-dd';

