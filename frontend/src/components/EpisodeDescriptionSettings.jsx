import React, { useEffect, useRef, useState } from 'react';
import {
  Anchor,
  Button,
  Divider,
  Group,
  Input,
  Paper,
  ScrollArea,
  SegmentedControl,
  Skeleton,
  Text,
  Textarea,
  Stack,
} from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { useTranslation } from 'react-i18next';
import { API } from '../helpers';
import { episodeTitle, splitDescriptionLinks } from '../helpers/episodeDisplay';

const ORIGINAL_ONLY_TEMPLATE = '{description}';
const STARTER_TEMPLATE = '{description}\n\n{source_link}';
const PLACEHOLDERS = [
  '{description}',
  '{title}',
  '{channel}',
  '{date}',
  '{source_url}',
  '{source_link}',
];
const PREVIEW_DEBOUNCE_MS = 400;

function modeOf(template) {
  if (!template) return 'DEFAULT';
  if (template.trim() === ORIGINAL_ONLY_TEMPLATE) return 'ORIGINAL';
  return 'CUSTOM';
}

function DescriptionPreview({ text }) {
  return (
    <Text size="sm" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
      {splitDescriptionLinks(text).map((part, index) =>
        part.href ? (
          <Anchor key={index} href={part.href} target="_blank" rel="noopener noreferrer">
            {part.text}
          </Anchor>
        ) : (
          <React.Fragment key={index}>{part.text}</React.Fragment>
        ),
      )}
    </Text>
  );
}

/**
 * Chooses how episode descriptions appear in the feed's RSS: PigeonPod's default, the original
 * description only, or a custom format with placeholders. {@code value} is the stored template,
 * {@code null} for the default.
 */
const EpisodeDescriptionSettings = ({
  value,
  onChange,
  feedType,
  feedId,
  feedSource,
  episodes,
}) => {
  const { t } = useTranslation();
  const [mode, setMode] = useState(() => modeOf(value));
  const [preview, setPreview] = useState(null);
  const [isLoadingPreview, setIsLoadingPreview] = useState(false);
  const textareaRef = useRef(null);
  const [debouncedTemplate] = useDebouncedValue(value || '', PREVIEW_DEBOUNCE_MS);
  const previewEpisode = episodes?.[0];
  const previewEpisodeId = previewEpisode?.id;

  useEffect(() => {
    if (!previewEpisodeId) return undefined;
    let isCurrent = true;
    setIsLoadingPreview(true);
    API.post(`/api/feed/${feedType}/${encodeURIComponent(feedId)}/episode-description/preview`, {
      episodeId: previewEpisodeId,
      template: debouncedTemplate,
    })
      .then((res) => {
        if (isCurrent && res?.data?.code === 200) setPreview(res.data.data || '');
      })
      .finally(() => {
        if (isCurrent) setIsLoadingPreview(false);
      });
    return () => {
      isCurrent = false;
    };
  }, [debouncedTemplate, feedType, feedId, previewEpisodeId]);

  const handleModeChange = (nextMode) => {
    setMode(nextMode);
    const templates = {
      DEFAULT: null,
      ORIGINAL: ORIGINAL_ONLY_TEMPLATE,
      CUSTOM: modeOf(value) === 'CUSTOM' ? value : STARTER_TEMPLATE,
    };
    onChange(templates[nextMode]);
  };

  const insertPlaceholder = (placeholder) => {
    const textarea = textareaRef.current;
    const current = value || '';
    const start = textarea?.selectionStart ?? current.length;
    const end = textarea?.selectionEnd ?? current.length;
    onChange(current.slice(0, start) + placeholder + current.slice(end));
    requestAnimationFrame(() => {
      if (!textarea) return;
      textarea.focus();
      textarea.setSelectionRange(start + placeholder.length, start + placeholder.length);
    });
  };

  // PigeonPod only adds the "Source channel" line to YouTube playlist feeds
  const addsSourceChannel =
    feedType === 'playlist' && String(feedSource || '').toUpperCase() === 'YOUTUBE';
  const modeHints = {
    DEFAULT: addsSourceChannel
      ? t('episode_description_default_hint_playlist', {
          defaultValue:
            'A “Source channel” line at the top, then the original description and a link to the source video.',
        })
      : t('episode_description_default_hint', {
          defaultValue: 'The original description, followed by a link to the source video.',
        }),
    ORIGINAL: t('episode_description_original_hint', {
      defaultValue: 'Only the original description, exactly as published.',
    }),
    CUSTOM: t('episode_description_custom_hint', {
      defaultValue: 'Write your own format. Click a placeholder to insert it.',
    }),
  };

  return (
    <Stack gap="sm">
      <Divider
        label={t('episode_descriptions', { defaultValue: 'Episode descriptions' })}
        labelPosition="left"
      />
      <Input.Wrapper
        description={t('episode_descriptions_desc', {
          defaultValue: 'What podcast apps show as each episode’s description.',
        })}
      >
        <SegmentedControl
          mt="xs"
          fullWidth
          value={mode}
          onChange={handleModeChange}
          data={[
            {
              value: 'DEFAULT',
              label: t('episode_description_default', { defaultValue: 'PigeonPod default' }),
            },
            {
              value: 'ORIGINAL',
              label: t('episode_description_original', { defaultValue: 'Original only' }),
            },
            {
              value: 'CUSTOM',
              label: t('episode_description_custom', { defaultValue: 'Custom format' }),
            },
          ]}
        />
      </Input.Wrapper>
      <Text size="xs" c="dimmed">
        {modeHints[mode]}
      </Text>

      {mode === 'CUSTOM' && (
        <Stack gap={6}>
          <Textarea
            ref={textareaRef}
            label={t('episode_description_format', { defaultValue: 'Format' })}
            value={value || ''}
            onChange={(event) => onChange(event.currentTarget.value)}
            autosize
            minRows={3}
            maxRows={8}
            styles={{ input: { fontFamily: 'var(--mantine-font-family-monospace)' } }}
          />
          <Group gap={6}>
            {PLACEHOLDERS.map((placeholder) => (
              <Button
                key={placeholder}
                size="compact-xs"
                variant="light"
                color="gray"
                ff="monospace"
                onClick={() => insertPlaceholder(placeholder)}
              >
                {placeholder}
              </Button>
            ))}
          </Group>
          <Text size="xs" c="dimmed">
            {t('episode_description_placeholders_help', {
              defaultValue:
                '{description} episode description · {title} episode title · {channel} channel name · {date} publish date · {source_url} video address · {source_link} video link',
            })}
          </Text>
        </Stack>
      )}

      {previewEpisode ? (
        <Input.Wrapper
          label={t('episode_description_preview', { defaultValue: 'Preview' })}
          description={episodeTitle(previewEpisode)}
        >
          <Paper withBorder radius="md" p="sm" mt={6}>
            <ScrollArea.Autosize mah={180}>
              {preview === null ? (
                <Skeleton height={60} />
              ) : (
                <div style={{ opacity: isLoadingPreview ? 0.6 : 1 }}>
                  <DescriptionPreview text={preview} />
                </div>
              )}
            </ScrollArea.Autosize>
          </Paper>
        </Input.Wrapper>
      ) : null}
    </Stack>
  );
};

export default EpisodeDescriptionSettings;
