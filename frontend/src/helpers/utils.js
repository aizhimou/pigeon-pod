import { notifications } from '@mantine/notifications';
import { toastConstants } from '../constants/toast.constants.js';
import '@mantine/notifications/styles.css';
import i18next from 'i18next';

export function showError(error) {
  if (error.message) {
    if (error.name === 'AxiosError') {
      switch (error.response.status) {
        case 401: {
          const searchParams = new URLSearchParams(window.location.search);
          const hasExpired = searchParams.get('expired') === 'true';
          if (!hasExpired) {
            window.location.href = '/login?expired=true';
            localStorage.removeItem('user');
          }
          break;
        }
        case 429:
          notifications.show({
            color: toastConstants.ERROR_COLOR,
            title: i18next.t('error'),
            message: 'Too many requests, please try again later.',
            position: 'top-right',
            autoClose: toastConstants.ERROR_TIMEOUT,
          });
          break;
        case 500:
          notifications.show({
            color: toastConstants.ERROR_COLOR,
            title: i18next.t('error'),
            message: 'Internal server error, please contact administrator.',
            position: 'top-right',
            autoClose: toastConstants.ERROR_TIMEOUT,
          });
          break;
        default:
          notifications.show({
            color: toastConstants.ERROR_COLOR,
            title: i18next.t('error'),
            message: error.message,
            position: 'top-right',
            autoClose: toastConstants.ERROR_TIMEOUT,
          });
      }
      return;
    }
    notifications.show({
      color: toastConstants.ERROR_COLOR,
      title: i18next.t('error'),
      message: error.message,
      position: 'top-right',
      autoClose: toastConstants.ERROR_TIMEOUT,
    });
  } else {
    notifications.show({
      color: toastConstants.ERROR_COLOR,
      title: i18next.t('error'),
      message: error,
      position: 'top-right',
      autoClose: toastConstants.ERROR_TIMEOUT,
    });
  }
}

export function showWarning(message) {
  notifications.show({
    color: toastConstants.WARNING_COLOR,
    title: i18next.t('warning'),
    message: message,
    position: 'bottom-right',
    autoClose: toastConstants.WARNING_TIMEOUT,
  });
}

export function showSuccess(message) {
  notifications.show({
    color: toastConstants.SUCCESS_COLOR,
    title: i18next.t('success'),
    message: message,
    position: 'bottom-right',
    autoClose: toastConstants.SUCCESS_TIMEOUT,
  });
}

export function showInfo(message) {
  notifications.show({
    color: toastConstants.INFO_COLOR,
    title: i18next.t('info'),
    message: message,
    position: 'bottom-right',
    autoClose: toastConstants.INFO_TIMEOUT,
  });
}

export function showNotice(message) {
  notifications.show({
    color: toastConstants.NOTICE_COLOR,
    title: i18next.t('notice'),
    message: message,
    position: 'bottom-right',
    autoClose: toastConstants.NOTICE_TIMEOUT,
  });
}

/**
 * 解析 ISO 8601 Duration (例如 PT1H11M52S)
 * 秒数四舍五入到分钟，不直接显示秒
 */
export function formatISODuration(isoDuration) {
  if (!isoDuration) {
    return '';
  }
  const regex = /P(?:([0-9]+)D)?T?(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9]+)S)?/;
  const matches = isoDuration.match(regex);

  if (!matches) return isoDuration;

  const days = parseInt(matches[1] || 0, 10);
  const hours = parseInt(matches[2] || 0, 10);
  let minutes = parseInt(matches[3] || 0, 10);
  const seconds = parseInt(matches[4] || 0, 10);

  // 四舍五入秒 → 分钟
  if (seconds >= 30) {
    minutes += 1;
  }

  // 转换天数为小时
  let totalHours = days * 24 + hours;

  let parts = [];
  if (totalHours > 0) {
    // 小时模式
    if (minutes >= 60) {
      totalHours += Math.floor(minutes / 60);
      minutes = minutes % 60;
    }
    parts.push(`${totalHours} ${i18next.t('hour')}`);
    if (minutes > 0) parts.push(`${minutes} ${i18next.t('minute')}`);
  } else {
    // 分钟模式（没有小时）
    const totalMinutes = days * 24 * 60 + minutes;
    parts.push(`${totalMinutes} ${i18next.t('minute')}`);
  }

  return parts.join(' ');
}

/**
 * 格式化 ISO 日期时间
 * 2025-08-25T01:00:28 -> 2025-08-25 01:00:28
 */
export function formatISODateTime(isoDateTime) {
  const date = new Date(isoDateTime);
  const pad = (n) => n.toString().padStart(2, '0');

  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  // const hour = pad(date.getHours());
  // const minute = pad(date.getMinutes());
  // const second = pad(date.getSeconds());

  // return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
  return `${year}-${month}-${day}`;
}

/**
 * 根据指定格式格式化日期
 * @param {string|Date} isoDateTime - ISO 日期时间字符串或 Date 对象
 * @param {string} format - 日期格式 (例如: 'yyyy-MM-dd', 'MM/dd/yyyy', 'dd.MM.yyyy')
 * @returns {string} 格式化后的日期字符串
 */
export function formatDateWithPattern(isoDateTime, format = 'yyyy-MM-dd') {
  if (!isoDateTime) return '';

  const date = new Date(isoDateTime);
  if (isNaN(date.getTime())) return '';

  const pad = (n) => n.toString().padStart(2, '0');

  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  const monthIndex = date.getMonth();

  // 月份名称映射
  const monthNames = {
    short: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
    full: [
      'January',
      'February',
      'March',
      'April',
      'May',
      'June',
      'July',
      'August',
      'September',
      'October',
      'November',
      'December',
    ],
  };

  // 替换格式字符串 - 注意顺序很重要！
  // 先处理长格式，再处理短格式，避免误替换
  let result = format;

  // 1. 先处理 MMMM（完整月份名）和 MMM（简短月份名）
  result = result.replace('MMMM', monthNames.full[monthIndex]);
  result = result.replace('MMM', monthNames.short[monthIndex]);

  // 2. 再处理 yyyy（年份）
  result = result.replace('yyyy', year);

  // 3. 最后处理 MM（数字月份）和 dd（日期）
  result = result.replace('MM', month);
  result = result.replace('dd', day);

  return result;
}

export function openPage(url) {
  window.open(url);
}

export function removeTrailingSlash(url) {
  if (url.endsWith('/')) {
    return url.slice(0, -1);
  } else {
    return url;
  }
}

/**
 * 检测浏览器是否支持现代复制API
 */
export function isClipboardSupported() {
  return !!(navigator.clipboard && window.isSecureContext);
}

/**
 * 自定义复制功能
 * 支持现代浏览器的自动复制和Safari等浏览器的手动复制提示
 * @param {string} text - 要复制的文本
 * @param {function} onSuccess - 复制成功回调
 * @param {function} onFallback - 需要手动复制时的回调（显示Modal等）
 * @returns {Promise<boolean>} - 是否成功复制或需要手动复制
 */
export async function copyToClipboard(text, onSuccess, onFallback) {
  try {
    // 检查是否支持现代复制API
    if (isClipboardSupported()) {
      await navigator.clipboard.writeText(text);
      if (onSuccess) onSuccess();
      return true;
    } else {
      // 不支持现代复制API，直接触发手动复制流程
      // console.warn('浏览器不支持现代复制API，需要手动复制');
      if (onFallback) onFallback(text);
      return false;
    }
  } catch {
    // console.warn('自动复制失败，需要手动复制:', error);
    // 自动复制失败，触发手动复制流程
    if (onFallback) onFallback(text);
    return false;
  }
}
