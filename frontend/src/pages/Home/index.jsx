import React, { useEffect, useState } from 'react';
import {
  API,
  formatISODateTime,
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
  Image,
  Text,
  Modal,
  Stack,
  Center,
  Box,
  NumberInput,
} from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { IconCheck, IconClock, IconSearch, IconSettings, IconClockHour4, IconDownload, IconCircleCheck, IconAlertCircle } from '@tabler/icons-react';
import { useNavigate } from 'react-router-dom';
import { useDisclosure, useMediaQuery } from '@mantine/hooks';
import VersionUpdateAlert from '../../components/VersionUpdateAlert';
import EditFeedModal from '../../components/EditFeedModal';
import FeedCard from '../../components/FeedCard';
import FeedHeader from '../../components/FeedHeader';
import StatisticsCard from '../../components/StatisticsCard';

const Home = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
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
  const [editConfigOpened, { open: openEditConfig, close: closeEditConfig }] = useDisclosure(false);
  const isPlaylistFeed = String(feed?.type || '').toLowerCase() === 'playlist';
  const [statistics, setStatistics] = useState({
    pendingCount: 0,
    downloadingCount: 0,
    completedCount: 0,
    failedCount: 0,
  });

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

  const goToFeedDetail = (type, feedId) => {
    const normalizedType = String(type || 'CHANNEL').toLowerCase();
    navigate(`/${normalizedType}/${feedId}`);
  };

  const fetchFeed = async () => {
    if (!feedSource) {
      showError(t('please_enter_valid_feed_url'));
      return;
    }
    setFetchFeedLoading(true);
    const res = await API.post('/api/feed/fetch', {
      source: feedSource.trim(),
    });
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
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

    // Set up polling for statistics every 3 seconds
    const statisticsInterval = setInterval(() => {
      fetchStatistics();
    }, 3000);

    // Cleanup interval on component unmount
    return () => {
      clearInterval(statisticsInterval);
    };
  }, []);

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

  return (
    <Container size="lg" mt="lg">
      <VersionUpdateAlert />

      {/* Dashboard Statistics Cards */}
      <Grid gutter="md" mb="lg">
        <Grid.Col span={{ base: 6, sm: 3 }}>
          <StatisticsCard
            label={t('dashboard_pending')}
            count={statistics.pendingCount}
            icon={<IconClockHour4 />}
            color="#2F4F4F"
          />
        </Grid.Col>

        <Grid.Col span={{ base: 6, sm: 3 }}>
          <StatisticsCard
            label={t('dashboard_downloading')}
            count={statistics.downloadingCount}
            icon={<IconDownload />}
            color="blue"
          />
        </Grid.Col>

        <Grid.Col span={{ base: 6, sm: 3 }}>
          <StatisticsCard
            label={t('dashboard_completed')}
            count={statistics.completedCount}
            icon={<IconCircleCheck />}
            color="green"
          />
        </Grid.Col>

        <Grid.Col span={{ base: 6, sm: 3 }}>
          <StatisticsCard
            label={t('dashboard_failed')}
            count={statistics.failedCount}
            icon={<IconAlertCircle />}
            color="red"
          />
        </Grid.Col>
      </Grid>

      <Group pos="relative" wrap="wrap" gap="sm">
        <Input
          leftSection={<IconSearch size={16} />}
          placeholder={t('enter_feed_source_url')}
          name="feedSource"
          value={feedSource}
          onChange={(e) => setFeedSource(decodeURIComponent(e.target.value))}
          style={{ flex: 1, minWidth: isSmallScreen ? '100%' : 0}}
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
            const isSyncEnabled = feedItem?.syncState !== false;
            const pausedTooltip = t('feed_sync_paused_tooltip');

            return (
              <FeedCard
                key={feedItem.id}
                feed={feedItem}
                onClick={() => goToFeedDetail(feedItem.type, feedItem.id)}
                dimmed={!isSyncEnabled}
                withTooltip={!isSyncEnabled}
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
        <Stack>
          <FeedHeader
            feed={feed}
            isSmallScreen={isSmallScreen}
            actions={modalActions}
            avatarSizeLarge={160}
            descriptionClampSmall={2}
            descriptionClampLarge={2}
          />
          <Box>
            {episodes.length === 0 ? (
              <Center py="xl">
                <Text c="dimmed">{t('no_episodes_found')}</Text>
              </Center>
            ) : (
              <Stack>
                {episodes.map((episode) => (
                  <Card key={episode.id} padding="md" radius="md" withBorder>
                    <Grid>
                      {/* Episode thumbnail */}
                      <Grid.Col span={{ base: 12, sm: 3 }}>
                        <Image
                          src={episode.maxCoverUrl || episode.defaultCoverUrl}
                          alt={episode.title}
                          radius="md"
                          w="100%"
                          fit="cover"
                        />
                      </Grid.Col>

                      {/* Episode details */}
                      <Grid.Col span={{ base: 12, sm: 9 }}>
                        <Text
                          fw={700}
                          style={{
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                          }}
                          title={episode.title}
                        >
                          {episode.title}
                        </Text>
                        <Text
                          size="sm"
                          lineClamp={isSmallScreen ? 2 : 4}
                          style={{ minHeight: isSmallScreen ? '2rem' : '4rem' }}
                        >
                          {episode.description
                            ? episode.description
                            : t('no_description_available')}
                        </Text>
                        <Group mt="xs" justify="space-between">
                          <Text size="sm" c="dimmed">
                            <IconClock
                              size={14}
                              style={{
                                display: 'inline',
                                verticalAlign: 'text-bottom',
                              }}
                            />{' '}
                            {episode.publishedAt
                              ? formatISODateTime(episode.publishedAt)
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
        initialEpisodesField={
          <NumberInput
            label={t('initial_episodes')}
            name="initialEpisodes"
            placeholder={t('3')}
            value={feed.initialEpisodes}
            onChange={(value) => setFeed({ ...feed, initialEpisodes: value })}
          />
        }
        actionButtons={
          <Group mt="md" justify={'flex-end'}>
            <Button variant="default" onClick={closeEditConfig}>
              {t('cancel')}
            </Button>
            <Button
              variant="filled"
              loading={filterLoading}
              onClick={previewFeed}
            >
              {t('confirm')}
            </Button>
          </Group>
        }
      />
    </Container>
  );
};

export default Home;
