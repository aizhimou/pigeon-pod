import React, { useState } from 'react';
import {
  Modal,
  Stack,
  TagsInput,
  NumberInput,
  Select,
  Group,
  Radio,
  Text,
  Tooltip,
  ActionIcon,
  Switch,
  MultiSelect,
  SegmentedControl,
} from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { IconHelpCircle } from '@tabler/icons-react';
import { SUBTITLE_LANGUAGE_OPTIONS, SUBTITLE_FORMAT_OPTIONS } from '../constants/subtitleLanguages';

const EditFeedModal = ({
  opened,
  onClose,
  title,
  feed,
  onFeedChange,
  autoDownloadLimitField,
  actionButtons,
  onPreview,
  size = 'md',
}) => {
  const { t } = useTranslation();
  const [mode, setMode] = useState('basic');
  const isExpertMode = mode === 'expert';
  const audioQualityDocUrl =
    'https://github.com/aizhimou/pigeon-pod/blob/main/documents/audio-quality-guide/audio-quality-guide-en.md';

  const vidoeEncodingDocUrl =
    'https://github.com/aizhimou/pigeon-pod/blob/main/documents/video-encoding-guide/video-encoding-guide-en.md';

  const handleFieldChange = (field, value) => {
    const newFeed = { ...feed, [field]: value };
    onFeedChange(newFeed);
    if (onPreview) {
      onPreview();
    }
  };

  const parseKeywords = (keywords) => {
    if (!keywords || !keywords.trim()) {
      return [];
    }
    return keywords
      .split(/[,\s]+/)
      .map((keyword) => keyword.trim())
      .filter(Boolean);
  };

  const formatKeywords = (keywords) => {
    if (!keywords || keywords.length === 0) {
      return '';
    }
    return keywords.join(' ');
  };

  const renderAudioQualityLabel = () => (
    <Group gap={4} align="center">
      <Text>{t('audio_quality')}</Text>
      <Tooltip label={t('audio_quality_help_tooltip')} withArrow>
        <ActionIcon
          component="a"
          href={audioQualityDocUrl}
          target="_blank"
          rel="noopener noreferrer"
          variant="subtle"
          size="sm"
          aria-label={t('audio_quality_help_tooltip')}
        >
          <IconHelpCircle size={16} />
        </ActionIcon>
      </Tooltip>
    </Group>
  );

  const renderVideoEncodingLabel = () => (
    <Group gap={4} align="center">
      <Text>{t('video_encoding')}</Text>
      <Tooltip label={t('video_encoding_help_tooltip')} withArrow>
        <ActionIcon
          component="a"
          href={vidoeEncodingDocUrl}
          target="_blank"
          rel="noopener noreferrer"
          variant="subtle"
          size="sm"
          aria-label={t('video_encoding_help_tooltip')}
        >
          <IconHelpCircle size={16} />
        </ActionIcon>
      </Tooltip>
    </Group>
  );

  return (
    <Modal opened={opened} onClose={onClose} title={title} size={size}>
      <Stack>
        <SegmentedControl
          fullWidth
          value={mode}
          onChange={setMode}
          data={[
            { label: t('simple_mode'), value: 'basic' },
            { label: t('expert_mode'), value: 'expert' },
          ]}
        />

        <Switch
          label={t('auto_download_enabled')}
          description={t('auto_download_enabled_description')}
          checked={feed?.autoDownloadEnabled !== false}
          onChange={(event) => handleFieldChange('autoDownloadEnabled', event.currentTarget.checked)}
        />
        <TagsInput
          label={t('title_contain_keywords')}
          name="titleContainKeywords"
          placeholder={t('multiple_keywords_space_separated')}
          value={parseKeywords(feed?.titleContainKeywords)}
          onChange={(value) => handleFieldChange('titleContainKeywords', formatKeywords(value))}
          splitChars={[',']}
        />
        <TagsInput
          label={t('title_exclude_keywords')}
          name="titleExcludeKeywords"
          placeholder={t('multiple_keywords_space_separated')}
          value={parseKeywords(feed?.titleExcludeKeywords)}
          onChange={(value) => handleFieldChange('titleExcludeKeywords', formatKeywords(value))}
          splitChars={[',']}
        />
        <NumberInput
          label={t('minimum_duration_minutes')}
          name="minimumDuration"
          placeholder="0"
          value={feed?.minimumDuration}
          onChange={(value) => handleFieldChange('minimumDuration', value)}
        />

        <NumberInput
          label={t('maximum_duration_minutes')}
          name="maximumDuration"
          placeholder={t('unlimited')}
          value={feed?.maximumDuration}
          onChange={(value) => handleFieldChange('maximumDuration', value)}
        />

        {/* Slot for the initial episodes field */}
        {autoDownloadLimitField}

        <NumberInput
          label={t('auto_download_delay_minutes')}
          description={t('auto_download_delay_minutes_description')}
          name="autoDownloadDelayMinutes"
          placeholder="0"
          min={0}
          clampBehavior="strict"
          value={feed?.autoDownloadDelayMinutes ?? 0}
          onChange={(value) => handleFieldChange('autoDownloadDelayMinutes', value === '' ? 0 : value)}
          disabled={feed?.autoDownloadEnabled === false}
        />

        {isExpertMode && (
          <>
            <TagsInput
              label={t('description_contain_keywords')}
              name="descriptionContainKeywords"
              placeholder={t('multiple_keywords_space_separated')}
              value={parseKeywords(feed?.descriptionContainKeywords)}
              onChange={(value) => handleFieldChange('descriptionContainKeywords', formatKeywords(value))}
              splitChars={[',']}
            />
            <TagsInput
              label={t('description_exclude_keywords')}
              name="descriptionExcludeKeywords"
              placeholder={t('multiple_keywords_space_separated')}
              value={parseKeywords(feed?.descriptionExcludeKeywords)}
              onChange={(value) => handleFieldChange('descriptionExcludeKeywords', formatKeywords(value))}
              splitChars={[',']}
            />

            <NumberInput
              label={t('maximum_episodes')}
              name="maximumEpisodes"
              placeholder={t('unlimited')}
              value={feed?.maximumEpisodes}
              onChange={(value) => handleFieldChange('maximumEpisodes', value === '' ? null : value)}
            />

            <Radio.Group
              name="downloadType"
              label={t('download_type')}
              value={feed?.downloadType || 'AUDIO'}
              onChange={(value) => {
                const newFeed = {
                  ...feed,
                  downloadType: value,
                  audioQuality: value === 'VIDEO' ? null : feed.audioQuality,
                  videoQuality: value === 'AUDIO' ? null : feed.videoQuality,
                  videoEncoding: value === 'AUDIO' ? null : feed.videoEncoding,
                };
                onFeedChange(newFeed);
                if (onPreview) {
                  onPreview();
                }
              }}
            >
              <Group mt="xs">
                <Radio value="AUDIO" label={t('audio')} />
                <Radio value="VIDEO" label={t('video')} />
              </Group>
            </Radio.Group>

            {(feed?.downloadType || 'AUDIO') === 'AUDIO' ? (
              <NumberInput
                label={renderAudioQualityLabel()}
                description={t('audio_quality_description')}
                name="audioQuality"
                placeholder=""
                min={0}
                max={10}
                clampBehavior="strict"
                value={feed?.audioQuality}
                onChange={(value) => handleFieldChange('audioQuality', value === '' ? null : value)}
              />
            ) : (
              <>
                <Select
                  label={t('video_quality')}
                  description={t('video_quality_description')}
                  name="videoQuality"
                  data={[
                    { value: '', label: t('best') },
                    { value: '2160', label: '2160p' },
                    { value: '1440', label: '1440p' },
                    { value: '1080', label: '1080p' },
                    { value: '720', label: '720p' },
                    { value: '480', label: '480p' },
                  ]}
                  value={feed?.videoQuality || ''}
                  onChange={(value) => handleFieldChange('videoQuality', value)}
                />
                <Select
                  label={renderVideoEncodingLabel()}
                  description={t('video_encoding_description')}
                  name="videoEncoding"
                  data={[
                    { value: '', label: t('default') },
                    { value: 'H264', label: 'H.264' },
                    { value: 'H265', label: 'H.265' },
                  ]}
                  value={feed?.videoEncoding || ''}
                  onChange={(value) => handleFieldChange('videoEncoding', value)}
                />
              </>
            )}

            <MultiSelect
              label={t('subtitle_languages')}
              description={t('subtitle_languages_feed_desc')}
              placeholder={t('use_global_settings')}
              value={feed?.subtitleLanguages ? feed.subtitleLanguages.split(',').filter(Boolean) : []}
              onChange={(value) => handleFieldChange('subtitleLanguages', value.length > 0 ? value.join(',') : null)}
              data={SUBTITLE_LANGUAGE_OPTIONS}
              searchable
              clearable
            />

            <Select
              label={t('subtitle_format')}
              description={t('subtitle_format_feed_desc')}
              placeholder={t('use_global_settings')}
              value={feed?.subtitleFormat || ''}
              onChange={(value) => handleFieldChange('subtitleFormat', value || null)}
              data={SUBTITLE_FORMAT_OPTIONS.map((opt) => ({
                ...opt,
                label: opt.value === 'vtt' ? `${opt.label} - ${t('recommended')}` : opt.label,
              }))}
              clearable
            />
          </>
        )}

        {/* Slot for action buttons */}
        {actionButtons}
      </Stack>
    </Modal>
  );
};

export default EditFeedModal;
