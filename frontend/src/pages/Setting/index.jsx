import React, { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { API, showError, showSuccess } from '../../helpers/index.js';
import {
  Button,
  Container,
  Paper,
  Group,
  PasswordInput,
  Stack,
  Switch,
  TextInput,
  Title,
  Text,
  Modal,
  FileInput,
  Divider,
  ActionIcon,
  Alert,
  Anchor,
  Select,
  MultiSelect,
  Textarea,
  List,
  NumberInput,
  Radio,
  Checkbox,
  Collapse,
  ScrollArea,
  SegmentedControl,
  Pill,
} from '@mantine/core';
import { UserContext } from '../../context/User/UserContext.jsx';
import { hasLength, useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import {
  IconCookie,
  IconEdit,
  IconLock,
  IconLockPassword,
  IconRefresh,
  IconEye,
  IconEyeOff,
  IconCalendar,
  IconChevronDown,
  IconChevronUp,
  IconCloudUp,
  IconDownload,
} from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { DATE_FORMAT_OPTIONS, DEFAULT_DATE_FORMAT } from '../../constants/dateFormats.js';
import {
  SUBTITLE_LANGUAGE_OPTIONS,
  SUBTITLE_FORMAT_OPTIONS,
} from '../../constants/subtitleLanguages.js';

const NEGATIVE_NUMBER_PATTERN = /^-\d+(\.\d+)?$/;

const quoteTokenIfNeeded = (token) => {
  if (!/\s/.test(token)) {
    return token;
  }
  return `"${token.replace(/(["\\])/g, '\\$1')}"`;
};

const tokenizeYtDlpLine = (line) => {
  if (!line) return [];
  const trimmed = line.trim();
  if (!trimmed) return [];

  // 支持简单 shell 风格引号：--arg "value with spaces"
  const pattern = /"([^"\\]*(?:\\.[^"\\]*)*)"|'([^'\\]*(?:\\.[^'\\]*)*)'|(\S+)/g;
  const tokens = [];
  let match;

  while ((match = pattern.exec(trimmed)) !== null) {
    if (match[1] != null) {
      tokens.push(match[1].replace(/\\(["\\])/g, '$1'));
    } else if (match[2] != null) {
      tokens.push(match[2].replace(/\\(['\\])/g, '$1'));
    } else if (match[3] != null) {
      tokens.push(match[3]);
    }
  }

  if (tokens.length === 0) {
    return trimmed.split(/\s+/).filter(Boolean);
  }
  return tokens;
};

const formatYtDlpTokens = (tokens) => {
  if (!tokens || tokens.length === 0) {
    return '';
  }

  const lines = [];
  let currentLine = [];

  tokens.forEach((rawToken) => {
    const token = (rawToken || '').trim();
    if (!token) return;

    const isOption = token.startsWith('-') && !NEGATIVE_NUMBER_PATTERN.test(token);
    if (isOption) {
      if (currentLine.length > 0) {
        lines.push(currentLine.join(' '));
      }
      currentLine = [token];
      return;
    }

    if (currentLine.length === 0) {
      currentLine = [quoteTokenIfNeeded(token)];
    } else {
      currentLine.push(quoteTokenIfNeeded(token));
    }
  });

  if (currentLine.length > 0) {
    lines.push(currentLine.join(' '));
  }

  return lines.join('\n');
};

const formatYtDlpArgsText = (value) => {
  if (!value) return '';
  if (Array.isArray(value)) {
    return formatYtDlpTokens(value);
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) return '';
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        return formatYtDlpTokens(parsed);
      }
    } catch {
      // fallback to keep legacy plain-string values readable
    }
    return formatYtDlpTokens(
      trimmed
        .split('\n')
        .flatMap((line) => tokenizeYtDlpLine(line)),
    );
  }
  return '';
};

const parseYtDlpArgsText = (text) => {
  if (!text) return [];
  return text
    .split('\n')
    .flatMap((line) => tokenizeYtDlpLine(line))
    .filter(Boolean);
};

const parseContentDispositionFilename = (contentDisposition) => {
  if (!contentDisposition) {
    return '';
  }
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match && utf8Match[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch {
      return utf8Match[1];
    }
  }
  const simpleMatch = contentDisposition.match(/filename="?([^";]+)"?/i);
  return simpleMatch && simpleMatch[1] ? simpleMatch[1] : '';
};

const createDefaultFeedDefaults = () => ({
  autoDownloadLimit: 3,
  autoDownloadDelayMinutes: 0,
  maximumEpisodes: null,
  audioQuality: null,
  downloadType: 'AUDIO',
  videoQuality: '',
  videoEncoding: '',
  subtitleLanguages: 'zh,en',
  subtitleFormat: 'vtt',
});

const createDefaultSystemConfig = () => ({
  baseUrl: '',
  youtubeApiKey: '',
  cookiesContent: '',
  ytDlpArgs: '',
  loginCaptchaEnabled: false,
  youtubeDailyLimitUnits: null,
  hasCookie: false,
  storageType: 'LOCAL',
  storageTempDir: '/tmp/pigeon-pod',
  localAudioPath: '/data/audio/',
  localVideoPath: '/data/video/',
  localCoverPath: '/data/cover/',
  s3Endpoint: '',
  s3Region: 'us-east-1',
  s3Bucket: '',
  s3AccessKey: '',
  s3SecretKey: '',
  hasS3SecretKey: false,
  s3PathStyleAccess: true,
  s3ConnectTimeoutSeconds: 30,
  s3SocketTimeoutSeconds: 1800,
  s3ReadTimeoutSeconds: 1800,
  s3PresignExpireHours: 72,
});

const toNullableNumber = (value) => {
  if (value === '' || value == null) {
    return null;
  }
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
};

const isLocalDiskPath = (rawPath) => {
  const value = (rawPath || '').trim();
  if (!value || value.includes('://')) {
    return false;
  }
  if (value.startsWith('/')) {
    return true;
  }
  return /^[A-Za-z]:[\\/]/.test(value);
};

const UserSetting = () => {
  const { t } = useTranslation();
  const [state, dispatch] = useContext(UserContext);
  const [resetPasswordLoading, setResetPasswordLoading] = useState(false);
  const [resetPasswordOpened, { open: openResetPassword, close: closeResetPassword }] =
    useDisclosure(false);
  const [
    confirmGenerateApiKeyOpened,
    { open: openConfirmGenerateApiKey, close: closeConfirmGenerateApiKey },
  ] = useDisclosure(false);
  const [changeUsernameOpened, { open: openChangeUsername, close: closeChangeUsername }] =
    useDisclosure(false);

  // API Key visibility states
  const [showApiKey, setShowApiKey] = useState(false);
  const [showYoutubeApiKey, setShowYoutubeApiKey] = useState(false);

  // YouTube Data API Key states
  const [editYoutubeApiKeyOpened, { open: openEditYoutubeApiKey, close: closeEditYoutubeApiKey }] =
    useDisclosure(false);
  const [youtubeApiKey, setYoutubeApiKey] = useState('');
  const [youtubeDailyLimitUnits, setYoutubeDailyLimitUnits] = useState('');
  const [youtubeQuotaToday, setYoutubeQuotaToday] = useState(null);

  // Cookie upload states
  const [uploadCookiesOpened, { open: openUploadCookies, close: closeUploadCookies }] =
    useDisclosure(false);
  const [cookieFile, setCookieFile] = useState(null);
  const [cookieUploading, setCookieUploading] = useState(false);
  // const [hasCookie, setHasCookie] = useState(false);

  // Date format states
  const [editDateFormatOpened, { open: openEditDateFormat, close: closeEditDateFormat }] =
    useDisclosure(false);
  const [dateFormat, setDateFormat] = useState(state.user?.dateFormat || DEFAULT_DATE_FORMAT);
  const [editBaseUrlOpened, { open: openEditBaseUrl, close: closeEditBaseUrl }] =
    useDisclosure(false);
  const [editStorageConfigOpened, { open: openEditStorageConfig, close: closeEditStorageConfig }] =
    useDisclosure(false);
  const [
    confirmStorageSwitchOpened,
    { open: openConfirmStorageSwitch, close: closeConfirmStorageSwitch },
  ] = useDisclosure(false);
  const [pendingStorageType, setPendingStorageType] = useState(null);

  const [editYtDlpArgsOpened, { open: openEditYtDlpArgs, close: closeEditYtDlpArgs }] =
    useDisclosure(false);
  const [
    editFeedDefaultsOpened,
    { open: openEditFeedDefaults, close: closeEditFeedDefaults },
  ] = useDisclosure(false);
  const [
    applyFeedDefaultsOpened,
    { open: openApplyFeedDefaults, close: closeApplyFeedDefaults },
  ] = useDisclosure(false);
  const [ytDlpArgsText, setYtDlpArgsText] = useState('');
  const [feedDefaults, setFeedDefaults] = useState(createDefaultFeedDefaults);
  const [applyFeedDefaultsMode, setApplyFeedDefaultsMode] = useState('override_all');
  const [applyingFeedDefaults, setApplyingFeedDefaults] = useState(false);
  const [editYtDlpRuntimeOpened, { open: openEditYtDlpRuntime, close: closeEditYtDlpRuntime }] =
    useDisclosure(false);
  const [
    confirmUpdateYtDlpOpened,
    { open: openConfirmUpdateYtDlp, close: closeConfirmUpdateYtDlp },
  ] = useDisclosure(false);
  const [ytDlpRuntime, setYtDlpRuntime] = useState(null);
  const [ytDlpChannel, setYtDlpChannel] = useState('stable');
  const [ytDlpUpdating, setYtDlpUpdating] = useState(false);
  const [ytDlpUpdateSubmitting, setYtDlpUpdateSubmitting] = useState(false);
  const ytDlpStatusRef = useRef(null);
  const [blockedYtDlpArgs, setBlockedYtDlpArgs] = useState([]);
  const [loginCaptchaEnabled, setLoginCaptchaEnabled] = useState(false);
  const [loginCaptchaSaving, setLoginCaptchaSaving] = useState(false);
  const [exportOpmlOpened, { open: openExportOpml, close: closeExportOpml }] = useDisclosure(false);
  const [exportFeedsLoading, setExportFeedsLoading] = useState(false);
  const [exportingOpml, setExportingOpml] = useState(false);
  const [exportFeedList, setExportFeedList] = useState([]);
  const [selectedExportFeedKeys, setSelectedExportFeedKeys] = useState([]);
  const [exportFeedTypeFilter, setExportFeedTypeFilter] = useState('all');
  const [systemConfig, setSystemConfig] = useState(createDefaultSystemConfig);
  const [systemConfigSaving, setSystemConfigSaving] = useState(false);
  const [systemConfigTesting, setSystemConfigTesting] = useState(false);
  const [storageSwitchChecking, setStorageSwitchChecking] = useState(false);
  const [storageAdvancedOpened, setStorageAdvancedOpened] = useState(false);

  const handleOpenEditStorageConfig = () => {
    setStorageAdvancedOpened(false);
    openEditStorageConfig();
  };

  const handleCloseEditStorageConfig = () => {
    setStorageAdvancedOpened(false);
    closeEditStorageConfig();
  };

  const fetchYoutubeQuotaToday = useCallback(async () => {
    try {
      const res = await API.get('/api/account/youtube-quota/today');
      const { code, data } = res.data;
      if (code === 200) {
        setYoutubeQuotaToday(data);
      }
    } catch (error) {
      console.error('Failed to fetch YouTube quota:', error);
    }
  }, []);

  useEffect(() => {
    setYtDlpArgsText(formatYtDlpArgsText(systemConfig.ytDlpArgs));
  }, [systemConfig.ytDlpArgs]);

  useEffect(() => {
    setYoutubeApiKey(systemConfig.youtubeApiKey || '');
    setYoutubeDailyLimitUnits(systemConfig.youtubeDailyLimitUnits ?? '');
  }, [systemConfig.youtubeApiKey, systemConfig.youtubeDailyLimitUnits]);

  useEffect(() => {
    if (!state.user) return;
    const fetchFeedDefaults = async () => {
      const res = await API.get('/api/account/feed-defaults');
      const { code, msg, data } = res.data;
      if (code !== 200) {
        showError(msg);
        return;
      }

      setFeedDefaults({
        autoDownloadLimit: data?.autoDownloadLimit ?? 3,
        autoDownloadDelayMinutes: data?.autoDownloadDelayMinutes ?? 0,
        maximumEpisodes: data?.maximumEpisodes ?? null,
        audioQuality: data?.audioQuality ?? null,
        downloadType: data?.downloadType || 'AUDIO',
        videoQuality: data?.videoQuality || '',
        videoEncoding: data?.videoEncoding || '',
        subtitleLanguages: data?.subtitleLanguages ?? null,
        subtitleFormat: data?.subtitleFormat ?? null,
      });
    };

    fetchFeedDefaults().catch(() => {});
  }, [state.user]);

  useEffect(() => {
    setLoginCaptchaEnabled(Boolean(systemConfig.loginCaptchaEnabled));
  }, [systemConfig.loginCaptchaEnabled]);

  useEffect(() => {
    if (!state.user || !editYoutubeApiKeyOpened) return;
    fetchYoutubeQuotaToday().then();
    const interval = setInterval(() => {
      fetchYoutubeQuotaToday().then();
    }, 30000);
    return () => clearInterval(interval);
  }, [state.user, editYoutubeApiKeyOpened, fetchYoutubeQuotaToday]);

  useEffect(() => {
    const fetchBlockedArgs = async () => {
      const res = await API.get('/api/account/yt-dlp-args-policy');
      const { code, data } = res.data;
      if (code === 200 && Array.isArray(data)) {
        setBlockedYtDlpArgs(data);
      }
    };
    fetchBlockedArgs().catch(() => {});
  }, []);

  const fetchSystemConfig = useCallback(async () => {
    try {
      const res = await API.get('/api/account/system-config');
      const { code, msg, data } = res.data;
      if (code !== 200) {
        showError(msg);
        return;
      }
      setSystemConfig({
        ...createDefaultSystemConfig(),
        ...(data || {}),
        s3SecretKey: '',
        hasS3SecretKey: Boolean(data?.hasS3SecretKey),
      });
    } catch (error) {
      console.error('Failed to fetch system config:', error);
    }
  }, []);

  useEffect(() => {
    if (!state.user) return;
    fetchSystemConfig().catch(() => {});
  }, [fetchSystemConfig, state.user]);

  const fetchYtDlpRuntime = useCallback(async () => {
    try {
      const res = await API.get('/api/account/yt-dlp/runtime');
      const { code, msg, data } = res.data;
      if (code !== 200) {
        showError(msg);
        return;
      }

      setYtDlpRuntime(data);
      if (data?.channel) {
        setYtDlpChannel(data.channel);
      }

      const stateValue = data?.status?.state || 'IDLE';
      ytDlpStatusRef.current = stateValue;
      setYtDlpUpdating(Boolean(data?.updating) || stateValue === 'RUNNING');
      // eslint-disable-next-line no-unused-vars
    } catch (error) {
      showError(
        t('yt_dlp_runtime_fetch_failed', {
          defaultValue: 'Failed to load yt-dlp runtime status.',
        }),
      );
    }
  }, [t]);

  const fetchYtDlpUpdateStatus = useCallback(async () => {
    try {
      const res = await API.get('/api/account/yt-dlp/update-status');
      const { code, msg, data } = res.data;
      if (code !== 200) {
        showError(msg);
        return;
      }

      const nextState = data?.state || 'IDLE';
      const previousState = ytDlpStatusRef.current;
      ytDlpStatusRef.current = nextState;

      setYtDlpRuntime((prev) => ({
        ...(prev || {}),
        status: data,
        updating: nextState === 'RUNNING',
      }));
      setYtDlpUpdating(nextState === 'RUNNING');

      if (previousState === 'RUNNING' && nextState === 'SUCCESS') {
        showSuccess(
          t('yt_dlp_update_success', {
            defaultValue: 'yt-dlp updated successfully.',
          }),
        );
        fetchYtDlpRuntime().catch(() => {});
      } else if (previousState === 'RUNNING' && nextState === 'FAILED') {
        const errorMessage =
          data?.error ||
          t('yt_dlp_update_failed', {
            defaultValue: 'yt-dlp update failed.',
          });
        showError(errorMessage);
        fetchYtDlpRuntime().catch(() => {});
      }
      // eslint-disable-next-line no-unused-vars
    } catch (error) {
      showError(
        t('yt_dlp_update_status_failed', {
          defaultValue: 'Failed to refresh yt-dlp update status.',
        }),
      );
    }
  }, [fetchYtDlpRuntime, t]);

  useEffect(() => {
    if (!state.user) return;
    fetchYtDlpRuntime().catch(() => {});
  }, [fetchYtDlpRuntime, state.user]);

  useEffect(() => {
    if (!ytDlpUpdating) return undefined;
    const timer = setInterval(() => {
      fetchYtDlpUpdateStatus().catch(() => {});
    }, 3000);
    return () => clearInterval(timer);
  }, [fetchYtDlpUpdateStatus, ytDlpUpdating]);

  const updateYtDlpVersion = async () => {
    try {
      setYtDlpUpdateSubmitting(true);
      const res = await API.post('/api/account/yt-dlp/update', {
        channel: ytDlpChannel,
      });
      const { code, msg, data } = res.data;
      if (code === 200) {
        setYtDlpRuntime((prev) => ({
          ...(prev || {}),
          channel: ytDlpChannel,
          status: data,
          updating: true,
        }));
        ytDlpStatusRef.current = 'RUNNING';
        setYtDlpUpdating(true);
        closeConfirmUpdateYtDlp();
        showSuccess(
          t('yt_dlp_update_started', {
            defaultValue: 'yt-dlp update started.',
          }),
        );
      } else {
        showError(msg);
      }
      // eslint-disable-next-line no-unused-vars
    } catch (error) {
      showError(
        t('yt_dlp_update_submit_failed', {
          defaultValue: 'Failed to submit yt-dlp update task.',
        }),
      );
    } finally {
      setYtDlpUpdateSubmitting(false);
    }
  };

  const getYtDlpStatusText = (statusValue) => {
    if (statusValue === 'RUNNING') {
      return t('yt_dlp_update_running', { defaultValue: 'Updating' });
    }
    if (statusValue === 'SUCCESS') {
      return t('yt_dlp_update_state_success', { defaultValue: 'Success' });
    }
    if (statusValue === 'FAILED') {
      return t('yt_dlp_update_state_failed', { defaultValue: 'Failed' });
    }
    return t('yt_dlp_update_state_idle', { defaultValue: 'Idle' });
  };

  const getExportFeedKey = (feed) => `${String(feed?.type || '').toUpperCase()}:${feed?.id || ''}`;
  const normalizeExportFeedType = (feed) => String(feed?.type || '').toLowerCase();
  const filteredExportFeedList =
    exportFeedTypeFilter === 'all'
      ? exportFeedList
      : exportFeedList.filter((feed) => normalizeExportFeedType(feed) === exportFeedTypeFilter);
  const selectedExportFeedKeySet = new Set(selectedExportFeedKeys);
  const selectedVisibleExportFeedCount = filteredExportFeedList.filter((feed) =>
    selectedExportFeedKeySet.has(getExportFeedKey(feed)),
  ).length;
  const hasUploadedCookies = Boolean(systemConfig.hasCookie);

  const loadExportFeedList = async () => {
    setExportFeedsLoading(true);
    try {
      const res = await API.get('/api/feed/list');
      const { code, msg, data } = res.data;
      if (code !== 200) {
        showError(msg);
        return;
      }
      const list = Array.isArray(data) ? data : [];
      setExportFeedList(list);
      setSelectedExportFeedKeys(list.map((feed) => getExportFeedKey(feed)));
    } finally {
      setExportFeedsLoading(false);
    }
  };

  const openExportOpmlModal = async () => {
    openExportOpml();
    setExportFeedTypeFilter('all');
    await loadExportFeedList();
  };

  const selectAllExportFeeds = () => {
    setSelectedExportFeedKeys((previous) => {
      const next = new Set(previous);
      filteredExportFeedList.forEach((feed) => {
        next.add(getExportFeedKey(feed));
      });
      return Array.from(next);
    });
  };

  const clearExportFeedSelection = () => {
    setSelectedExportFeedKeys([]);
  };

  const exportSelectedFeedsAsOpml = async () => {
    if (selectedExportFeedKeys.length === 0) {
      showError(t('export_subscriptions_no_selection'));
      return;
    }

    const selectedSet = new Set(selectedExportFeedKeys);
    const selectedFeeds = exportFeedList
      .filter((feed) => selectedSet.has(getExportFeedKey(feed)))
      .map((feed) => ({
        id: feed.id,
        type: feed.type,
      }));

    if (selectedFeeds.length === 0) {
      showError(t('export_subscriptions_no_selection'));
      return;
    }

    setExportingOpml(true);
    try {
      const res = await API.post(
        '/api/account/export-opml',
        { feeds: selectedFeeds },
        { responseType: 'blob' },
      );

      const contentType = String(res.headers?.['content-type'] || '').toLowerCase();
      if (contentType.includes('application/json')) {
        const text = await res.data.text();
        let message = t('export_subscriptions_failed');
        try {
          const parsed = JSON.parse(text);
          message = parsed?.msg || message;
        } catch {
          // keep fallback message
        }
        showError(message);
        return;
      }

      const filenameFromHeader = parseContentDispositionFilename(res.headers?.['content-disposition']);
      const fallbackFilename =
        `pigeonpod-subscriptions-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.opml`;
      const filename = filenameFromHeader || fallbackFilename;

      const blob = new Blob([res.data], { type: 'text/x-opml;charset=utf-8' });
      const downloadUrl = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = downloadUrl;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(downloadUrl);

      showSuccess(t('export_subscriptions_success'));
      closeExportOpml();
    } finally {
      setExportingOpml(false);
    }
  };

  const resetPassword = async (values) => {
    setResetPasswordLoading(true);
    const res = await API.post('/api/account/reset-password', {
      ...state.user,
      password: values.oldPassword,
      newPassword: values.newPassword,
    });
    const { code, msg } = res.data;
    if (code === 200) {
      showSuccess(t('password_reset_success'));
      closeResetPassword();
    } else {
      showError(msg);
    }
    setResetPasswordLoading(false);
  };

  const generateApiKey = async () => {
    const res = await API.get('/api/account/generate-api-key');
    const { code, msg, data } = res.data;
    if (code === 200) {
      showSuccess(t('api_key_generated'));
      // update the apiKey in the context
      const user = {
        ...state.user,
        apiKey: data,
      };
      dispatch({
        type: 'login',
        payload: user,
      });
      localStorage.setItem('user', JSON.stringify(user));
    } else {
      showError(msg);
    }
  };

  const changeUsername = async (values) => {
    const res = await API.post('/api/account/change-username', {
      id: state.user.id,
      username: values.username,
    });
    const { code, msg, data } = res.data;
    if (code === 200) {
      showSuccess(t('username_changed_success'));
      dispatch({ type: 'login', payload: data });
      localStorage.setItem('user', JSON.stringify(data));
      closeChangeUsername();
      changeUsernameForm.reset();
    } else {
      showError(msg);
    }
  };

  // YouTube API Key functions
  const saveYoutubeApiKey = async () => {
    const normalizedDailyLimit =
      youtubeDailyLimitUnits === '' || youtubeDailyLimitUnits == null
        ? null
        : Number(youtubeDailyLimitUnits);

    const res = await API.post('/api/account/update-youtube-api-key', {
      id: state.user.id,
      youtubeApiKey: youtubeApiKey,
      youtubeDailyLimitUnits: normalizedDailyLimit,
    });
    const { code, msg, data } = res.data;
    if (code === 200) {
      showSuccess(t('youtube_api_key_saved'));
      setSystemConfig({
        ...createDefaultSystemConfig(),
        ...(data || {}),
        s3SecretKey: '',
        hasS3SecretKey: Boolean(data?.hasS3SecretKey),
      });
      fetchYoutubeQuotaToday().then();
      closeEditYoutubeApiKey();
    } else {
      showError(msg);
    }
  };

  const uploadCookies = async () => {
    if (!cookieFile) return;

    setCookieUploading(true);

    // Read file text content
    const fileContent = await cookieFile.text();

    // Send text content to API
    const res = await API.post('/api/account/cookies', {
      id: state.user.id,
      cookiesContent: fileContent,
    });

    const { code, msg } = res.data;

    if (code === 200) {
      showSuccess(t('cookies_upload_success'));
      fetchSystemConfig().then();
      closeUploadCookies();
      setCookieFile(null);
    } else {
      showError(msg);
    }

    setCookieUploading(false);
  };

  const updateLoginCaptcha = async (enabled) => {
    const previous = loginCaptchaEnabled;
    setLoginCaptchaEnabled(enabled);
    setLoginCaptchaSaving(true);
    const res = await API.post('/api/account/update-login-captcha', {
      enabled,
    });
    const { code, msg, data } = res.data;
    if (code === 200) {
      showSuccess(t('login_captcha_updated'));
      setLoginCaptchaEnabled(Boolean(data));
      setSystemConfig((prev) => ({
        ...prev,
        loginCaptchaEnabled: Boolean(data),
      }));
    } else {
      showError(msg);
      setLoginCaptchaEnabled(previous);
    }
    setLoginCaptchaSaving(false);
  };

  const deleteCookie = async () => {
    const res = await API.delete('/api/account/cookies/' + state.user.id);
    const { code, msg } = res.data;
    if (code === 200) {
      showSuccess(t('cookie_deleted_successfully'));
      fetchSystemConfig().then();
      closeUploadCookies();
    } else {
      showError(msg);
    }
  };

  // Date format functions
  const saveDateFormat = async () => {
    const res = await API.post('/api/account/update-date-format', {
      id: state.user.id,
      dateFormat: dateFormat,
    });
    const { code, msg, data } = res.data;
    if (code === 200) {
      showSuccess(t('date_format_saved'));
      const user = {
        ...state.user,
        dateFormat: data,
      };
      dispatch({
        type: 'login',
        payload: user,
      });
      localStorage.setItem('user', JSON.stringify(user));
      closeEditDateFormat();
    } else {
      showError(msg);
    }
  };

  const saveFeedDefaults = async (showToast = true) => {
    const payload = {
      autoDownloadLimit: feedDefaults.autoDownloadLimit === '' ? null : feedDefaults.autoDownloadLimit,
      autoDownloadDelayMinutes:
        feedDefaults.autoDownloadDelayMinutes === '' ? null : feedDefaults.autoDownloadDelayMinutes,
      maximumEpisodes: feedDefaults.maximumEpisodes === '' ? null : feedDefaults.maximumEpisodes,
      audioQuality: feedDefaults.audioQuality === '' ? null : feedDefaults.audioQuality,
      downloadType: feedDefaults.downloadType || 'AUDIO',
      videoQuality: feedDefaults.videoQuality || null,
      videoEncoding: feedDefaults.videoEncoding || null,
      subtitleLanguages: feedDefaults.subtitleLanguages || null,
      subtitleFormat: feedDefaults.subtitleFormat || null,
    };

    const res = await API.post('/api/account/update-feed-defaults', payload);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      return false;
    }

    setFeedDefaults({
      autoDownloadLimit: data?.autoDownloadLimit ?? 3,
      autoDownloadDelayMinutes: data?.autoDownloadDelayMinutes ?? 0,
      maximumEpisodes: data?.maximumEpisodes ?? null,
      audioQuality: data?.audioQuality ?? null,
      downloadType: data?.downloadType || 'AUDIO',
      videoQuality: data?.videoQuality || '',
      videoEncoding: data?.videoEncoding || '',
      subtitleLanguages: data?.subtitleLanguages ?? null,
      subtitleFormat: data?.subtitleFormat ?? null,
    });

    if (showToast) {
      showSuccess(t('feed_defaults_saved', { defaultValue: 'Feed defaults updated' }));
    }
    return true;
  };

  const applyFeedDefaults = async () => {
    setApplyingFeedDefaults(true);
    try {
      const persisted = await saveFeedDefaults(false);
      if (!persisted) {
        return;
      }

      const res = await API.post('/api/account/apply-feed-defaults', {
        mode: applyFeedDefaultsMode,
      });
      const { code, msg, data } = res.data;
      if (code === 200) {
        showSuccess(
          t('feed_defaults_applied', {
            defaultValue: 'Applied to {{count}} feeds',
            count: data?.updatedFeeds ?? 0,
          }),
        );
        closeApplyFeedDefaults();
        closeEditFeedDefaults();
      } else {
        showError(msg);
      }
    } finally {
      setApplyingFeedDefaults(false);
    }
  };

  const saveYtDlpArgs = async () => {
    const res = await API.post('/api/account/update-yt-dlp-args', {
      id: state.user.id,
      ytDlpArgs: parseYtDlpArgsText(ytDlpArgsText),
    });
    const { code, msg, data } = res.data;
    if (code === 200) {
      showSuccess(t('yt_dlp_args_saved', { defaultValue: 'yt-dlp args saved' }));
      setSystemConfig((prev) => ({
        ...prev,
        ytDlpArgs: data,
      }));
      closeEditYtDlpArgs();
    } else {
      showError(msg);
    }
  };

  const buildSystemConfigPayload = () => ({
    ...systemConfig,
    storageType: systemConfig.storageType || 'LOCAL',
    baseUrl: systemConfig.baseUrl?.trim() || null,
    storageTempDir: systemConfig.storageTempDir?.trim() || null,
    localAudioPath: systemConfig.localAudioPath?.trim() || null,
    localVideoPath: systemConfig.localVideoPath?.trim() || null,
    localCoverPath: systemConfig.localCoverPath?.trim() || null,
    s3Endpoint: systemConfig.s3Endpoint?.trim() || null,
    s3Region: systemConfig.s3Region?.trim() || null,
    s3Bucket: systemConfig.s3Bucket?.trim() || null,
    s3AccessKey: systemConfig.s3AccessKey?.trim() || null,
    s3SecretKey: systemConfig.s3SecretKey ? systemConfig.s3SecretKey.trim() : null,
    hasS3SecretKey: Boolean(systemConfig.hasS3SecretKey),
    s3PathStyleAccess: Boolean(systemConfig.s3PathStyleAccess),
    s3ConnectTimeoutSeconds: toNullableNumber(systemConfig.s3ConnectTimeoutSeconds),
    s3SocketTimeoutSeconds: toNullableNumber(systemConfig.s3SocketTimeoutSeconds),
    s3ReadTimeoutSeconds: toNullableNumber(systemConfig.s3ReadTimeoutSeconds),
    s3PresignExpireHours: toNullableNumber(systemConfig.s3PresignExpireHours),
  });

  const saveSystemStorageConfig = async () => {
    const payload = buildSystemConfigPayload();
    if (payload.storageType === 'S3' && !isLocalDiskPath(payload.storageTempDir || '')) {
      showError(
        t('storage_temp_dir_local_disk_only', {
          defaultValue: 'Temp directory must be a local disk path, such as /tmp/pigeon-pod.',
        }),
      );
      return false;
    }
    setSystemConfigSaving(true);
    try {
      const res = await API.post('/api/account/system-config', payload);
      const { code, msg, data } = res.data;
      if (code !== 200) {
        showError(msg);
        return false;
      }
      showSuccess(
        t('storage_config_saved_apply_new_tasks', {
          defaultValue:
            'Storage configuration saved. New download tasks will use the updated storage strategy.',
        }),
      );
      setSystemConfig({
        ...createDefaultSystemConfig(),
        ...(data || {}),
        s3SecretKey: '',
        hasS3SecretKey: Boolean(data?.hasS3SecretKey),
      });
      return true;
    } finally {
      setSystemConfigSaving(false);
    }
  };

  const testSystemStorageConfig = async () => {
    const payload = buildSystemConfigPayload();
    if (payload.storageType === 'S3' && !isLocalDiskPath(payload.storageTempDir || '')) {
      showError(
        t('storage_temp_dir_local_disk_only', {
          defaultValue: 'Temp directory must be a local disk path, such as /tmp/pigeon-pod.',
        }),
      );
      return;
    }
    setSystemConfigTesting(true);
    try {
      const res = await API.post('/api/account/system-config/storage/test', payload);
      const { code, msg } = res.data;
      if (code !== 200) {
        showError(msg);
        return;
      }
      showSuccess(
        t('storage_connection_test_success', {
          defaultValue: 'Storage connection test succeeded.',
        }),
      );
    } finally {
      setSystemConfigTesting(false);
    }
  };

  const changeStorageType = async (nextType) => {
    if (!nextType || nextType === systemConfig.storageType) {
      return;
    }
    setStorageSwitchChecking(true);
    try {
      const res = await API.get('/api/account/system-config/storage/switch-check', {
        params: { targetType: nextType },
      });
      const { code, msg, data } = res.data;
      if (code !== 200) {
        showError(msg);
        return;
      }
      if (!data?.canSwitch) {
        showError(
          data?.message ||
            t('storage_switch_check_failed', {
              defaultValue: 'Storage strategy cannot be switched at the moment.',
            }),
        );
        return;
      }
    } finally {
      setStorageSwitchChecking(false);
    }
    setPendingStorageType(nextType);
    openConfirmStorageSwitch();
  };

  const confirmStorageTypeSwitch = () => {
    if (!pendingStorageType) {
      closeConfirmStorageSwitch();
      return;
    }
    setSystemConfig((prev) => ({
      ...prev,
      storageType: pendingStorageType,
    }));
    setPendingStorageType(null);
    closeConfirmStorageSwitch();
  };

  const cancelStorageTypeSwitch = () => {
    setPendingStorageType(null);
    closeConfirmStorageSwitch();
  };

  const resetPasswordForm = useForm({
    mode: 'uncontrolled',
    initialValues: {
      oldPassword: '',
      newPassword: '',
    },
    validate: {
      oldPassword: hasLength({ min: 6 }, t('old_password_validation')),
      newPassword: hasLength({ min: 6 }, t('new_password_validation')),
    },
  });

  const changeUsernameForm = useForm({
    mode: 'uncontrolled',
    initialValues: {
      username: '',
    },
    validate: {
      username: (value) =>
        value.length >= 3 && value.length <= 20
          ? null
          : 'Username must be between 3 and 20 characters',
    },
  });

  return (
    <Container size="lg" mt="lg">
      {!state?.user ? (
        <Stack>
          <Paper p="md">
            <Text c="dimmed">{t('loading')}...</Text>
          </Paper>
        </Stack>
      ) : (
        <Stack>
          <Paper p="md">
            <Stack>
              <Title order={4}>{t('account_setting')}</Title>
              <Title order={6}>{t('setting_group_account')}</Title>
              <Group>
                <Text c="dimmed">{t('username')}:</Text>
                <Text>{state.user?.username}</Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Youtube Api Key"
                  onClick={openChangeUsername}
                >
                  <IconEdit size={18} />
                </ActionIcon>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Youtube Api Key"
                  onClick={openResetPassword}
                >
                  <IconLockPassword size={18} />
                </ActionIcon>
              </Group>
              <Divider hiddenFrom="sm" />

              <Group>
                <Text c="dimmed">API Key:</Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Regenerate API Key"
                  onClick={openConfirmGenerateApiKey}
                  hiddenFrom="sm"
                >
                  <IconRefresh size={18} />
                </ActionIcon>
                {state.user?.apiKey ? (
                  <PasswordInput
                    value={state.user.apiKey}
                    readOnly
                    variant="unstyled"
                    size="sm"
                    style={{ flex: 1, maxWidth: '300px' }}
                    visible={showApiKey}
                    onVisibilityChange={setShowApiKey}
                    rightSection={
                      <ActionIcon
                        variant="transparent"
                        size="sm"
                        onClick={() => setShowApiKey(!showApiKey)}
                        aria-label="Toggle API Key visibility"
                      >
                        {showApiKey ? <IconEyeOff size={20} /> : <IconEye size={20} />}
                      </ActionIcon>
                    }
                  />
                ) : (
                  <Text c="dimmed">{t('not_set')}</Text>
                )}
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Regenerate API Key"
                  onClick={openConfirmGenerateApiKey}
                  visibleFrom="sm"
                >
                  <IconRefresh size={18} />
                </ActionIcon>
              </Group>
              <Divider hiddenFrom="sm" />

              <Group>
                <Text c="dimmed">YouTube API Key:</Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Youtube Api Key"
                  onClick={openEditYoutubeApiKey}
                  hiddenFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
                {systemConfig.youtubeApiKey ? (
                  <PasswordInput
                    value={systemConfig.youtubeApiKey}
                    readOnly
                    variant="unstyled"
                    size="sm"
                    style={{ flex: 1, maxWidth: '300px' }}
                    visible={showYoutubeApiKey}
                    onVisibilityChange={setShowYoutubeApiKey}
                    rightSection={
                      <ActionIcon
                        variant="transparent"
                        size="sm"
                        onClick={() => setShowYoutubeApiKey(!showYoutubeApiKey)}
                        aria-label="Toggle YouTube API Key visibility"
                      >
                        {showYoutubeApiKey ? <IconEyeOff size={20} /> : <IconEye size={20} />}
                      </ActionIcon>
                    }
                  />
                ) : (
                  <Text c="dimmed">{t('youtube_api_key_not_set')}</Text>
                )}
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Youtube Api Key"
                  onClick={openEditYoutubeApiKey}
                  visibleFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
              </Group>
              <Divider hiddenFrom="sm" />

              <Group>
                <Text c="dimmed">{t('cookies', { defaultValue: 'Cookies' })}:</Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit cookies"
                  onClick={openUploadCookies}
                  hiddenFrom="sm"
                >
                  <IconCookie size={18} />
                </ActionIcon>
                <Text>
                  {hasUploadedCookies
                    ? t('cookies_set', { defaultValue: 'Configured' })
                    : t('not_set')}
                </Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit cookies"
                  onClick={openUploadCookies}
                  visibleFrom="sm"
                >
                  <IconCookie size={18} />
                </ActionIcon>
              </Group>
              <Divider />
              <Title order={6}>{t('setting_group_system')}</Title>
              <Group>
                <Text c="dimmed">{t('base_url_label', { defaultValue: 'Base URL' })}:</Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Base URL"
                  onClick={openEditBaseUrl}
                  hiddenFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
                <Text>{systemConfig.baseUrl?.trim() || t('not_set')}</Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Base URL"
                  onClick={openEditBaseUrl}
                  visibleFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
              </Group>
              <Divider hiddenFrom="sm" />

              <Group>
                <Text c="dimmed">
                  {t('storage_strategy_label', { defaultValue: 'Storage strategy' })}:
                </Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Storage Strategy"
                  onClick={handleOpenEditStorageConfig}
                  hiddenFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
                <Text>
                  {systemConfig.storageType === 'S3'
                    ? `S3${systemConfig.s3Bucket ? ` · ${systemConfig.s3Bucket}` : ''}`
                    : 'LOCAL'}
                </Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Storage Strategy"
                  onClick={handleOpenEditStorageConfig}
                  visibleFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
              </Group>
              <Divider hiddenFrom="sm" />

              <Group>
                <Text c="dimmed">{t('date_format')}:</Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Date Format"
                  onClick={openEditDateFormat}
                  hiddenFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
                <Text>{state.user?.dateFormat || DEFAULT_DATE_FORMAT}</Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Date Format"
                  onClick={openEditDateFormat}
                  visibleFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
              </Group>
              <Divider hiddenFrom="sm" />

              <Group>
                <Text c="dimmed">{t('feed_defaults', { defaultValue: 'Feed defaults' })}:</Text>
                <Button size="xs" variant="default" onClick={openEditFeedDefaults}>
                  {t('setup', { defaultValue: 'Setup' })}
                </Button>
              </Group>
              <Divider hiddenFrom="sm" />

              <Group>
                <Text c="dimmed">{t('export_subscriptions_opml')}:</Text>
                <Button
                  size="xs"
                  variant="default"
                  leftSection={<IconDownload size={14} />}
                  onClick={() => {
                    openExportOpmlModal().then();
                  }}
                >
                  {t('export_subscriptions_action')}
                </Button>
              </Group>
              <Divider hiddenFrom="sm" />

              <Group>
                <Text c="dimmed">{t('login_captcha')}:</Text>
                <Switch
                  checked={loginCaptchaEnabled}
                  onChange={(event) => {
                    const enabled = event.currentTarget.checked;
                    updateLoginCaptcha(enabled).then();
                  }}
                  disabled={loginCaptchaSaving}
                />
              </Group>
              <Divider hiddenFrom="sm" />

              <Group>
                <Text c="dimmed">{t('yt_dlp_args', { defaultValue: 'yt-dlp args' })}:</Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit yt-dlp arguments"
                  onClick={openEditYtDlpArgs}
                  hiddenFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
                <Text>
                  {ytDlpArgsText ? t('customized', { defaultValue: 'Customized' }) : t('not_set')}
                </Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit yt-dlp arguments"
                  onClick={openEditYtDlpArgs}
                  visibleFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
              </Group>
              <Divider hiddenFrom="sm" />

              <Group>
                <Text c="dimmed">
                  {t('yt_dlp_runtime_label', { defaultValue: 'yt-dlp version' })}:
                </Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Manage yt-dlp version"
                  onClick={openEditYtDlpRuntime}
                  hiddenFrom="sm"
                >
                  <IconCloudUp size={18} />
                </ActionIcon>
                <Text>
                  {ytDlpRuntime?.version ||
                    t('yt_dlp_version_unknown', {
                      defaultValue: 'Unknown',
                    })}
                  {' | '}
                  {getYtDlpStatusText(ytDlpRuntime?.status?.state)}
                </Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Manage yt-dlp version"
                  onClick={openEditYtDlpRuntime}
                  visibleFrom="sm"
                >
                  <IconCloudUp size={18} />
                </ActionIcon>
              </Group>
              <Divider hiddenFrom="sm" />
            </Stack>
          </Paper>
        </Stack>
      )}

      {/* Reset Password Modal */}
      <Modal opened={resetPasswordOpened} onClose={closeResetPassword} title={t('reset_password')}>
        <form onSubmit={resetPasswordForm.onSubmit((values) => resetPassword(values))}>
          <PasswordInput
            name="oldPassword"
            label={t('old_password')}
            withAsterisk
            leftSection={<IconLock size={16} />}
            placeholder={t('enter_old_password')}
            key={resetPasswordForm.key('oldPassword')}
            {...resetPasswordForm.getInputProps('oldPassword')}
            style={{ flex: 1 }}
          />
          <PasswordInput
            mt="sm"
            name="newPassword"
            label={t('new_password')}
            withAsterisk
            leftSection={<IconLock size={16} />}
            placeholder={t('enter_new_password')}
            key={resetPasswordForm.key('newPassword')}
            {...resetPasswordForm.getInputProps('newPassword')}
            style={{ flex: 1 }}
          />
          <Group justify="flex-end" mt="sm">
            <Button mt="sm" loading={resetPasswordLoading} type="submit">
              {t('confirm_reset')}
            </Button>
          </Group>
        </form>
      </Modal>

      {/* Confirm Generate API Key Modal */}
      <Modal
        opened={confirmGenerateApiKeyOpened}
        onClose={closeConfirmGenerateApiKey}
        title={t('confirm_generation')}
      >
        <Text fw={500}>{t('confirm_generate_api_key_tip')}</Text>
        <Group justify="flex-end" mt="md">
          <Button
            color="red"
            onClick={() => {
              generateApiKey().then(closeConfirmGenerateApiKey);
            }}
          >
            {t('confirm')}
          </Button>
        </Group>
      </Modal>

      <Modal
        opened={editYtDlpArgsOpened}
        onClose={closeEditYtDlpArgs}
        size="lg"
        title={t('yt_dlp_args', { defaultValue: 'yt-dlp args' })}
      >
        <Stack>
          <Alert>
            <Text c="red" size="sm" fw={500}>
              {t('yt_dlp_args_warning', {
                defaultValue:
                  '⚠️ Custom yt-dlp arguments are advanced and may cause downloads to fail. If issues occur, remove the arguments and retry.',
              })}
            </Text>
          </Alert>
          <Textarea
            label={t('yt_dlp_args_input', { defaultValue: 'Custom arguments' })}
            placeholder="--force-ipv6"
            resize="vertical"
            minRows={3}
            value={ytDlpArgsText}
            onChange={(event) => setYtDlpArgsText(event.currentTarget.value)}
          />
          <Text size="sm" c="dimmed">
            {t('yt_dlp_args_hint', {
              defaultValue: 'One argument per line. Example: --force-ipv6.',
            })}
          </Text>
          <Text size="sm">{t('yt_dlp_args_blocked', { defaultValue: 'Blocked arguments:' })}</Text>
          <List size="sm" withPadding>
            {blockedYtDlpArgs.map((arg) => (
              <List.Item key={arg}>
                <code>{arg}</code>
              </List.Item>
            ))}
          </List>
          <Group justify="flex-end">
            <Button variant="default" onClick={closeEditYtDlpArgs}>
              {t('cancel')}
            </Button>
            <Button onClick={saveYtDlpArgs}>{t('save')}</Button>
          </Group>
        </Stack>
      </Modal>

      <Modal
        opened={editYtDlpRuntimeOpened}
        onClose={closeEditYtDlpRuntime}
        title={t('yt_dlp_runtime_label', { defaultValue: 'yt-dlp version' })}
      >
        <Stack>
          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {t('yt_dlp_current_version', { defaultValue: 'Current version' })}
            </Text>
            <Text size="sm" fw={500}>
              {ytDlpRuntime?.version ||
                t('yt_dlp_version_unknown', {
                  defaultValue: 'Unknown',
                })}
            </Text>
          </Group>

          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {t('yt_dlp_update_status_label', { defaultValue: 'Update status' })}
            </Text>
            <Text size="sm" fw={500}>
              {getYtDlpStatusText(ytDlpRuntime?.status?.state)}
            </Text>
          </Group>

          <Select
            label={t('yt_dlp_update_channel', { defaultValue: 'Update channel' })}
            data={[
              {
                label: t('yt_dlp_channel_stable', { defaultValue: 'Stable' }),
                value: 'stable',
              },
              {
                label: t('yt_dlp_channel_nightly', { defaultValue: 'Nightly' }),
                value: 'nightly',
              },
            ]}
            value={ytDlpChannel}
            onChange={(value) => {
              if (value) {
                setYtDlpChannel(value);
              }
            }}
            disabled={ytDlpUpdating}
          />

          <Alert color="blue">
            <Text size="sm">
              {t('yt_dlp_update_persistence_hint', {
                defaultValue:
                  'The installed yt-dlp runtime is stored under /data and survives container recreation.',
              })}
            </Text>
          </Alert>

          {ytDlpRuntime?.status?.state === 'FAILED' && ytDlpRuntime?.status?.error ? (
            <Alert color="red">
              <Text size="sm">{ytDlpRuntime.status.error}</Text>
            </Alert>
          ) : null}

          <Group justify="space-between">
            <Button
              variant="default"
              onClick={() => {
                fetchYtDlpRuntime().catch(() => {});
              }}
            >
              {t('refresh')}
            </Button>
            <Button
              onClick={openConfirmUpdateYtDlp}
              loading={ytDlpUpdateSubmitting}
              disabled={ytDlpUpdating}
            >
              {t('yt_dlp_update_now', { defaultValue: 'Update now' })}
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal
        opened={exportOpmlOpened}
        onClose={closeExportOpml}
        size="lg"
        title={t('export_subscriptions_modal_title')}
      >
        <Stack>
          <Text size="sm" c="dimmed">
            {t('export_subscriptions_modal_desc')}
          </Text>
          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {t('export_subscriptions_selected_count', {
                selected: selectedVisibleExportFeedCount,
                total: filteredExportFeedList.length,
              })}
            </Text>
            <Group gap="xs">
              <Button
                size="xs"
                variant="default"
                onClick={selectAllExportFeeds}
                disabled={exportFeedsLoading || filteredExportFeedList.length === 0}
              >
                {t('export_subscriptions_select_all')}
              </Button>
              <Button
                size="xs"
                variant="default"
                onClick={clearExportFeedSelection}
                disabled={exportFeedsLoading || selectedExportFeedKeys.length === 0}
              >
                {t('export_subscriptions_clear')}
              </Button>
            </Group>
          </Group>
          <Stack gap={4}>
            <Text size="sm" c="dimmed">
              {t('export_subscriptions_filter_label')}
            </Text>
            <SegmentedControl
              fullWidth
              value={exportFeedTypeFilter}
              onChange={setExportFeedTypeFilter}
              disabled={exportFeedsLoading}
              data={[
                {
                  label: t('export_subscriptions_filter_all'),
                  value: 'all',
                },
                {
                  label: t('feed_type_channel'),
                  value: 'channel',
                },
                {
                  label: t('feed_type_playlist'),
                  value: 'playlist',
                },
              ]}
            />
          </Stack>
          <ScrollArea h={300}>
            <Checkbox.Group value={selectedExportFeedKeys} onChange={setSelectedExportFeedKeys}>
              <Stack gap="xs">
                {exportFeedsLoading ? (
                  <Text size="sm" c="dimmed">
                    {t('loading')}...
                  </Text>
                ) : null}
                {!exportFeedsLoading && filteredExportFeedList.length === 0 ? (
                  <Text size="sm" c="dimmed">
                    {exportFeedList.length === 0
                      ? t('export_subscriptions_no_feeds')
                      : t('export_subscriptions_no_filtered_feeds')}
                  </Text>
                ) : null}
                {!exportFeedsLoading
                  ? filteredExportFeedList.map((feed) => {
                      const feedTypeKey = `feed_type_${String(feed?.type || '').toLowerCase()}`;
                      const feedTypeLabel = t(feedTypeKey, { defaultValue: feed?.type || '' });
                      const feedLabel = feed?.customTitle || feed?.title || feed?.id;
                      return (
                        <Checkbox
                          key={getExportFeedKey(feed)}
                          value={getExportFeedKey(feed)}
                          label={`${feedLabel} (${feedTypeLabel})`}
                        />
                      );
                    })
                  : null}
              </Stack>
            </Checkbox.Group>
          </ScrollArea>
          <Group justify="flex-end">
            <Button variant="default" onClick={closeExportOpml}>
              {t('cancel')}
            </Button>
            <Button
              leftSection={<IconDownload size={16} />}
              loading={exportingOpml}
              disabled={exportFeedsLoading || selectedExportFeedKeys.length === 0}
              onClick={() => {
                exportSelectedFeedsAsOpml().then();
              }}
            >
              {t('export_subscriptions_download')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal
        opened={confirmUpdateYtDlpOpened}
        onClose={closeConfirmUpdateYtDlp}
        title={t('yt_dlp_update_confirm_title', { defaultValue: 'Confirm yt-dlp update' })}
      >
        <Text fw={500}>
          {t('yt_dlp_update_confirm_tip', {
            defaultValue: 'Start updating yt-dlp with channel:',
          })}{' '}
          <code>{ytDlpChannel}</code>
        </Text>
        <Group justify="flex-end" mt="md">
          <Button variant="default" onClick={closeConfirmUpdateYtDlp}>
            {t('cancel')}
          </Button>
          <Button
            onClick={() => {
              updateYtDlpVersion().then();
            }}
            loading={ytDlpUpdateSubmitting}
          >
            {t('confirm')}
          </Button>
        </Group>
      </Modal>

      {/* Change Username Modal */}
      <Modal
        opened={changeUsernameOpened}
        onClose={closeChangeUsername}
        title={t('change_username')}
      >
        <form onSubmit={changeUsernameForm.onSubmit((values) => changeUsername(values))}>
          <TextInput
            withAsterisk
            label={t('new_username')}
            placeholder={t('enter_new_username')}
            key={changeUsernameForm.key('username')}
            maxLength={20}
            {...changeUsernameForm.getInputProps('username')}
          />
          <Group justify="flex-end" mt="md">
            <Button type="submit">{t('confirm')}</Button>
          </Group>
        </form>
      </Modal>

      {/* YouTube Data API Key Edit Modal */}
      <Modal
        opened={editYoutubeApiKeyOpened}
        onClose={closeEditYoutubeApiKey}
        title={t('youtube_data_api_key')}
      >
        <Stack>
          <PasswordInput
            label={t('youtube_data_api_key')}
            placeholder={t('enter_youtube_data_api_key')}
            value={youtubeApiKey}
            onChange={(event) => setYoutubeApiKey(event.currentTarget.value)}
            leftSection={<IconLock size={16} />}
          />
          <NumberInput
            label={t('youtube_daily_limit_units_label', {
              defaultValue: 'YouTube daily quota limit',
            })}
            description={t('youtube_daily_limit_units_desc', {
              defaultValue: 'Leave empty for unlimited.',
            })}
            placeholder={t('youtube_daily_limit_units_placeholder', { defaultValue: '10000' })}
            value={youtubeDailyLimitUnits}
            min={1}
            onChange={setYoutubeDailyLimitUnits}
            clampBehavior="strict"
          />
          <Text size="sm" c="dimmed">
            {t('youtube_quota_auto_sync_tip', {
              defaultValue:
                'When the daily quota limit is reached, auto sync will stop for today and resume tomorrow.',
            })}
          </Text>
          {youtubeQuotaToday ? (
            <Alert
              color={
                youtubeQuotaToday.autoSyncBlocked
                  ? 'red'
                  : youtubeQuotaToday.warningReached
                    ? 'orange'
                    : 'blue'
              }
              variant="light"
              radius="md"
            >
              <Stack gap={4}>
                <Text size="sm">
                  {t('youtube_quota_today_usage', {
                    defaultValue: 'Today usage: {{used}} units / {{limit}}',
                    used: youtubeQuotaToday.usedUnits ?? 0,
                    limit: youtubeQuotaToday.dailyLimitUnits
                      ? youtubeQuotaToday.dailyLimitUnits
                      : t('youtube_daily_limit_unlimited', { defaultValue: 'Unlimited' }),
                  })}
                </Text>
                {youtubeQuotaToday.autoSyncBlocked ? (
                  <Text size="sm">
                    {t('youtube_quota_auto_sync_blocked', {
                      defaultValue:
                        'Auto sync is stopped for today because quota limit has been reached. It will resume tomorrow.',
                    })}
                  </Text>
                ) : null}
              </Stack>
            </Alert>
          ) : null}
        </Stack>
        <Group justify="flex-end" mt="md">
          <Button
            onClick={() => {
              saveYoutubeApiKey().then();
            }}
          >
            {t('confirm')}
          </Button>
        </Group>
      </Modal>

      <Modal
        opened={editBaseUrlOpened}
        onClose={closeEditBaseUrl}
        title={t('base_url_label', { defaultValue: 'Base URL' })}
      >
        <Stack>
          <TextInput
            label={t('base_url_label', { defaultValue: 'Base URL' })}
            placeholder="https://your-domain.com"
            value={systemConfig.baseUrl || ''}
            onChange={(event) => {
              const value = event.currentTarget.value;
              setSystemConfig((prev) => ({
                ...prev,
                baseUrl: value,
              }));
            }}
            description={t('base_url_hint', {
              defaultValue:
                'Can be empty for startup, but RSS link generation/copy requires this field.',
            })}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={closeEditBaseUrl}>
              {t('cancel')}
            </Button>
            <Button
              loading={systemConfigSaving}
              onClick={async () => {
                const success = await saveSystemStorageConfig();
                if (success) {
                  closeEditBaseUrl();
                }
              }}
            >
              {t('confirm')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal
        opened={editStorageConfigOpened}
        onClose={handleCloseEditStorageConfig}
        title={t('storage_strategy_label', { defaultValue: 'Storage strategy' })}
        size="lg"
      >
        <Stack gap="sm">
          <SegmentedControl
            value={systemConfig.storageType || 'LOCAL'}
            onChange={changeStorageType}
            disabled={storageSwitchChecking}
            data={[
              { label: 'LOCAL', value: 'LOCAL' },
              { label: 'S3', value: 'S3' },
            ]}
          />
          <Text size="sm" c="dimmed">
            {t('storage_switch_manual_migration_hint', {
              defaultValue:
                'Switching storage mode does not migrate existing media. Please migrate files manually.',
            })}
          </Text>

          {systemConfig.storageType === 'S3' ? (
            <Stack gap="xs">
              <TextInput
                label={t('storage_temp_dir', { defaultValue: 'Temp directory' })}
                description={t('storage_temp_dir_local_only_hint', {
                  defaultValue: '缓存目录只允许使用本地磁盘目录',
                })}
                value={systemConfig.storageTempDir || ''}
                error={
                  systemConfig.storageTempDir && !isLocalDiskPath(systemConfig.storageTempDir)
                    ? t('storage_temp_dir_local_disk_only', {
                        defaultValue:
                          'Temp directory must be a local disk path, such as /tmp/pigeon-pod.',
                      })
                    : null
                }
                onChange={(event) => {
                  const value = event.currentTarget.value;
                  setSystemConfig((prev) => ({
                    ...prev,
                    storageTempDir: value,
                  }));
                }}
              />
              <TextInput
                label={t('s3_endpoint', { defaultValue: 'S3 Endpoint' })}
                placeholder="https://xxx.r2.cloudflarestorage.com"
                value={systemConfig.s3Endpoint || ''}
                onChange={(event) => {
                  const value = event.currentTarget.value;
                  setSystemConfig((prev) => ({
                    ...prev,
                    s3Endpoint: value,
                  }));
                }}
              />
              <TextInput
                label={t('s3_region', { defaultValue: 'S3 Region' })}
                description={t('s3_region_desc', {
                  defaultValue:
                    'Cloudflare R2 usually uses auto; MinIO typically uses us-east-1 unless your MinIO server defines a custom region.',
                })}
                value={systemConfig.s3Region || ''}
                onChange={(event) => {
                  const value = event.currentTarget.value;
                  setSystemConfig((prev) => ({
                    ...prev,
                    s3Region: value,
                  }));
                }}
              />
              <TextInput
                label={t('s3_bucket', { defaultValue: 'S3 Bucket' })}
                value={systemConfig.s3Bucket || ''}
                onChange={(event) => {
                  const value = event.currentTarget.value;
                  setSystemConfig((prev) => ({
                    ...prev,
                    s3Bucket: value,
                  }));
                }}
              />
              <TextInput
                label={t('s3_access_key', { defaultValue: 'S3 Access Key' })}
                value={systemConfig.s3AccessKey || ''}
                onChange={(event) => {
                  const value = event.currentTarget.value;
                  setSystemConfig((prev) => ({
                    ...prev,
                    s3AccessKey: value,
                  }));
                }}
              />
              <PasswordInput
                label={t('s3_secret_key', { defaultValue: 'S3 Secret Key' })}
                placeholder={
                  systemConfig.hasS3SecretKey
                    ? t('s3_secret_keep_hint', {
                        defaultValue: 'Leave empty to keep current secret key',
                      })
                    : ''
                }
                value={systemConfig.s3SecretKey || ''}
                onChange={(event) => {
                  const value = event.currentTarget.value;
                  setSystemConfig((prev) => ({
                    ...prev,
                    s3SecretKey: value,
                    hasS3SecretKey: prev.hasS3SecretKey || Boolean(value),
                  }));
                }}
              />
              <Switch
                checked={Boolean(systemConfig.s3PathStyleAccess)}
                onChange={(event) => {
                  const checked = event.currentTarget.checked;
                  setSystemConfig((prev) => ({
                    ...prev,
                    s3PathStyleAccess: checked,
                  }));
                }}
                label={t('s3_path_style_access', { defaultValue: 'Path style access' })}
                description={t('s3_path_style_access_desc', {
                  defaultValue:
                    'Use path-style URL access. Usually true for MinIO. Cloudflare R2 is usually false when using account endpoint.',
                })}
              />
              <Button
                color="dimmed"
                variant="default"
                fullWidth
                rightSection={
                  storageAdvancedOpened ? (
                    <IconChevronUp size={16} />
                  ) : (
                    <IconChevronDown size={16} />
                  )
                }
                onClick={() => setStorageAdvancedOpened((prev) => !prev)}
              >
                {t('storage_advanced_config', { defaultValue: 'Advanced config' })}
              </Button>
              <Collapse in={storageAdvancedOpened}>
                <Stack gap="xs" mt="xs">
                  <NumberInput
                    label={t('s3_connect_timeout_seconds', { defaultValue: 'Connect timeout (s)' })}
                    description={t('s3_connect_timeout_seconds_desc', {
                      defaultValue:
                        'Maximum time to establish TCP connection with the storage endpoint.',
                    })}
                    min={1}
                    value={systemConfig.s3ConnectTimeoutSeconds}
                    onChange={(value) =>
                      setSystemConfig((prev) => ({
                        ...prev,
                        s3ConnectTimeoutSeconds: value,
                      }))
                    }
                  />
                  <NumberInput
                    label={t('s3_socket_timeout_seconds', { defaultValue: 'Socket timeout (s)' })}
                    description={t('s3_socket_timeout_seconds_desc', {
                      defaultValue:
                        'Maximum idle time for a socket operation before retry/timeout.',
                    })}
                    min={1}
                    value={systemConfig.s3SocketTimeoutSeconds}
                    onChange={(value) =>
                      setSystemConfig((prev) => ({
                        ...prev,
                        s3SocketTimeoutSeconds: value,
                      }))
                    }
                  />
                  <NumberInput
                    label={t('s3_read_timeout_seconds', { defaultValue: 'Read timeout (s)' })}
                    description={t('s3_read_timeout_seconds_desc', {
                      defaultValue:
                        'Maximum time waiting for response body data from storage service.',
                    })}
                    min={1}
                    value={systemConfig.s3ReadTimeoutSeconds}
                    onChange={(value) =>
                      setSystemConfig((prev) => ({
                        ...prev,
                        s3ReadTimeoutSeconds: value,
                      }))
                    }
                  />
                  <NumberInput
                    label={t('s3_presign_expire_hours', { defaultValue: 'Presign expire (hours)' })}
                    description={t('s3_presign_expire_hours_desc', {
                      defaultValue:
                        'How long generated presigned URLs stay valid for play/download links.',
                    })}
                    min={1}
                    value={systemConfig.s3PresignExpireHours}
                    onChange={(value) =>
                      setSystemConfig((prev) => ({
                        ...prev,
                        s3PresignExpireHours: value,
                      }))
                    }
                  />
                </Stack>
              </Collapse>
            </Stack>
          ) : (
            <Stack gap="xs">
              <TextInput
                label={t('local_audio_path', { defaultValue: 'Local audio path' })}
                description={t('local_path_docker_hint', {
                  defaultValue:
                    'If you run PigeonPod with Docker, enter the persistent volume or bind mount directory configured for the container here.',
                })}
                value={systemConfig.localAudioPath || ''}
                onChange={(event) => {
                  const value = event.currentTarget.value;
                  setSystemConfig((prev) => ({
                    ...prev,
                    localAudioPath: value,
                  }));
                }}
              />
              <TextInput
                label={t('local_video_path', { defaultValue: 'Local video path' })}
                description={t('local_path_docker_hint', {
                  defaultValue:
                    'If you run PigeonPod with Docker, enter the persistent volume or bind mount directory configured for the container here.',
                })}
                value={systemConfig.localVideoPath || ''}
                onChange={(event) => {
                  const value = event.currentTarget.value;
                  setSystemConfig((prev) => ({
                    ...prev,
                    localVideoPath: value,
                  }));
                }}
              />
              <TextInput
                label={t('local_cover_path', { defaultValue: 'Local cover path' })}
                description={t('local_path_docker_hint', {
                  defaultValue:
                    'If you run PigeonPod with Docker, enter the persistent volume or bind mount directory configured for the container here.',
                })}
                value={systemConfig.localCoverPath || ''}
                onChange={(event) => {
                  const value = event.currentTarget.value;
                  setSystemConfig((prev) => ({
                    ...prev,
                    localCoverPath: value,
                  }));
                }}
              />
            </Stack>
          )}

          <Group justify="space-between">
            <Button
              variant="light"
              onClick={testSystemStorageConfig}
              loading={systemConfigTesting}
            >
              {t('storage_test_connection', { defaultValue: 'Test connection' })}
            </Button>
            <Group>
              <Button variant="default" onClick={handleCloseEditStorageConfig}>
                {t('cancel')}
              </Button>
              <Button
                loading={systemConfigSaving}
                onClick={async () => {
                  const success = await saveSystemStorageConfig();
                  if (success) {
                    handleCloseEditStorageConfig();
                  }
                }}
              >
                {t('confirm')}
              </Button>
            </Group>
          </Group>
        </Stack>
      </Modal>

      <Modal
        opened={confirmStorageSwitchOpened}
        onClose={cancelStorageTypeSwitch}
        title={t('storage_switch_confirm_title', { defaultValue: 'Confirm storage switch' })}
      >
        <Stack>
          <Alert color="red" variant="light">
            {t('storage_switch_warning', {
              defaultValue:
                'Switching storage mode will not migrate existing media. You must migrate old files manually, otherwise old episodes may be inaccessible.',
            })}
          </Alert>
          <Text size="sm">
            {t('storage_switch_target', {
              defaultValue: 'Target storage type: {{storageType}}',
              storageType: pendingStorageType || '-',
            })}
          </Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={cancelStorageTypeSwitch}>
              {t('cancel')}
            </Button>
            <Button onClick={confirmStorageTypeSwitch}>{t('confirm')}</Button>
          </Group>
        </Stack>
      </Modal>

      {/* Date Format Edit Modal */}
      <Modal
        opened={editDateFormatOpened}
        onClose={closeEditDateFormat}
        title={t('edit_date_format')}
      >
        <Select
          label={t('date_format')}
          placeholder={t('select_date_format')}
          data={DATE_FORMAT_OPTIONS}
          value={dateFormat}
          onChange={setDateFormat}
          leftSection={<IconCalendar size={16} />}
          withAsterisk
        />
        <Group justify="flex-end" mt="md">
          <Button
            onClick={() => {
              saveDateFormat().then();
            }}
          >
            {t('confirm')}
          </Button>
        </Group>
      </Modal>

      {/* Feed Defaults Edit Modal */}
      <Modal
        opened={editFeedDefaultsOpened}
        onClose={closeEditFeedDefaults}
        title={t('edit_feed_defaults', { defaultValue: 'Edit feed defaults' })}
        size="lg"
      >
        <Stack>
          <NumberInput
            label={t('auto_download_limit')}
            description={t('auto_download_limit_description', {
              defaultValue: 'Default number of episodes to auto download for new feeds.',
            })}
            value={feedDefaults.autoDownloadLimit}
            onChange={(value) =>
              setFeedDefaults((prev) => ({
                ...prev,
                autoDownloadLimit: value === '' ? null : value,
              }))
            }
            min={1}
            placeholder={t('3')}
            clampBehavior="strict"
          />

          <NumberInput
            label={t('auto_download_delay_minutes')}
            description={t('auto_download_delay_minutes_description')}
            value={feedDefaults.autoDownloadDelayMinutes}
            onChange={(value) =>
              setFeedDefaults((prev) => ({
                ...prev,
                autoDownloadDelayMinutes: value === '' ? null : value,
              }))
            }
            min={0}
            clampBehavior="strict"
          />

          <NumberInput
            label={t('maximum_episodes')}
            description={t('default_maximum_episodes_description')}
            value={feedDefaults.maximumEpisodes}
            onChange={(value) =>
              setFeedDefaults((prev) => ({
                ...prev,
                maximumEpisodes: value === '' ? null : value,
              }))
            }
            min={1}
            placeholder={t('unlimited')}
            clampBehavior="strict"
          />

          <Radio.Group
            label={t('download_type')}
            value={feedDefaults.downloadType || 'AUDIO'}
            onChange={(value) => {
              setFeedDefaults((prev) => ({
                ...prev,
                downloadType: value,
                audioQuality: value === 'VIDEO' ? null : prev.audioQuality,
                videoQuality: value === 'AUDIO' ? '' : prev.videoQuality,
                videoEncoding: value === 'AUDIO' ? '' : prev.videoEncoding,
              }));
            }}
          >
            <Group mt="xs">
              <Radio value="AUDIO" label={t('audio')} />
              <Radio value="VIDEO" label={t('video')} />
            </Group>
          </Radio.Group>

          {(feedDefaults.downloadType || 'AUDIO') === 'AUDIO' ? (
            <NumberInput
              label={t('audio_quality')}
              description={t('audio_quality_description')}
              value={feedDefaults.audioQuality}
              onChange={(value) =>
                setFeedDefaults((prev) => ({
                  ...prev,
                  audioQuality: value === '' ? null : value,
                }))
              }
              min={0}
              max={10}
              clampBehavior="strict"
            />
          ) : (
            <>
              <Select
                label={t('video_quality')}
                description={t('video_quality_description')}
                data={[
                  { value: '', label: t('best') },
                  { value: '2160', label: '2160p' },
                  { value: '1440', label: '1440p' },
                  { value: '1080', label: '1080p' },
                  { value: '720', label: '720p' },
                  { value: '480', label: '480p' },
                ]}
                value={feedDefaults.videoQuality || ''}
                onChange={(value) =>
                  setFeedDefaults((prev) => ({
                    ...prev,
                    videoQuality: value || '',
                  }))
                }
              />
              <Select
                label={t('video_encoding')}
                description={t('video_encoding_description')}
                data={[
                  { value: '', label: t('default') },
                  { value: 'H264', label: 'H.264' },
                  { value: 'H265', label: 'H.265' },
                ]}
                value={feedDefaults.videoEncoding || ''}
                onChange={(value) =>
                  setFeedDefaults((prev) => ({
                    ...prev,
                    videoEncoding: value || '',
                  }))
                }
              />
            </>
          )}

          <MultiSelect
            label={t('subtitle_languages')}
            description={t('subtitle_languages_desc')}
            placeholder={t('select_subtitle_languages')}
            value={
              feedDefaults.subtitleLanguages
                ? feedDefaults.subtitleLanguages.split(',').filter(Boolean)
                : []
            }
            onChange={(value) =>
              setFeedDefaults((prev) => ({
                ...prev,
                subtitleLanguages: value.length > 0 ? value.join(',') : null,
              }))
            }
            data={SUBTITLE_LANGUAGE_OPTIONS}
            searchable
            clearable
          />

          <Select
            label={t('subtitle_format')}
            description={t('subtitle_format_desc')}
            value={feedDefaults.subtitleFormat || ''}
            onChange={(value) =>
              setFeedDefaults((prev) => ({
                ...prev,
                subtitleFormat: value || null,
              }))
            }
            data={[
              { value: '', label: t('default') },
              ...SUBTITLE_FORMAT_OPTIONS.map((opt) => ({
                ...opt,
                label: opt.value === 'vtt' ? opt.label + ' - ' + t('recommended') : opt.label,
              })),
            ]}
          />

          <Group justify="space-between" mt="md">
            <Button variant="default" onClick={openApplyFeedDefaults}>
              {t('apply_feed_defaults', { defaultValue: 'Apply' })}
            </Button>
            <Button
              onClick={() => {
                saveFeedDefaults().then((success) => {
                  if (success) {
                    closeEditFeedDefaults();
                  }
                });
              }}
            >
              {t('confirm')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      {/* Apply Feed Defaults Modal */}
      <Modal
        opened={applyFeedDefaultsOpened}
        onClose={closeApplyFeedDefaults}
        title={t('apply_feed_defaults_title', { defaultValue: 'Apply feed defaults' })}
      >
        <Stack>
          <Text size="sm">
            {t('apply_feed_defaults_description', {
              defaultValue: 'Choose how to apply current defaults to existing feeds.',
            })}
          </Text>
          <Radio.Group
            label={t('apply_feed_defaults_mode', { defaultValue: 'Apply mode' })}
            value={applyFeedDefaultsMode}
            onChange={setApplyFeedDefaultsMode}
          >
            <Stack mt="xs" gap="xs">
              <Radio
                value="override_all"
                label={t('apply_feed_defaults_mode_override_all', {
                  defaultValue: 'Override all feeds',
                })}
              />
              <Radio
                value="fill_empty"
                label={t('apply_feed_defaults_mode_fill_empty', {
                  defaultValue: 'Only unconfigured feeds',
                })}
              />
            </Stack>
          </Radio.Group>
          <Group justify="flex-end">
            <Button variant="default" onClick={closeApplyFeedDefaults}>
              {t('cancel')}
            </Button>
            <Button
              onClick={() => {
                applyFeedDefaults().then();
              }}
              loading={applyingFeedDefaults}
            >
              {t('confirm')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      {/* Upload Cookies Modal */}
      <Modal
        opened={uploadCookiesOpened}
        onClose={closeUploadCookies}
        size="lg"
        title={t('manage_youtube_cookies')}
      >
        <Stack>
          <Alert>
            <Text c="red" size="sm" fw={500}>
              {t('cookies_warning')}
            </Text>
            <Anchor
              target="_blank"
              href="https://github.com/yt-dlp/yt-dlp/wiki/Extractors#exporting-youtube-cookies"
              size="sm"
              display="block"
              mt="xs"
            >
              {t('cookies_instructions_link')}
            </Anchor>
          </Alert>

          <Group>
            <Text>{t('current_cookie_status')}:</Text>
            <Text c={hasUploadedCookies ? 'green' : 'dimmed'} fw={500}>
              {hasUploadedCookies ? t('cookie_uploaded') : t('cookie_not_uploaded')}
            </Text>
            <Button
              variant="default"
              onClick={deleteCookie}
              disabled={!hasUploadedCookies}
              ml="auto"
            >
              {t('clear_uploaded_cookies')}
            </Button>
          </Group>

          <FileInput
            label={t('upload_update_youtube_cookies')}
            placeholder={t('select_file')}
            accept="text/plain"
            onChange={setCookieFile}
            leftSection={<IconCookie size={16} />}
          />
        </Stack>
        <Group justify="flex-end" mt="md">
          <Button variant="default" onClick={closeUploadCookies}>
            {t('cancel')}
          </Button>
          <Button onClick={uploadCookies} loading={cookieUploading} disabled={!cookieFile}>
            {t('upload')}
          </Button>
        </Group>
      </Modal>
    </Container>
  );
};

export default UserSetting;
