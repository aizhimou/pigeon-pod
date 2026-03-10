import './index.css';
import React, { useEffect, useRef, useState } from 'react';
import {
  ActionIcon,
  Anchor,
  Box,
  Center,
  Container,
  Group,
  Loader,
  Slider,
  Stack,
  Text,
  Title,
  useMatches,
} from '@mantine/core';
import {
  IconPlayerPauseFilled,
  IconPlayerPlayFilled,
  IconRewindBackward15,
  IconRewindForward15,
} from '@tabler/icons-react';
import { useColorScheme } from '@mantine/hooks';
import { marked } from 'marked';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { API, formatISODuration } from '../../helpers/index.js';

function createPalette(isDark) {
  return {
    page: isDark ? '#101215' : '#ffffff',
    text: isDark ? '#f3f4f6' : '#1f2328',
    textMuted: isDark ? 'rgba(243,244,246,0.66)' : 'rgba(31,35,40,0.62)',
    border: isDark ? 'rgba(255,255,255,0.12)' : 'rgba(27,31,36,0.14)',
    controlSurface: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(27,31,36,0.06)',
    sliderTrack: isDark ? 'rgba(255,255,255,0.12)' : 'rgba(27,31,36,0.12)',
    sliderBar: isDark ? '#f3f4f6' : '#1f2328',
    thumbRing: isDark ? 'rgba(16,18,21,0.92)' : 'rgba(255,255,255,0.92)',
    mediaBackground: isDark ? '#000000' : '#f5f5f5',
  };
}

function formatClockTime(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) {
    return '00:00';
  }

  const totalSeconds = Math.floor(seconds);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const remainingSeconds = totalSeconds % 60;
  const pad = (value) => value.toString().padStart(2, '0');

  if (hours > 0) {
    return `${pad(hours)}:${pad(minutes)}:${pad(remainingSeconds)}`;
  }

  return `${pad(minutes)}:${pad(remainingSeconds)}`;
}

function formatPublishedAt(publishedAt, language) {
  if (!publishedAt) {
    return '';
  }

  const date = new Date(publishedAt);
  if (Number.isNaN(date.getTime())) {
    return '';
  }

  try {
    return new Intl.DateTimeFormat(language || undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }).format(date);
  } catch {
    return `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date
      .getDate()
      .toString()
      .padStart(2, '0')}`;
  }
}

function resolveDurationText(duration) {
  if (!duration) {
    return '';
  }

  if (duration.startsWith('P')) {
    return formatISODuration(duration);
  }

  return duration;
}

function sanitizeDescriptionHtml(markup) {
  if (!markup) {
    return '';
  }

  return markup
    .replace(/<(script|style|iframe|object|embed|meta|link)[\s\S]*?>[\s\S]*?<\/\1>/gi, '')
    .replace(/\son\w+="[^"]*"/gi, '')
    .replace(/\son\w+='[^']*'/gi, '')
    .replace(/\son\w+=([^\s>]+)/gi, '')
    .replace(/\s(href|src)\s*=\s*(['"])\s*javascript:[^'"]*\2/gi, ' $1="#"');
}

function renderDescription(description) {
  if (!description) {
    return '';
  }

  const rendered = marked.parse(description, {
    breaks: true,
    gfm: true,
  });

  return sanitizeDescriptionHtml(rendered);
}

function BrandFooter({ palette }) {
  const showSloganNewline = useMatches({ base: true, sm: false });
  return (
    <Box style={{ borderTop: `1px solid ${palette.border}` }}>
      <Stack pt="sm" gap="xs">
        <Group justify="center">
          <Anchor
            href="https://pigeonpod.cloud/"
            c={palette.text}
            target="_blank"
            underline="hover"
          >
            <Group gap="0" wrap="nowrap" item-align="center">
              <Box
                component="img"
                src="/pigeonpod.svg"
                alt="PigeonPod"
                style={{
                  height: 32,
                  flexShrink: 0,
                }}
              />
              <Group gap="0">
                <Text fs="italic" fw={600} c={palette.text}>
                  PigeonPod &nbsp;
                </Text>
                {showSloganNewline ? null : (
                  <Text fs="italic" c={palette.text}>
                    - The podcast feed for everything you watch.
                  </Text>
                )}
              </Group>
            </Group>
          </Anchor>
        </Group>
        {showSloganNewline ? (
          <Center>
            <Anchor
              size="sm"
              fs="italic"
              c={palette.text}
              href="https://pigeonpod.cloud/"
              underline="always"
            >
              The podcast feed for everything you watch.
            </Anchor>
          </Center>
        ) : null}
      </Stack>
    </Box>
  );
}

function AudioControls({
  audioRef,
  currentTime,
  durationSeconds,
  episode,
  isPlaying,
  onJump,
  onSeek,
  onTogglePlayback,
  t,
}) {
  const fallbackDuration = resolveDurationText(episode?.duration);
  const totalTimeText =
    durationSeconds > 0 ? formatClockTime(durationSeconds) : fallbackDuration || '00:00';
  const actionIconSize = useMatches({ base: 'md', sm: 'lg' });
  const actionIconGlyphSize = useMatches({ base: 18, sm: 26 });
  const controlsGap = useMatches({ base: 'sm', sm: 'xl' });
  const overlayPadding = useMatches({ base: 4, sm: 8 });
  const glassPaddingX = useMatches({ base: 10, sm: 12 });
  const glassPaddingY = useMatches({ base: 4, sm: 8 });
  const controlSectionGap = useMatches({ base: 1, sm: 3 });
  const timelineGap = useMatches({ base: 2, sm: 4 });
  const controlsOffset = useMatches({ base: -16, sm: -10 });

  return (
    <Box
      pos="absolute"
      inset={0}
      style={{
        display: 'flex',
        alignItems: 'flex-end',
      }}
    >
      <audio ref={audioRef} preload="metadata" src={episode.mediaUrlResolved} />

      <Box w="100%" px={overlayPadding} py={overlayPadding}>
        <Box
          w={{ base: '80%', sm: '40%' }}
          mx="auto"
          px={glassPaddingX}
          py={glassPaddingY}
          style={{
            borderRadius: 'calc(var(--mantine-radius-md)',
            background: 'rgba(12, 14, 18, 0.36)',
            backdropFilter: 'blur(18px) saturate(140%)',
            WebkitBackdropFilter: 'blur(18px) saturate(140%)',
          }}
        >
          <Stack gap={controlSectionGap}>
            <Stack gap={timelineGap}>
              <Slider
                value={durationSeconds > 0 ? currentTime : 0}
                max={durationSeconds > 0 ? durationSeconds : 1}
                min={0}
                step={1}
                onChange={onSeek}
                aria-label={t('share_episode_seek', { defaultValue: 'Seek playback position' })}
                styles={{
                  track: {
                    height: 3,
                  },
                  bar: {
                    background: '#ffffff',
                  },
                  thumb: {
                    width: 10,
                    height: 10,
                    borderWidth: 0,
                    backgroundColor: '#ffffff',
                  },
                }}
              />

              <Group justify="space-between">
                <Text size="xs" c="rgba(255,255,255,0.82)">
                  {formatClockTime(currentTime)}
                </Text>
                <Text size="xs" c="rgba(255,255,255,0.82)">
                  {totalTimeText}
                </Text>
              </Group>
            </Stack>

            <Group justify="center" gap={controlsGap} wrap="nowrap" mt={controlsOffset}>
              <ActionIcon
                size={actionIconSize}
                radius="xl"
                variant="transparent"
                onClick={() => onJump(-15)}
                aria-label={t('share_episode_jump_back', { defaultValue: 'Jump back 15 seconds' })}
                color="white"
              >
                <IconRewindBackward15 size={actionIconGlyphSize} stroke={1.8} />
              </ActionIcon>

              <ActionIcon
                size={actionIconSize}
                radius="xl"
                variant="filled"
                onClick={onTogglePlayback}
                aria-label={
                  isPlaying
                    ? t('pause', { defaultValue: 'Pause' })
                    : t('play', { defaultValue: 'Play' })
                }
                style={{
                  backgroundColor: 'rgba(255,255,255,0.18)',
                  backdropFilter: 'blur(4px)',
                  color: '#ffffff',
                }}
              >
                {isPlaying ? (
                  <IconPlayerPauseFilled size={actionIconGlyphSize} />
                ) : (
                  <IconPlayerPlayFilled size={actionIconGlyphSize} />
                )}
              </ActionIcon>

              <ActionIcon
                size={actionIconSize}
                radius="xl"
                variant="transparent"
                onClick={() => onJump(15)}
                aria-label={t('share_episode_jump_forward', {
                  defaultValue: 'Jump forward 15 seconds',
                })}
                color="white"
              >
                <IconRewindForward15 size={actionIconGlyphSize} stroke={1.8} />
              </ActionIcon>
            </Group>
          </Stack>
        </Box>
      </Box>
    </Box>
  );
}

function ShareEpisode() {
  const { episodeId } = useParams();
  const { t, i18n } = useTranslation();
  const colorScheme = useColorScheme();
  const palette = createPalette(colorScheme === 'dark');
  const audioRef = useRef(null);
  const [episode, setEpisode] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isUnavailable, setIsUnavailable] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [durationSeconds, setDurationSeconds] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);

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

        const mediaUrlResolved = API.defaults.baseURL
          ? `${API.defaults.baseURL}${payload.data.mediaUrl}`
          : payload.data.mediaUrl;

        setEpisode({
          ...payload.data,
          mediaUrlResolved,
          publishedAtText: formatPublishedAt(payload.data.publishedAt, i18n.language),
          renderedDescription: renderDescription(payload.data.description),
        });
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
    document.title = episode?.title ? `${episode.title} | PigeonPod` : 'PigeonPod';
    return () => {
      document.title = 'PigeonPod';
    };
  }, [episode?.title]);

  useEffect(() => {
    setCurrentTime(0);
    setDurationSeconds(0);
    setIsPlaying(false);
  }, [episode?.mediaUrlResolved]);

  useEffect(() => {
    if (!episode || episode.mediaType?.startsWith('video')) {
      return undefined;
    }

    const audio = audioRef.current;
    if (!audio) {
      return undefined;
    }

    function syncDuration() {
      if (Number.isFinite(audio.duration) && audio.duration > 0) {
        setDurationSeconds(audio.duration);
      }
    }

    function syncTime() {
      setCurrentTime(audio.currentTime || 0);
    }

    function handlePlay() {
      setIsPlaying(true);
    }

    function handlePause() {
      setIsPlaying(false);
    }

    function handleEnded() {
      setIsPlaying(false);
      setCurrentTime(audio.duration || 0);
    }

    audio.addEventListener('loadedmetadata', syncDuration);
    audio.addEventListener('durationchange', syncDuration);
    audio.addEventListener('timeupdate', syncTime);
    audio.addEventListener('play', handlePlay);
    audio.addEventListener('pause', handlePause);
    audio.addEventListener('ended', handleEnded);

    syncDuration();
    syncTime();

    return () => {
      audio.removeEventListener('loadedmetadata', syncDuration);
      audio.removeEventListener('durationchange', syncDuration);
      audio.removeEventListener('timeupdate', syncTime);
      audio.removeEventListener('play', handlePlay);
      audio.removeEventListener('pause', handlePause);
      audio.removeEventListener('ended', handleEnded);
    };
  }, [episode]);

  async function togglePlayback() {
    const audio = audioRef.current;
    if (!audio) {
      return;
    }

    if (audio.paused) {
      try {
        await audio.play();
      } catch (error) {
        console.error('Failed to play audio:', error);
      }
      return;
    }

    audio.pause();
  }

  function handleSeek(value) {
    const audio = audioRef.current;
    if (!audio) {
      return;
    }

    audio.currentTime = value;
    setCurrentTime(value);
  }

  function handleJump(seconds) {
    const audio = audioRef.current;
    if (!audio) {
      return;
    }

    const nextTime = Math.min(
      Math.max((audio.currentTime || 0) + seconds, 0),
      audio.duration || Infinity,
    );
    audio.currentTime = nextTime;
    setCurrentTime(nextTime);
  }

  if (isLoading) {
    return (
      <Box mih="100vh" style={{ backgroundColor: palette.page }}>
        <Center mih="100vh">
          <Stack align="center" gap="sm">
            <Loader color={palette.text} />
            <Text c={palette.textMuted}>{t('loading')}</Text>
          </Stack>
        </Center>
      </Box>
    );
  }

  if (isUnavailable || !episode) {
    return (
      <Box mih="100vh" px="md" style={{ backgroundColor: palette.page }}>
        <Center mih="100vh">
          <Stack gap="xl" align="center" w="100%">
            <Stack gap="sm" align="center">
              <Title order={2} ta="center">
                {t('share_episode_unavailable', {
                  defaultValue: 'The shared episode is unavailable.',
                })}
              </Title>
            </Stack>
            <BrandFooter palette={palette} />
          </Stack>
        </Center>
      </Box>
    );
  }

  const coverUrl = episode.coverUrl || '/pigeonpod.svg';
  const isVideo = episode.mediaType?.startsWith('video');

  return (
    <Box mih="100vh" py="xl" style={{ backgroundColor: palette.page }}>
      <Container size="xl">
        <Stack gap="xl">
          <Stack gap="lg">
            {isVideo ? (
              <Box>
                <video
                  controls
                  preload="metadata"
                  poster={coverUrl}
                  src={episode.mediaUrlResolved}
                  style={{
                    width: '100%',
                    borderRadius: 'var(--mantine-radius-md)',
                    backgroundColor: palette.mediaBackground,
                  }}
                />
              </Box>
            ) : (
              <Center>
                <Box
                  pos="relative"
                  style={{
                    width: '100%',
                    overflow: 'hidden',
                    borderRadius: 'var(--mantine-radius-md)',
                  }}
                >
                  <Box
                    component="img"
                    src={coverUrl}
                    alt={episode.title}
                    style={{
                      display: 'block',
                      width: '100%',
                      height: 'auto',
                    }}
                  />
                  <AudioControls
                    audioRef={audioRef}
                    currentTime={currentTime}
                    durationSeconds={durationSeconds}
                    episode={episode}
                    isPlaying={isPlaying}
                    onJump={handleJump}
                    onSeek={handleSeek}
                    onTogglePlayback={togglePlayback}
                    t={t}
                  />
                </Box>
              </Center>
            )}
          </Stack>

          <Stack gap="sm" align="center">
            {episode.sourceUrl ? (
              <Title
                order={3}
                component="a"
                href={episode.sourceUrl}
                target="_blank"
                rel="noreferrer"
                ta="center"
                maw="100%"
                c={palette.text}
                style={{
                  textDecoration: 'none',
                }}
              >
                {episode.title}
              </Title>
            ) : (
              <Title order={3} ta="center" maw="100%" c={palette.text}>
                {episode.title}
              </Title>
            )}

            {episode.publishedAtText ? (
              <Text size="sm" ta="center" c={palette.textMuted}>
                {episode.publishedAtText}
              </Text>
            ) : null}
          </Stack>

          {episode.renderedDescription ? (
            <Box
              className="share-episode-markdown"
              style={{
                color: palette.text,
                '--share-episode-text': palette.text,
                '--share-episode-muted': palette.textMuted,
                '--share-episode-border': palette.border,
              }}
              dangerouslySetInnerHTML={{ __html: episode.renderedDescription }}
            />
          ) : null}

          <BrandFooter palette={palette} />
        </Stack>
      </Container>
    </Box>
  );
}

export default ShareEpisode;
