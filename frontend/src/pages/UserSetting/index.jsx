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
  IconCloudUp,
} from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { DATE_FORMAT_OPTIONS, DEFAULT_DATE_FORMAT } from '../../constants/dateFormats.js';
import {
  SUBTITLE_LANGUAGE_OPTIONS,
  SUBTITLE_FORMAT_OPTIONS,
} from '../../constants/subtitleLanguages.js';

const formatYtDlpArgsText = (value) => {
  if (!value) return '';
  if (Array.isArray(value)) {
    return value.join('\n');
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) return '';
    const parsed = JSON.parse(trimmed);
    if (Array.isArray(parsed)) {
      return parsed.join('\n');
    }
    return trimmed;
  }
  return '';
};

const parseYtDlpArgsText = (text) => {
  if (!text) return [];
  return text
    .split('\n')
    .flatMap((line) => line.trim().split(/\s+/))
    .filter(Boolean);
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

  // Subtitle settings states
  const [editSubtitleOpened, { open: openEditSubtitle, close: closeEditSubtitle }] =
    useDisclosure(false);
  const [subtitleLanguages, setSubtitleLanguages] = useState(() => {
    const langs = state.user?.subtitleLanguages || 'zh,en';
    return langs.split(',').filter(Boolean);
  });
  const [subtitleFormat, setSubtitleFormat] = useState(state.user?.subtitleFormat || 'vtt');
  const [editYtDlpArgsOpened, { open: openEditYtDlpArgs, close: closeEditYtDlpArgs }] =
    useDisclosure(false);
  const [ytDlpArgsText, setYtDlpArgsText] = useState(() =>
    formatYtDlpArgsText(state.user?.ytDlpArgs),
  );
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

  useEffect(() => {
    setYtDlpArgsText(formatYtDlpArgsText(state.user?.ytDlpArgs));
  }, [state.user?.ytDlpArgs]);

  useEffect(() => {
    if (!state.user) return;
    const langs = state.user.subtitleLanguages || 'zh,en';
    setSubtitleLanguages(langs.split(',').filter(Boolean));
    setSubtitleFormat(state.user.subtitleFormat || 'vtt');
  }, [state.user]);

  useEffect(() => {
    const fetchLoginCaptchaConfig = async () => {
      const res = await API.get('/api/auth/captcha-config');
      const { code, msg, data } = res.data;
      if (code === 200) {
        setLoginCaptchaEnabled(Boolean(data));
      } else {
        showError(msg);
      }
    };
    if (state.user) {
      fetchLoginCaptchaConfig().then();
    }
  }, [state.user]);

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
    const res = await API.post('/api/account/update-youtube-api-key', {
      id: state.user.id,
      youtubeApiKey: youtubeApiKey,
    });
    const { code, msg, data } = res.data;
    if (code === 200) {
      showSuccess(t('youtube_api_key_saved'));
      // update the apiKey in the context
      const user = {
        ...state.user,
        youtubeApiKey: data,
      };
      dispatch({
        type: 'login',
        payload: user,
      });
      localStorage.setItem('user', JSON.stringify(user));
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
      const user = { ...state.user, hasCookie: true };
      dispatch({ type: 'login', payload: user });
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
      const user = { ...state.user, hasCookie: false };
      dispatch({ type: 'login', payload: user });
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

  // Subtitle settings functions
  const saveSubtitleSettings = async () => {
    const res = await API.post('/api/account/update-subtitle-settings', {
      id: state.user.id,
      subtitleLanguages: subtitleLanguages.join(','), // 将数组转换为逗号分隔的字符串
      subtitleFormat: subtitleFormat,
    });
    const { code, msg, data } = res.data;
    if (code === 200) {
      showSuccess(t('subtitle_settings_updated'));
      const user = {
        ...state.user,
        subtitleLanguages: data.subtitleLanguages,
        subtitleFormat: data.subtitleFormat,
      };
      dispatch({
        type: 'login',
        payload: user,
      });
      localStorage.setItem('user', JSON.stringify(user));
      closeEditSubtitle();
    } else {
      showError(msg);
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
      const user = {
        ...state.user,
        ytDlpArgs: data,
      };
      dispatch({
        type: 'login',
        payload: user,
      });
      localStorage.setItem('user', JSON.stringify(user));
      closeEditYtDlpArgs();
    } else {
      showError(msg);
    }
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
          <Paper shadow="xs" p="md">
            <Text c="dimmed">{t('loading')}...</Text>
          </Paper>
        </Stack>
      ) : (
        <Stack>
          <Paper shadow="xs" p="md">
            <Stack>
              <Title order={4}>{t('account_setting')}</Title>
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
                {state.user?.youtubeApiKey ? (
                  <PasswordInput
                    value={state.user.youtubeApiKey}
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
                <Text c="dimmed">{t('subtitle_settings')}:</Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Subtitle Settings"
                  onClick={openEditSubtitle}
                  hiddenFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
                <Text>
                  {subtitleLanguages
                    .map(
                      (lang) =>
                        SUBTITLE_LANGUAGE_OPTIONS.find((opt) => opt.value === lang)?.label || lang,
                    )
                    .join(', ')}{' '}
                  | {subtitleFormat.toUpperCase()}
                </Text>
                <ActionIcon
                  variant="transparent"
                  size="sm"
                  aria-label="Edit Subtitle Settings"
                  onClick={openEditSubtitle}
                  visibleFrom="sm"
                >
                  <IconEdit size={18} />
                </ActionIcon>
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
                  {state.user?.hasCookie
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
            <Button onClick={openConfirmUpdateYtDlp} loading={ytDlpUpdateSubmitting} disabled={ytDlpUpdating}>
              {t('yt_dlp_update_now', { defaultValue: 'Update now' })}
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
        <PasswordInput
          label={t('youtube_data_api_key')}
          placeholder={t('enter_youtube_data_api_key')}
          value={youtubeApiKey}
          onChange={(event) => setYoutubeApiKey(event.currentTarget.value)}
          withAsterisk
          leftSection={<IconLock size={16} />}
        />
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

      {/* Subtitle Settings Edit Modal */}
      <Modal
        opened={editSubtitleOpened}
        onClose={closeEditSubtitle}
        title={t('edit_subtitle_settings')}
      >
        <MultiSelect
          label={t('subtitle_languages')}
          description={t('subtitle_languages_desc')}
          placeholder={t('select_subtitle_languages')}
          value={subtitleLanguages}
          onChange={setSubtitleLanguages}
          data={SUBTITLE_LANGUAGE_OPTIONS}
          searchable
          clearable
          mb="md"
        />
        <Select
          label={t('subtitle_format')}
          description={t('subtitle_format_desc')}
          value={subtitleFormat}
          onChange={setSubtitleFormat}
          data={SUBTITLE_FORMAT_OPTIONS.map((opt) => ({
            ...opt,
            label: opt.value === 'vtt' ? opt.label + ' - ' + t('recommended') : opt.label,
          }))}
          mb="md"
        />
        <Group justify="flex-end" mt="md">
          <Button
            onClick={() => {
              saveSubtitleSettings().then();
            }}
          >
            {t('confirm')}
          </Button>
        </Group>
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
            <Text c={state.user?.hasCookie ? 'green' : 'dimmed'} fw={500}>
              {state.user?.hasCookie ? t('cookie_uploaded') : t('cookie_not_uploaded')}
            </Text>
            <Button
              variant="default"
              onClick={deleteCookie}
              disabled={!state.user?.hasCookie}
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
