import React, { useEffect, useState } from 'react';
import {
  Alert,
  Anchor,
  Badge,
  Button,
  Divider,
  FileInput,
  Group,
  Modal,
  Stack,
  Text,
} from '@mantine/core';
import { IconCookie } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { formatDateWithPattern } from '../helpers/utils.js';
import { useDateFormat } from '../hooks/useDateFormat.js';

const COOKIE_ORDER = ['YOUTUBE', 'BILIBILI'];
const COOKIE_INSTRUCTIONS_URL =
  'https://github.com/yt-dlp/yt-dlp/wiki/FAQ#how-do-i-pass-cookies-to-yt-dlp';

function getCookieLabel(t, platform) {
  if (platform === 'BILIBILI')
    return t('cookie_platform_bilibili', { defaultValue: 'Bilibili' });
  return t('cookie_platform_youtube', { defaultValue: 'YouTube' });
}

function formatUpdatedAt(value, dateFormat) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (part) => String(part).padStart(2, '0');
  const datePart = formatDateWithPattern(date, dateFormat);
  const timePart = `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  return `${datePart} ${timePart}`;
}

export default function CookieConfigModal({ opened, onClose, cookieConfigs, onUpload, onDelete }) {
  const { t } = useTranslation();
  const dateFormat = useDateFormat();
  const [fileByPlatform, setFileByPlatform] = useState({});
  const [uploadingPlatform, setUploadingPlatform] = useState('');
  const [deletingPlatform, setDeletingPlatform] = useState('');

  useEffect(() => {
    if (opened) return;
    setFileByPlatform({});
    setUploadingPlatform('');
    setDeletingPlatform('');
  }, [opened]);

  const cookieMap = new Map(
    (cookieConfigs || []).map((config) => [String(config?.platform || '').toUpperCase(), config]),
  );

  async function handleSelectFile(platform, file) {
    setFileByPlatform((current) => ({
      ...current,
      [platform]: file || null,
    }));

    if (!file) return;

    setUploadingPlatform(platform);
    const isSuccess = await onUpload(platform, file);
    setUploadingPlatform('');

    setFileByPlatform((current) => ({
      ...current,
      [platform]: null,
    }));

    if (!isSuccess) return;
  }

  async function handleDelete(platform) {
    setDeletingPlatform(platform);
    const isSuccess = await onDelete(platform);
    setDeletingPlatform('');

    if (!isSuccess) return;
    setFileByPlatform((current) => ({
      ...current,
      [platform]: null,
    }));
  }

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      size="lg"
      title={t('platform_cookies', { defaultValue: 'Platform Cookies' })}
    >
      <Stack>
        <Alert>
          <Stack gap={6}>
            <Text c="red" size="sm" fw={500}>
              {t('platform_cookie_warning', {
                defaultValue:
                  'Using account cookies may cause temporary or permanent restrictions. Use them only when necessary and prefer a throwaway account if possible.',
              })}
            </Text>
            <Anchor
              href={COOKIE_INSTRUCTIONS_URL}
              target="_blank"
              rel="noreferrer"
              size="sm"
              style={{ width: 'fit-content' }}
            >
              {t('platform_cookie_instructions_link', {
                defaultValue: 'See instructions on how to export cookies',
              })}
            </Anchor>
          </Stack>
        </Alert>

        {COOKIE_ORDER.map((platform, index) => {
          const summary = cookieMap.get(platform);
          const isConfigured = Boolean(summary);
          const updatedAt = formatUpdatedAt(summary?.updatedAt, dateFormat);
          const platformLabel = getCookieLabel(t, platform);

          return (
            <Stack key={platform} gap="sm">
              <Stack gap={6}>
                <Group gap="xs">
                  <Text fw={600}>{platformLabel}</Text>
                  <Badge color={isConfigured ? 'darkgreen' : 'gray'} variant="outline" >
                    {isConfigured
                      ? t('platform_cookie_status_configured', {
                          defaultValue: 'Configured',
                        })
                      : t('platform_cookie_status_not_configured', {
                          defaultValue: 'Not configured',
                        })}
                  </Badge>
                </Group>
                {updatedAt ? (
                    <Text size="sm" fs="italic" c="darkgreen">
                      {t('platform_cookie_updated_at', {
                        time: updatedAt,
                        defaultValue: 'Updated: {{time}}',
                      })}
                    </Text>
                ) : null}
                <Text size="sm" c="dimmed">
                  {platform === 'BILIBILI'
                    ? t('platform_cookie_bilibili_description', {
                        defaultValue:
                          'Use Bilibili cookies to improve reliability when yt-dlp hits 412 or other browser verification checks.',
                      })
                    : t('platform_cookie_youtube_description', {
                        defaultValue:
                          'Use YouTube cookies for age-restricted, members-only, or other risk-controlled content.',
                      })}
                </Text>
              </Stack>

              <Group align="flex-end" wrap="nowrap">
                <FileInput
                  label={t('platform_cookie_file_label', {
                    platform: platformLabel,
                    defaultValue: '{{platform}} Cookies File',
                  })}
                  placeholder={t('select_file')}
                  accept="text/plain"
                  value={fileByPlatform[platform] || null}
                  onChange={(file) => {
                    handleSelectFile(platform, file).then();
                  }}
                  leftSection={<IconCookie size={16} />}
                  disabled={uploadingPlatform === platform || deletingPlatform === platform}
                  style={{ flex: 1 }}
                />
                <Button
                  variant="default"
                  onClick={() => handleDelete(platform)}
                  disabled={!isConfigured || uploadingPlatform === platform}
                  loading={deletingPlatform === platform}
                >
                  {t('platform_cookie_clear', { defaultValue: 'Clear Uploaded Cookies' })}
                </Button>
              </Group>

              {index < COOKIE_ORDER.length - 1 ? <Divider /> : null}
            </Stack>
          );
        })}
      </Stack>
    </Modal>
  );
}
