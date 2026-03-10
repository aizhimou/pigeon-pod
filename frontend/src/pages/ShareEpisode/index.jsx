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
} from '@mantine/core';
import {
  IconPlayerPauseFilled,
  IconPlayerPlayFilled,
  IconRewindBackward15,
  IconRewindForward15,
  IconVolume,
  IconVolumeOff,
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

function AudioControls({
  audioRef,
  currentTime,
  durationSeconds,
  episode,
  isMuted,
  isPlaying,
  onJump,
  onSeek,
  onToggleMute,
  onTogglePlayback,
  palette,
  t,
}) {
  const fallbackDuration = resolveDurationText(episode?.duration);
  const totalTimeText = durationSeconds > 0 ? formatClockTime(durationSeconds) : fallbackDuration || '00:00';

  return (
    <Box mx="lg">
      <audio ref={audioRef} preload="metadata" src={episode.mediaUrlResolved} />

      <Stack gap="xs">
        <Stack gap="xs">
          <Slider
            value={durationSeconds > 0 ? currentTime : 0}
            max={durationSeconds > 0 ? durationSeconds : 1}
            min={0}
            step={1}
            onChange={onSeek}
            aria-label={t('share_episode_seek', { defaultValue: 'Seek playback position' })}
            color={palette.sliderBar}
            styles={{
              track: {
                height: 8,
                backgroundColor: palette.sliderTrack,
              },
              bar: {
                background: palette.sliderBar,
              },
              thumb: {
                width: 18,
                height: 18,
                borderWidth: 0,
                boxShadow: `0 0 0 6px ${palette.thumbRing}`,
                backgroundColor: palette.sliderBar,
              },
            }}
          />

          <Group justify="space-between">
            <Text size="sm" fw={600} c={palette.textMuted}>
              {formatClockTime(currentTime)}
            </Text>
            <Text size="sm" fw={600} c={palette.textMuted}>
              {totalTimeText}
            </Text>
          </Group>
        </Stack>

        <Group justify="center" gap="xl" wrap="nowrap">
          <ActionIcon
            size="xl"
            radius="xl"
            variant="default"
            onClick={() => onJump(-15)}
            aria-label={t('share_episode_jump_back', { defaultValue: 'Jump back 15 seconds' })}
          >
            <IconRewindBackward15 size={32} stroke={1.8} />
          </ActionIcon>

          <ActionIcon
              size="xl"
              radius="xl"
              variant="default"
            onClick={onTogglePlayback}
            aria-label={isPlaying ? t('pause', { defaultValue: 'Pause' }) : t('play', { defaultValue: 'Play' })}
          >
            {isPlaying ? <IconPlayerPauseFilled size={32} /> : <IconPlayerPlayFilled size={32} />}
          </ActionIcon>

          <ActionIcon
              size="xl"
              radius="xl"
              variant="default"
            onClick={() => onJump(15)}
            aria-label={t('share_episode_jump_forward', { defaultValue: 'Jump forward 15 seconds' })}
          >
            <IconRewindForward15 size={32} stroke={1.8} />
          </ActionIcon>
        </Group>
      </Stack>
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
  const [isMuted, setIsMuted] = useState(false);

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
    setIsMuted(false);
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

    function syncMute() {
      setIsMuted(Boolean(audio.muted || audio.volume === 0));
    }

    audio.addEventListener('loadedmetadata', syncDuration);
    audio.addEventListener('durationchange', syncDuration);
    audio.addEventListener('timeupdate', syncTime);
    audio.addEventListener('play', handlePlay);
    audio.addEventListener('pause', handlePause);
    audio.addEventListener('ended', handleEnded);
    audio.addEventListener('volumechange', syncMute);

    syncDuration();
    syncTime();
    syncMute();

    return () => {
      audio.removeEventListener('loadedmetadata', syncDuration);
      audio.removeEventListener('durationchange', syncDuration);
      audio.removeEventListener('timeupdate', syncTime);
      audio.removeEventListener('play', handlePlay);
      audio.removeEventListener('pause', handlePause);
      audio.removeEventListener('ended', handleEnded);
      audio.removeEventListener('volumechange', syncMute);
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

    const nextTime = Math.min(Math.max((audio.currentTime || 0) + seconds, 0), audio.duration || Infinity);
    audio.currentTime = nextTime;
    setCurrentTime(nextTime);
  }

  function toggleMute() {
    const audio = audioRef.current;
    if (!audio) {
      return;
    }

    audio.muted = !audio.muted;
    setIsMuted(audio.muted);
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
          <Stack gap="sm" align="center">
            <Box
              component="img"
              src="/pigeonpod.svg"
              alt="PigeonPod"
              style={{
                width: 72,
                height: 72,
              }}
            />
            <Title
              order={2}
              ta="center"
              c={palette.text}
              component="a"
              href="https://pigeonpod.cloud/"
              style={{
                textDecoration: 'none',
                color: palette.text,
              }}
            >
              PigeonPod
            </Title>
            <Text ta="center" c={palette.textMuted}>
              {t('share_episode_unavailable', { defaultValue: 'The shared episode is unavailable.' })}
            </Text>
            <Anchor
              href="https://pigeonpod.cloud/"
              underline="always"
              style={{ textAlign: 'center' }}
            >
              The podcast feed for everything you watch.
            </Anchor>
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
                  component="img"
                  src={coverUrl}
                  alt={episode.title}
                  style={{
                    display: 'block',
                    width: '100%',
                    height: 'auto',
                    borderRadius: 'var(--mantine-radius-md)',
                  }}
                />
              </Center>
            )}

            {!isVideo ? (
              <AudioControls
                audioRef={audioRef}
                currentTime={currentTime}
                durationSeconds={durationSeconds}
                episode={episode}
                isMuted={isMuted}
                isPlaying={isPlaying}
                onJump={handleJump}
                onSeek={handleSeek}
                onToggleMute={toggleMute}
                onTogglePlayback={togglePlayback}
                palette={palette}
                t={t}
              />
            ) : null}
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
              <Title
                order={3}
                ta="center"
                maw="100%"
                c={palette.text}
              >
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
        </Stack>
      </Container>
    </Box>
  );
}

export default ShareEpisode;
