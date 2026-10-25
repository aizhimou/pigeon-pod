import React, { useEffect, useMemo, useState } from 'react';
import {
  AspectRatio,
  Button,
  FileInput,
  Grid,
  Group,
  Image,
  Modal,
  Stack,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { IconArrowBackUp, IconPhotoUp } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { API, showError, showSuccess } from '../helpers';
import { episodeCoverSrc } from '../helpers/episodeDisplay';

function ResetButton({ visible, label, onClick }) {
  if (!visible) return null;
  return (
    <Button
      size="compact-xs"
      variant="subtle"
      color="gray"
      leftSection={<IconArrowBackUp size={14} />}
      onClick={onClick}
    >
      {label}
    </Button>
  );
}

/**
 * Edits one episode's title, description and thumbnail. Fields start from what the episode
 * shows now; saving text equal to the original removes the override on the server.
 */
const EpisodeEditModal = ({ opened, onClose, episode, onSaved }) => {
  const { t } = useTranslation();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [coverFile, setCoverFile] = useState(null);
  const [isSaving, setIsSaving] = useState(false);

  // Reset the form only when a dialog opens for an episode, not when that episode is refreshed
  // mid-edit (e.g. after switching back to the original thumbnail).
  const episodeId = episode?.id;
  useEffect(() => {
    if (!opened || !episode) return;
    setTitle(episode.customTitle || episode.title || '');
    setDescription(episode.customDescription || episode.description || '');
    setCoverFile(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [opened, episodeId]);

  const coverPreview = useMemo(
    () => (coverFile ? URL.createObjectURL(coverFile) : episodeCoverSrc(episode)),
    [coverFile, episode],
  );

  useEffect(() => {
    if (!coverFile) return undefined;
    return () => URL.revokeObjectURL(coverPreview);
  }, [coverFile, coverPreview]);

  if (!episode) return null;

  const resetLabel = t('episode_reset_to_original', { defaultValue: 'Reset to original' });
  const isTitleEdited = title.trim() !== (episode.title || '').trim();
  const isDescriptionEdited = description.trim() !== (episode.description || '').trim();

  const handleSave = async () => {
    setIsSaving(true);
    try {
      const textRes = await API.put(`/api/episode/${episode.id}/custom`, {
        customTitle: title,
        customDescription: description,
      });
      if (textRes?.data?.code !== 200) {
        showError(textRes?.data?.msg || t('update_feed_failed'));
        return;
      }
      let updated = textRes.data.data;
      if (coverFile) {
        const formData = new FormData();
        formData.append('file', coverFile);
        const coverRes = await API.post(`/api/episode/${episode.id}/cover`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        if (coverRes?.data?.code !== 200) {
          showError(coverRes?.data?.msg || t('update_feed_failed'));
          return;
        }
        updated = coverRes.data.data;
      }
      onSaved(updated);
      showSuccess(t('episode_edit_saved', { defaultValue: 'Episode updated' }));
      onClose();
    } finally {
      setIsSaving(false);
    }
  };

  const handleUseOriginalCover = async () => {
    const res = await API.delete(`/api/episode/${episode.id}/cover`);
    if (res?.data?.code !== 200) {
      showError(res?.data?.msg || t('update_feed_failed'));
      return;
    }
    setCoverFile(null);
    onSaved(res.data.data);
  };

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={t('edit_episode', { defaultValue: 'Edit episode' })}
      size="lg"
    >
      <Stack>
        <Text size="xs" c="dimmed">
          {t('episode_edit_hint', {
            defaultValue:
              'Changes show in podcast apps and in PigeonPod. The original stays saved, so you can reset anytime.',
          })}
        </Text>

        <Grid align="flex-start">
          <Grid.Col span={{ base: 12, sm: 5 }}>
            <AspectRatio ratio={16 / 9}>
              <Image
                src={coverPreview}
                alt={title}
                radius="md"
                fit="contain"
                bg="var(--mantine-color-default-hover)"
                referrerPolicy="no-referrer"
              />
            </AspectRatio>
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 7 }}>
            <Stack gap="xs">
              <FileInput
                label={t('episode_thumbnail', { defaultValue: 'Thumbnail' })}
                placeholder={t('episode_replace_thumbnail', {
                  defaultValue: 'Upload a new thumbnail',
                })}
                leftSection={<IconPhotoUp size={16} />}
                accept="image/jpeg,image/png,image/webp"
                value={coverFile}
                onChange={setCoverFile}
                clearable
              />
              {episode.customCoverExt && !coverFile ? (
                <Group>
                  <ResetButton
                    visible
                    label={t('episode_use_original_thumbnail', {
                      defaultValue: 'Use original thumbnail',
                    })}
                    onClick={handleUseOriginalCover}
                  />
                </Group>
              ) : null}
            </Stack>
          </Grid.Col>
        </Grid>

        <Stack gap={4}>
          <TextInput
            label={t('episode_title', { defaultValue: 'Title' })}
            value={title}
            placeholder={episode.title}
            onChange={(event) => setTitle(event.currentTarget.value)}
          />
          <Group justify="flex-end">
            <ResetButton
              visible={isTitleEdited}
              label={resetLabel}
              onClick={() => setTitle(episode.title || '')}
            />
          </Group>
        </Stack>

        <Stack gap={4}>
          <Textarea
            label={t('episode_description', { defaultValue: 'Description' })}
            value={description}
            placeholder={episode.description || t('no_description_available')}
            onChange={(event) => setDescription(event.currentTarget.value)}
            autosize
            minRows={4}
            maxRows={12}
          />
          <Group justify="flex-end">
            <ResetButton
              visible={isDescriptionEdited}
              label={resetLabel}
              onClick={() => setDescription(episode.description || '')}
            />
          </Group>
        </Stack>

        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={onClose}>
            {t('cancel')}
          </Button>
          <Button onClick={handleSave} loading={isSaving}>
            {t('save', { defaultValue: 'Save' })}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
};

export default EpisodeEditModal;
