import React, { useEffect, useState } from 'react';
import {
  Alert,
  Anchor,
  Button,
  Divider,
  FileInput,
  Group,
  Modal,
  Pill,
  Stack,
  Text,
} from '@mantine/core';
import { IconCookie } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

const COOKIE_ORDER = ['YOUTUBE', 'BILIBILI'];

const COOKIE_META = {
  YOUTUBE: {
    instructionUrl: 'https://github.com/yt-dlp/yt-dlp/wiki/Extractors#exporting-youtube-cookies',
    domainHints: ['youtube.com'],
  },
  BILIBILI: {
    instructionUrl: 'https://github.com/yt-dlp/yt-dlp/wiki/FAQ#how-do-i-pass-cookies-to-yt-dlp',
    domainHints: ['bilibili.com'],
  },
};

function getCookieLabel(t, platform) {
  if (platform === 'BILIBILI')
    return t('cookie_platform_bilibili', { defaultValue: 'Bilibili' });
  return t('cookie_platform_youtube', { defaultValue: 'YouTube' });
}

function formatUpdatedAt(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString();
}

export default function CookieConfigModal({ opened, onClose, cookieConfigs, onUpload, onDelete }) {
  const { t } = useTranslation();
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

  async function handleUpload(platform) {
    const file = fileByPlatform[platform];
    if (!file) return;

    setUploadingPlatform(platform);
    const isSuccess = await onUpload(platform, file);
    setUploadingPlatform('');

    if (!isSuccess) return;
    setFileByPlatform((current) => ({
      ...current,
      [platform]: null,
    }));
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
          <Text c="red" size="sm" fw={500}>
            {t('platform_cookie_warning', {
              defaultValue:
                'Using account cookies may cause temporary or permanent restrictions. Use them only when necessary and prefer a throwaway account if possible.',
            })}
          </Text>
        </Alert>

        {COOKIE_ORDER.map((platform, index) => {
          const meta = COOKIE_META[platform];
          const summary = cookieMap.get(platform);
          const isConfigured = Boolean(summary?.hasCookie);
          const updatedAt = formatUpdatedAt(summary?.updatedAt);
          const platformLabel = getCookieLabel(t, platform);

          return (
            <Stack key={platform} gap="sm">
              <Stack gap={6}>
                <Group gap="xs">
                  <Text fw={600}>{platformLabel}</Text>
                  <Text c={isConfigured ? 'green' : 'dimmed'} size="sm">
                    {isConfigured
                      ? t('platform_cookie_status_configured', {
                          defaultValue: 'Configured',
                        })
                      : t('platform_cookie_status_not_configured', {
                          defaultValue: 'Not configured',
                        })}
                  </Text>
                </Group>
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
                <Group gap={6}>
                  {meta.domainHints.map((domain) => (
                    <Pill key={domain}>{domain}</Pill>
                  ))}
                </Group>
                <Anchor target="_blank" href={meta.instructionUrl} size="sm" style={{ width: 'fit-content' }}>
                  {t('platform_cookie_instructions_link', {
                    platform: platformLabel,
                    defaultValue: 'See instructions on how to export {{platform}} cookies',
                  })}
                </Anchor>
                {updatedAt ? (
                  <Text size="xs" c="dimmed">
                    {t('platform_cookie_updated_at', {
                      time: updatedAt,
                      defaultValue: 'Updated: {{time}}',
                    })}
                  </Text>
                ) : null}
              </Stack>

              <FileInput
                label={t('platform_cookie_file_label', {
                  platform: platformLabel,
                  defaultValue: '{{platform}} Cookies File',
                })}
                placeholder={t('select_file')}
                accept="text/plain"
                value={fileByPlatform[platform] || null}
                onChange={(file) =>
                  setFileByPlatform((current) => ({
                    ...current,
                    [platform]: file || null,
                  }))
                }
                leftSection={<IconCookie size={16} />}
              />

              <Group justify="flex-end">
                <Button
                  variant="default"
                  onClick={() => handleDelete(platform)}
                  disabled={!isConfigured}
                  loading={deletingPlatform === platform}
                >
                  {t('platform_cookie_clear', { defaultValue: 'Clear Uploaded Cookies' })}
                </Button>
                <Button
                  onClick={() => handleUpload(platform)}
                  disabled={!fileByPlatform[platform]}
                  loading={uploadingPlatform === platform}
                >
                  {t('upload')}
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
