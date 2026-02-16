import React, { useEffect, useState } from 'react';
import {
  API,
  formatDateWithPattern,
  formatISODuration,
  showError,
  showSuccess,
} from '../../helpers';
import {
  Container,
  Button,
  Card,
  Grid,
  Group,
  Input,
  ActionIcon,
  Image,
  Text,
  Anchor,
  Modal,
  Stack,
  Center,
  Box,
  Alert,
  NumberInput,
  rem,
} from '@mantine/core';
import { useTranslation } from 'react-i18next';
import {
  IconCheck,
  IconSearch,
  IconSettings,
  IconClockHour4,
  IconDownload,
  IconCircleCheck,
  IconAlertCircle,
} from '@tabler/icons-react';
import { useNavigate } from 'react-router-dom';
import { useDisclosure, useMediaQuery } from '@mantine/hooks';
import VersionUpdateAlert from '../../components/VersionUpdateAlert';
import EditFeedModal from '../../components/EditFeedModal';
import FeedCard from '../../components/FeedCard/FeedCard.jsx';
import { useDateFormat } from '../../hooks/useDateFormat.js';
import FeedHeader from '../../components/FeedHeader';
import StatisticsCard from '../../components/StatisticsCard/StatisticsCard.jsx';

const INVALID_SOURCE_MESSAGE_PATTERNS = [
  'Invalid YouTube channel URL',
  'Invalid YouTube playlist URL',
  '无效的YouTube频道URL',
  '无效的YouTube播放列表URL',
  'URL de canal de YouTube inválida',
  'URL de lista de reproducción de YouTube inválida',
  'URL do canal do YouTube inválida',
  'URL de playlist do YouTube inválida',
  '無効なYouTubeチャンネルURLです',
  '無効なYouTubeプレイリストURLです',
  'URL de chaîne YouTube invalide',
  'URL de playlist YouTube invalide',
  'Ungültige YouTube-Kanal-URL',
  'Ungültige YouTube-Playlist-URL',
  '유효하지 않은 YouTube 채널 URL입니다',
  '유효하지 않은 YouTube 플레이리스트 URL입니다',
];

function isValidFeedSource(source) {
  if (!source) {
    return false;
  }

  const trimmed = source.trim();
  if (!trimmed) {
    return false;
  }

  const isYouTubeHandleUrl =
    /^https?:\/\/(?:www\.|m\.)?youtube\.com\/@[^/?#\s]+(?:[/?#].*)?$/i.test(trimmed);
  const isYouTubeChannelUrl =
    /^https?:\/\/(?:www\.|m\.)?youtube\.com\/channel\/UC[A-Za-z0-9_-]{22}(?:[/?#].*)?$/i.test(
      trimmed,
    );
  const isYouTubeChannelId = /^UC[A-Za-z0-9_-]{22}$/.test(trimmed);
  const isYouTubePlaylistUrl =
    /^https?:\/\/(?:www\.|m\.)?youtube\.com\/(?:playlist|watch)\?(?:[^#]*&)?list=[A-Za-z0-9_-]{13,64}(?:[&#].*)?$/i.test(
      trimmed,
    );
  const isYouTubePlaylistId = /^(PL|UU|OL|LL)[A-Za-z0-9_-]{10,}$/i.test(trimmed);

  const isBilibiliSpaceUrl = /^https?:\/\/space\.bilibili\.com\/\d+(?:[/?#].*)?$/i.test(trimmed);
  const isBilibiliMid = /^\d+$/.test(trimmed);
  const isBilibiliChannelId = /^bili-mid-\d+$/i.test(trimmed);
  const isBilibiliPlaylistUrl =
    /^https?:\/\/space\.bilibili\.com\/\d+\/lists\/\d+(?:\?.*)?$/i.test(trimmed) &&
    /(?:\?|&)type=(season|series)(?:&|$)/i.test(trimmed);
  const isBilibiliPlaylistId = /^bili-(season|series)-\d+$/i.test(trimmed);
  const isBilibiliLegacyPlaylistId = /^(season|series):\d+(?::\d+)?$/i.test(trimmed);

  return (
    isYouTubeHandleUrl ||
    isYouTubeChannelUrl ||
    isYouTubeChannelId ||
    isYouTubePlaylistUrl ||
    isYouTubePlaylistId ||
    isBilibiliSpaceUrl ||
    isBilibiliMid ||
    isBilibiliChannelId ||
    isBilibiliPlaylistUrl ||
    isBilibiliPlaylistId ||
    isBilibiliLegacyPlaylistId
  );
}

function shouldShowSourceFormatModal(message) {
  if (!message) {
    return false;
  }

  const normalizedMessage = String(message).toLowerCase();
  const hasKnownInvalidMessage = INVALID_SOURCE_MESSAGE_PATTERNS.some((pattern) =>
    normalizedMessage.includes(pattern.toLowerCase()),
  );
  if (hasKnownInvalidMessage) {
    return true;
  }

  return (
    normalizedMessage.includes('cannot resolve bilibili') ||
    normalizedMessage.includes('unsupported bilibili playlist type') ||
    normalizedMessage.includes('bilibili channel source is empty') ||
    normalizedMessage.includes('bilibili playlist source is empty')
  );
}

const Home = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const dateFormat = useDateFormat();
  const isSmallScreen = useMediaQuery('(max-width: 36em)');
  const [feedSource, setFeedSource] = useState('');
  const [fetchFeedLoading, setFetchFeedLoading] = useState(false);
  const [filterLoading, setFilterLoading] = useState(false);
  const [addFeedLoading, setAddFeedLoading] = useState(false);
  const [feed, setFeed] = useState({});
  const [episodes, setEpisodes] = useState([]);
  const [feeds, setFeeds] = useState([]);
  const [preview, setPreview] = useState(false);
  const [opened, { open, close }] = useDisclosure(false);
  const [invalidSourceOpened, { open: openInvalidSourceModal, close: closeInvalidSourceModal }] =
    useDisclosure(false);
  const [sourceFormatModalScene, setSourceFormatModalScene] = useState('guide');
  const [editConfigOpened, { open: openEditConfig, close: closeEditConfig }] = useDisclosure(false);
  const isPlaylistFeed = String(feed?.type || '').toLowerCase() === 'playlist';
  const [statistics, setStatistics] = useState({
    pendingCount: 0,
    downloadingCount: 0,
    completedCount: 0,
    failedCount: 0,
  });
  const [youtubeQuotaToday, setYoutubeQuotaToday] = useState(null);

  const fetchFeeds = async () => {
    const res = await API.get('/api/feed/list');
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      return;
    }
    setFeeds(data);
  };

  const fetchStatistics = async () => {
    try {
      const res = await API.get('/api/dashboard/statistics');
      const { code, data } = res.data;
      if (code === 200) {
        setStatistics(data);
      }
    } catch (error) {
      // Silently fail if statistics endpoint is not available
      console.error('Failed to fetch statistics:', error);
    }
  };

  const fetchYoutubeQuotaToday = async () => {
    try {
      const res = await API.get('/api/account/youtube-quota/today');
      const { code, data } = res.data;
      if (code === 200) {
        setYoutubeQuotaToday(data);
      }
    } catch (error) {
      console.error('Failed to fetch YouTube quota:', error);
    }
  };

  const goToFeedDetail = (type, feedId) => {
    const normalizedType = String(type || 'CHANNEL').toLowerCase();
    navigate(`/${normalizedType}/${feedId}`);
  };

  const openSourceFormatGuideModal = () => {
    setSourceFormatModalScene('guide');
    openInvalidSourceModal();
  };

  const openSourceFormatResultModal = () => {
    setSourceFormatModalScene('result');
    openInvalidSourceModal();
  };

  const fetchFeed = async () => {
    if (!feedSource) {
      showError(t('please_enter_valid_feed_url'));
      return;
    }
    const normalizedFeedSource = feedSource.trim();
    if (!isValidFeedSource(normalizedFeedSource)) {
      openSourceFormatGuideModal();
      return;
    }

    setFetchFeedLoading(true);
    const res = await API.post('/api/feed/fetch', {
      source: normalizedFeedSource,
    });
    const { code, msg, data } = res.data;
    if (code !== 200) {
      if (shouldShowSourceFormatModal(msg)) {
        openSourceFormatGuideModal();
      } else {
        showError(msg);
      }
      setFetchFeedLoading(false);
      return;
    }

    open();

    setFeed(data.feed);
    setEpisodes(data.episodes || []);

    setFetchFeedLoading(false);
    setFeedSource(''); // Clear the input field after successful addition
  };

  const addFeed = async () => {
    const currentType = String(feed?.type || 'CHANNEL').toLowerCase();
    setAddFeedLoading(true);
    const res = await API.post(`/api/feed/${currentType}/add`, feed);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      setAddFeedLoading(false);
      return;
    }

    showSuccess(data.message);

    // Add the new feed at the beginning of the feeds list
    setFeeds((prevFeeds) => [data.feed, ...prevFeeds]);
    setFeed(data.feed);

    setAddFeedLoading(false);
    close();
  };

  const previewFeed = async () => {
    if (!preview) {
      closeEditConfig();
      return;
    }
    setFilterLoading(true);
    const currentType = String(feed?.type || 'CHANNEL').toLowerCase();
    const res = await API.post(`/api/feed/${currentType}/preview`, feed);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      setFilterLoading(false);
      return;
    }
    setFeed(data.feed || feed);
    setEpisodes(data.episodes || []);
    setFilterLoading(false);
    closeEditConfig();
  };

  useEffect(() => {
    fetchFeeds().then();
    fetchStatistics().then();
    fetchYoutubeQuotaToday().then();

    // Set up polling for statistics every 3 seconds
    const statisticsInterval = setInterval(() => {
      fetchStatistics();
    }, 3000);
    const quotaInterval = setInterval(() => {
      fetchYoutubeQuotaToday();
    }, 30000);

    // Cleanup interval on component unmount
    return () => {
      clearInterval(statisticsInterval);
      clearInterval(quotaInterval);
    };
  }, []);

  const openStatusDetail = (status) => {
    const normalized = String(status || '').toLowerCase();
    navigate(`/dashboard/episodes/${normalized}`);
  };

  const modalActions = [
    {
      key: 'config',
      label: t('config'),
      color: 'orange',
      leftSectionDesktop: <IconSettings size={16} />,
      leftSectionMobile: <IconSettings size={14} />,
      onClick: openEditConfig,
      sizeDesktop: isSmallScreen ? 'compact-xs' : 'xs',
      sizeMobile: 'xs',
    },
    {
      key: 'confirm',
      label: t('confirm'),
      leftSectionDesktop: <IconCheck size={16} />,
      leftSectionMobile: <IconCheck size={14} />,
      onClick: addFeed,
      loading: addFeedLoading,
      sizeDesktop: isSmallScreen ? 'compact-xs' : 'xs',
      sizeMobile: 'xs',
    },
  ];
  const supportedSourceFormats = [
    {
      key: 'youtube_channel_id',
      label: t('feed_source_format_youtube_channel_id'),
      examples: [
        {
          label: t('feed_source_example_id'),
          value: 'UCSJ4gkVC6NrvII8umztf0Ow',
        },
      ],
      tip: t('feed_source_format_youtube_channel_id_tip'),
    },
    {
      key: 'youtube_channel_url',
      label: t('feed_source_format_youtube_channel_url'),
      examples: [
        {
          label: t('feed_source_example_handler_url'),
          value: 'https://www.youtube.com/@LofiGirl',
        },
        {
          label: t('feed_source_example_channel_url'),
          value: 'https://www.youtube.com/channel/UCSJ4gkVC6NrvII8umztf0Ow',
        },
      ],
    },
    {
      key: 'youtube_playlist_url',
      label: t('feed_source_format_youtube_playlist_url'),
      examples: [
        {
          label: t('feed_source_example_playlist_url'),
          value: 'https://www.youtube.com/playlist?list=PLFgquLnL59anNXuf1M87FT1O169Qt6-Lp',
        },
        {
          label: t('feed_source_example_playlist_id'),
          value: 'PLFgquLnL59anNXuf1M87FT1O169Qt6-Lp',
        },
      ],
    },
    {
      key: 'bilibili_space_url',
      label: t('feed_source_format_bilibili_space_url'),
      examples: [
        {
          label: t('feed_source_example_bilibili_up_home'),
          value: 'https://space.bilibili.com/349436355',
        },
        {
          label: t('feed_source_example_bilibili_up_id'),
          value: '349436355',
        },
      ],
    },
    {
      key: 'bilibili_playlist_url',
      label: t('feed_source_format_bilibili_playlist_url'),
      examples: [
        {
          label: t('feed_source_example_bilibili_series_url'),
          value: 'https://space.bilibili.com/349436355/lists/1766638?type=series',
        },
        {
          label: t('feed_source_example_bilibili_season_url'),
          value: 'https://space.bilibili.com/290526283/lists/6842432?type=season',
        },
      ],
    },
  ];

  return (
    <Container size="lg" mt="lg">
      <VersionUpdateAlert />
      {youtubeQuotaToday?.warningReached ? (
        <Alert color="red" variant="light" mb="md" icon={<IconAlertCircle size={18} />}>
          <Text size="sm">
            {youtubeQuotaToday.autoSyncBlocked
              ? t('home_youtube_quota_blocked', {
                  defaultValue:
                    'YouTube API daily limit has been reached ({{used}} / {{limit}}). Auto sync is stopped for today and will resume tomorrow.',
                  used: youtubeQuotaToday.usedUnits ?? 0,
                  limit:
                    youtubeQuotaToday.dailyLimitUnits ??
                    t('youtube_daily_limit_unlimited', { defaultValue: 'Unlimited' }),
                })
              : t('home_youtube_quota_warning', {
                  defaultValue:
                    'YouTube API usage is {{used}} / {{limit}} (>=80%). Once the daily limit is reached, auto sync will stop for today and resume tomorrow.',
                  used: youtubeQuotaToday.usedUnits ?? 0,
                  limit:
                    youtubeQuotaToday.dailyLimitUnits ??
                    t('youtube_daily_limit_unlimited', { defaultValue: 'Unlimited' }),
                })}
          </Text>
        </Alert>
      ) : null}

      {/* Dashboard Statistics Cards */}
      <Grid gutter="md" mb="lg">
        <Grid.Col span={{ base: 6, sm: 3 }}>
          <StatisticsCard
            label={t('dashboard_pending')}
            count={statistics.pendingCount}
            icon={<IconClockHour4 />}
            color="gray"
            onClick={() => openStatusDetail('PENDING')}
          />
        </Grid.Col>

        <Grid.Col span={{ base: 6, sm: 3 }}>
          <StatisticsCard
            label={t('dashboard_downloading')}
            count={statistics.downloadingCount}
            icon={<IconDownload />}
            color="blue"
            onClick={() => openStatusDetail('DOWNLOADING')}
          />
        </Grid.Col>

        <Grid.Col span={{ base: 6, sm: 3 }}>
          <StatisticsCard
            label={t('dashboard_completed')}
            count={statistics.completedCount}
            icon={<IconCircleCheck />}
            color="green"
            onClick={() => openStatusDetail('COMPLETED')}
          />
        </Grid.Col>

        <Grid.Col span={{ base: 6, sm: 3 }}>
          <StatisticsCard
            label={t('dashboard_failed')}
            count={statistics.failedCount}
            icon={<IconAlertCircle />}
            color="red"
            onClick={() => openStatusDetail('FAILED')}
          />
        </Grid.Col>
      </Grid>

      <Group pos="relative" wrap="wrap" gap="sm">
        <Input
          leftSection={<IconSearch size={16} />}
          rightSection={
            <ActionIcon
              variant="subtle"
              color="gray"
              size="sm"
              radius="xl"
              onClick={openSourceFormatGuideModal}
              aria-label={t('feed_source_result_not_expected')}
              title={t('feed_source_result_not_expected')}
            >
              ?
            </ActionIcon>
          }
          rightSectionPointerEvents="all"
          placeholder={t('enter_feed_source_url')}
          name="feedSource"
          value={feedSource}
          onChange={(e) => setFeedSource(decodeURIComponent(e.target.value))}
          style={{ flex: 1, minWidth: isSmallScreen ? '100%' : 0 }}
        />
        <Button
          onClick={fetchFeed}
          loading={fetchFeedLoading}
          variant="gradient"
          gradient={{ from: '#ae2140', to: '#f28b96', deg: 10 }}
          fullWidth={isSmallScreen}
        >
          {t('new_feed')}
        </Button>
      </Group>

      <Grid mt={isSmallScreen ? 'md' : 'lg'}>
        {feeds.length > 0 ? (
          feeds.map((feedItem) => {
            const isAutoDownloadEnabled = feedItem?.autoDownloadEnabled !== false;
            const pausedTooltip = t('auto_download_paused_tooltip');

            return (
              <FeedCard
                key={feedItem.id}
                feed={feedItem}
                onClick={() => goToFeedDetail(feedItem.type, feedItem.id)}
                dimmed={!isAutoDownloadEnabled}
                withTooltip={!isAutoDownloadEnabled}
                tooltipLabel={pausedTooltip}
              />
            );
          })
        ) : (
          <Grid.Col span={12}>
            <Text align="center" c="dimmed" size="lg">
              {t('no_feeds_available')}
            </Text>
          </Grid.Col>
        )}
      </Grid>

      <Modal
        opened={opened}
        onClose={close}
        withCloseButton
        title={t('subscription_configuration')}
        size={'xl'}
        fullScreen={isSmallScreen}
        closeOnEscape={!editConfigOpened}
      >
        <Stack gap="xs">
          <FeedHeader
            feed={feed}
            isSmallScreen={isSmallScreen}
            actions={modalActions}
            avatarSizeLarge={160}
            descriptionClampSmall={2}
            descriptionClampLarge={2}
          />
          <Anchor
            component="button"
            type="button"
            size="sm"
            onClick={openSourceFormatResultModal}
            style={{ alignSelf: 'flex-start' }}
            mt={-25}
          >
            {t('feed_source_result_not_expected')}
          </Anchor>
          <Box>
            {episodes.length === 0 ? (
              <Center py="xl">
                <Text c="dimmed">{t('no_episodes_found')}</Text>
              </Center>
            ) : (
              <Stack>
                {episodes.map((episode) => (
                  <Card key={episode.id} padding="md" radius="md" withBorder>
                    <Grid align="flex-start">
                      {/* Episode thumbnail */}
                      <Grid.Col span={{ base: 12, sm: 3 }} style={{ alignSelf: 'flex-start' }}>
                        <Image
                          src={episode.maxCoverUrl || episode.defaultCoverUrl}
                          alt={episode.title}
                          referrerPolicy="no-referrer"
                          radius="md"
                          w="100%"
                          h={{ base: rem(120), sm: '100%' }}
                          fit="cover"
                        />
                      </Grid.Col>

                      {/* Episode details */}
                      <Grid.Col span={{ base: 12, sm: 9 }}>
                        <Text
                          fw={600}
                          style={{
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                          }}
                          title={episode.title}
                        >
                          {episode.title}
                        </Text>
                        <Text size="sm" lineClamp={2} style={{ minHeight: '2rem' }}>
                          {episode.description
                            ? episode.description
                            : t('no_description_available')}
                        </Text>
                        <Group mt="xs" justify="space-between">
                          <Text size="sm" c="dimmed">
                            {episode.publishedAt
                              ? formatDateWithPattern(episode.publishedAt, dateFormat)
                              : t('unknown_date')}
                          </Text>
                          <Text c="dimmed" size="sm">
                            {formatISODuration(episode.duration)}
                          </Text>
                        </Group>
                      </Grid.Col>
                    </Grid>
                  </Card>
                ))}
              </Stack>
            )}
          </Box>
        </Stack>
      </Modal>
      <EditFeedModal
        opened={editConfigOpened}
        onClose={closeEditConfig}
        title={t('edit_feed_configuration')}
        feed={feed}
        onFeedChange={setFeed}
        isPlaylist={isPlaylistFeed}
        onPreview={() => setPreview(true)}
        size="lg"
        autoDownloadLimitField={
          <NumberInput
            label={t('auto_download_limit')}
            name="autoDownloadLimit"
            placeholder={t('3')}
            value={feed.autoDownloadLimit}
            onChange={(value) => setFeed({ ...feed, autoDownloadLimit: value })}
            disabled={feed?.autoDownloadEnabled === false}
          />
        }
        actionButtons={
          <Group mt="md" justify={'flex-end'}>
            <Button variant="default" onClick={closeEditConfig}>
              {t('cancel')}
            </Button>
            <Button variant="filled" loading={filterLoading} onClick={previewFeed}>
              {t('confirm')}
            </Button>
          </Group>
        }
      />
      <Modal
        opened={invalidSourceOpened}
        onClose={closeInvalidSourceModal}
        title={
          sourceFormatModalScene === 'result'
            ? t('feed_source_format_modal_title')
            : t('feed_source_format_modal_title_guide')
        }
        size="xl"
      >
        <Stack gap="sm">
          <Text size="sm" c="dimmed">
            {t('feed_source_format_modal_description')}
          </Text>
          {supportedSourceFormats.map((formatItem, index) => (
            <Box key={formatItem.key}>
              <Text size="sm" fw={600}>
                {index + 1}. {formatItem.label}
              </Text>
              {formatItem.examples.map((exampleItem) => (
                <Text key={`${formatItem.key}-${exampleItem.value}`} size="sm" c="dimmed">
                  <Text span fw={500}>
                    {exampleItem.label}:
                  </Text>{' '}
                  <Text span ff="monospace">
                    {exampleItem.value}
                  </Text>
                </Text>
              ))}
              {formatItem.tip ? (
                <Text size="sm" c="darkgreen" mt={2}>
                  {formatItem.tip}
                </Text>
              ) : null}
            </Box>
          ))}
        </Stack>
      </Modal>
    </Container>
  );
};

export default Home;
