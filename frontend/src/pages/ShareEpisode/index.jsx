import React, { useEffect, useState } from 'react';
import {
  Anchor,
  AspectRatio,
  Box,
  Card,
  Center,
  Container,
  Image,
  Loader,
  Paper,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { useParams } from 'react-router-dom';
import { API } from '../../helpers/index.js';
import { useTranslation } from 'react-i18next';

function ShareEpisode() {
  const { episodeId } = useParams();
  const { t, i18n } = useTranslation();
  const [episode, setEpisode] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isUnavailable, setIsUnavailable] = useState(false);

  useEffect(() => {
    let isMounted = true;

    async function loadEpisode() {
      if (!episodeId) {
        if (!isMounted) return;
        setIsUnavailable(true);
        setIsLoading(false);
        return;
      }

      setIsLoading(true);
      setIsUnavailable(false);
      setEpisode(null);

      try {
        const response = await fetch(`/api/public/episode/${encodeURIComponent(episodeId)}`, {
          headers: {
            'Accept-Language': i18n.language,
          },
        });

        if (response.status === 404) {
          if (!isMounted) return;
          setIsUnavailable(true);
          return;
        }

        if (!response.ok) {
          throw new Error(`Unexpected status: ${response.status}`);
        }

        const payload = await response.json();
        if (!isMounted) return;

        if (payload?.code !== 200 || !payload?.data) {
          setIsUnavailable(true);
          return;
        }

        setEpisode(payload.data);
      } catch (error) {
        console.error('Failed to load shared episode:', error);
        if (!isMounted) return;
        setIsUnavailable(true);
      } finally {
        if (!isMounted) return;
        setIsLoading(false);
      }
    }

    loadEpisode().then();

    return () => {
      isMounted = false;
    };
  }, [episodeId, i18n.language]);

  useEffect(() => {
    if (episode?.title) {
      document.title = `${episode.title} | PigeonPod`;
      return () => {
        document.title = 'PigeonPod';
      };
    }

    document.title = 'PigeonPod';
    return () => {
      document.title = 'PigeonPod';
    };
  }, [episode?.title]);

  if (isLoading) {
    return (
      <Box
        mih="100vh"
        style={{
          background:
            'linear-gradient(180deg, rgba(247,244,233,1) 0%, rgba(255,255,255,1) 45%, rgba(239,246,252,1) 100%)',
        }}
      >
        <Center mih="100vh">
          <Stack align="center" gap="sm">
            <Loader color="orange" />
            <Text c="dimmed">{t('loading')}</Text>
          </Stack>
        </Center>
      </Box>
    );
  }

  if (isUnavailable || !episode) {
    return (
      <Box
        mih="100vh"
        style={{
          background:
            'linear-gradient(180deg, rgba(247,244,233,1) 0%, rgba(255,255,255,1) 45%, rgba(239,246,252,1) 100%)',
        }}
      >
        <Center mih="100vh" px="md">
          <Paper shadow="sm" radius="xl" p="xl" maw={520} withBorder>
            <Stack gap="sm" align="center">
              <Title order={2} ta="center">
                PigeonPod
              </Title>
              <Text ta="center" c="dimmed">
                {t('share_episode_unavailable', { defaultValue: 'The shared episode is unavailable.' })}
              </Text>
            </Stack>
          </Paper>
        </Center>
      </Box>
    );
  }

  const mediaUrl = API.defaults.baseURL
    ? `${API.defaults.baseURL}${episode.mediaUrl}`
    : episode.mediaUrl;
  const isVideo = episode.mediaType?.startsWith('video');

  return (
    <Box
      mih="100vh"
      py={{ base: 'xl', sm: 48 }}
      style={{
        background:
          'linear-gradient(180deg, rgba(247,244,233,1) 0%, rgba(255,255,255,1) 45%, rgba(239,246,252,1) 100%)',
      }}
    >
      <Container size="sm">
        <Card shadow="md" radius="xl" p={{ base: 'md', sm: 'xl' }} withBorder>
          <Stack gap="lg">
            <AspectRatio ratio={1}>
              <Image
                src={episode.coverUrl || '/pigeonpod.svg'}
                alt={episode.title}
                radius="lg"
                referrerPolicy="no-referrer"
              />
            </AspectRatio>

            <Stack gap="xs">
              <Title order={1} lh={1.15}>
                {episode.title}
              </Title>

              {episode.sourceUrl ? (
                <Text size="sm" c="dimmed">
                  {t('share_episode_source_label', { defaultValue: 'Source URL:' })}{' '}
                  <Anchor href={episode.sourceUrl} target="_blank" rel="noreferrer">
                    {episode.sourceUrl}
                  </Anchor>
                </Text>
              ) : null}
            </Stack>

            {episode.description ? (
              <Text
                size="sm"
                style={{
                  whiteSpace: 'pre-wrap',
                  lineHeight: 1.7,
                }}
              >
                {episode.description}
              </Text>
            ) : null}

            <Box>
              {isVideo ? (
                <video
                  controls
                  preload="metadata"
                  poster={episode.coverUrl || '/pigeonpod.svg'}
                  src={mediaUrl}
                  style={{ width: '100%', borderRadius: '16px', backgroundColor: '#000' }}
                />
              ) : (
                <audio controls preload="metadata" src={mediaUrl} style={{ width: '100%' }} />
              )}
            </Box>
          </Stack>
        </Card>
      </Container>
    </Box>
  );
}

export default ShareEpisode;
