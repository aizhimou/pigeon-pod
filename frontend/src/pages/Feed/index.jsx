import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useDisclosure, useMediaQuery } from '@mantine/hooks';
import {
  Container,
  Grid,
  Title,
  Text,
  Image,
  Button,
  Group,
  Card,
  Center,
  Stack,
  Badge,
  Box,
  Modal,
  Loader,
  TextInput,
  Tooltip,
  FileInput,
} from '@mantine/core';
import {
  IconPlayerPlayFilled,
  IconBackspace,
  IconRotate,
  IconDownload,
  IconCircleX,
  IconBrandYoutubeFilled,
  IconVideo,
  IconHeadphones
} from '@tabler/icons-react';
import {
  API,
  formatDateWithPattern,
  formatISODuration,
  showError,
  showSuccess,
  copyToClipboard,
} from '../../helpers/index.js';
import { useTranslation } from 'react-i18next';
import { usePlayer } from '../../context/PlayerContext';
import { useDateFormat } from '../../hooks/useDateFormat.js';
import CopyModal from '../../components/CopyModal';
import EditFeedModal from '../../components/EditFeedModal';
import FeedHeader from '../../components/FeedHeader';
import './episode-image.css';

// 需要自动轮询的节目状态常量（移到组件外部避免重复创建）
const ACTIVE_STATUSES = ['PENDING', 'DOWNLOADING'];

// 下载状态对应的多语言文案 key
const DOWNLOAD_STATUS_LABEL_KEYS = {
  READY: 'episode_status_ready',
  PENDING: 'episode_status_pending',
  DOWNLOADING: 'episode_status_downloading',
  COMPLETED: 'episode_status_completed',
  FAILED: 'episode_status_failed',
};

const FeedDetail = () => {
  const { t } = useTranslation();
  const dateFormat = useDateFormat();
  const isSmallScreen = useMediaQuery('(max-width: 36em)');
  const { type, feedId } = useParams();
  const navigate = useNavigate();
  const [feed, setFeed] = useState(null);
  const [episodes, setEpisodes] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [hasMoreEpisodes, setHasMoreEpisodes] = useState(true);
  const [loadingEpisodes, setLoadingEpisodes] = useState(false);
  const observerRef = useRef();
  const loadingRef = useRef(false); // Use ref to track loading state without causing re-renders
  const [
    confirmDeleteFeedOpened,
    { open: openConfirmDeleteFeed, close: closeConfirmDeleteFeed },
  ] = useDisclosure(false);
  const [deleting, setDeleting] = useState(false);
  const [editConfigOpened, { open: openEditConfig, close: closeEditConfig }] = useDisclosure(false);
  const [copyModalOpened, { open: openCopyModal, close: closeCopyModal }] = useDisclosure(false);
  const [copyText, setCopyText] = useState('');
  const [
    customizeFeedModalOpened,
    { open: openCustomizeFeedModal, close: closeCustomizeFeedModal },
  ] = useDisclosure(false);
  const [editingTitle, setEditingTitle] = useState('');
  const [customCoverFile, setCustomCoverFile] = useState(null);
  const [refreshTimer, setRefreshTimer] = useState(null);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);

  // Intersection Observer callback for infinite scrolling
  const lastEpisodeElementRef = useCallback(
    (node) => {
      if (loadingRef.current) return;
      if (observerRef.current) observerRef.current.disconnect();
      observerRef.current = new IntersectionObserver(
        (entries) => {
          if (entries[0].isIntersecting && hasMoreEpisodes && !loadingRef.current) {
            setCurrentPage((prevPage) => prevPage + 1);
          }
        },
        { threshold: 0.1 },
      );
      if (node) observerRef.current.observe(node);
    },
    [hasMoreEpisodes],
  );

  const fetchFeedDetail = useCallback(async () => {
    const res = await API.get(`/api/feed/${type}/detail/${feedId}`);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
    } else {
      setFeed(data);
    }
  }, [feedId, type]);

  const fetchEpisodes = useCallback(
    async (page = 1, isInitialLoad = false) => {
      // Prevent duplicate requests using ref
      if (loadingRef.current) return;

      loadingRef.current = true;
      setLoadingEpisodes(true);

      try {
        const res = await API.get(`/api/episode/list/${feedId}?page=${page}&size=25`);
        const { code, msg, data } = res.data;

        if (code !== 200) {
          showError(msg);
          return;
        }

        // MyBatis Plus Page object has 'records' for data and 'pages' for total pages
        const episodes = data.records || [];
        const totalPages = data.pages || 0;

        if (isInitialLoad) {
          setEpisodes(episodes);
          setCurrentPage(1);
        } else {
          setEpisodes((prevEpisodes) => [...prevEpisodes, ...episodes]);
        }

        // Check if there are more episodes to load
        setHasMoreEpisodes(page < totalPages);
      } catch (error) {
        showError('Failed to load episodes');
        console.error('Fetch episodes error:', error);
      } finally {
        loadingRef.current = false;
        setLoadingEpisodes(false);
      }
    },
    [feedId], // Remove loadingEpisodes dependency
  );

  useEffect(() => {
    fetchFeedDetail();
    fetchEpisodes(1, true); // Initial load
  }, [fetchFeedDetail, fetchEpisodes]);

  useEffect(() => {
    if (currentPage > 1) {
      fetchEpisodes(currentPage, false); // Load more episodes
    }
  }, [currentPage, fetchEpisodes]);

  // 组件卸载时清理定时器
  useEffect(() => {
    return () => {
      if (refreshTimer) {
        clearInterval(refreshTimer);
      }
    };
  }, [refreshTimer]);

  // Update feed config
  const updateFeedConfig = async () => {
    const res = await API.put(`/api/feed/${type}/config/${feedId}`, feed);
    const { code, msg, data } = res.data;

    if (code !== 200) {
      showError(msg || t('update_channel_config_failed'));
      return;
    }

    if (data.downloadHistory) {
      showSuccess(t('channel_config_updated_and_add_history_episodes_task_submitted'));
    } else {
      showSuccess(t('channel_config_updated'));
    }
    closeEditConfig();
  };

  const handleUpdateCustomFeed = async () => {
    if (customCoverFile) {
      const formData = new FormData();
      formData.append('file', customCoverFile);

      try {
        const uploadRes = await API.post(`/api/feed/${type}/${feedId}/cover`, formData, {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        });

        if (uploadRes.data.code !== 200) {
          showError(uploadRes.data.msg || 'Failed to upload cover image');
          return; // Stop if cover upload fails
        }
      } catch (error) {
        showError('Failed to upload cover image');
        console.error(error);
        return; // Stop if cover upload fails
      }
    }

    // Now update the title
    const res = await API.put(`/api/feed/${type}/config/${feedId}`, {
      ...feed,
      customTitle: editingTitle,
    });

    const { code, msg } = res.data;

    if (code !== 200) {
      showError(msg || t('update_feed_failed'));
      return;
    }

    showSuccess(t('update_feed_success'));
    await fetchFeedDetail(); // Refetch to get all updated data
    closeCustomizeFeedModal();
    setCustomCoverFile(null);
  };

  const handleClearCustomCover = async () => {
    try {
      const res = await API.delete(`/api/feed/${type}/${feedId}/cover`);
      if (res.data.code !== 200) {
        showError(res.data.msg || 'Failed to clear custom cover');
        return;
      }
      showSuccess('Custom cover cleared successfully');
      await fetchFeedDetail(); // Refetch to get all updated data
      closeCustomizeFeedModal();
      setCustomCoverFile(null);
    } catch (error) {
      showError('Failed to clear custom cover');
      console.error(error);
    }
  };

  const deleteFeed = async () => {
    setDeleting(true);
    const response = await API.delete(`/api/feed/${type}/delete/${feedId}`);
    const { code, msg } = response.data;

    if (code !== 200) {
      showError(msg || t('delete_channel_failed'));
      setDeleting(false);
      return;
    }

    showSuccess(t('channel_deleted_success'));
    setDeleting(false);

    // Navigate back to the feeds list page
    navigate('/');
  };

  const handleSubscribe = async () => {
    if (!feed) {
      return;
    }
    try {
      const response = await API.get(`/api/feed/${type}/subscribe/${feed.id}`);
      const { code, msg, data } = response.data;

      if (code !== 200) {
        showError(msg || t('failed_to_generate_subscription_url'));
        return;
      }

      // 使用自定义复制功能
      await copyToClipboard(
        data,
        () => {
          // 复制成功回调
          showSuccess(t('subscription_link_generated_success'));
        },
        (text) => {
          // 需要手动复制时的回调
          setCopyText(text);
          openCopyModal();
        },
      );
    } catch (error) {
      showError(t('failed_to_generate_subscription_url'));
      console.error('Subscribe error:', error);
    }
  };

  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      const res = await API.post(`/api/feed/${type}/refresh/${feedId}`);
      const { code, data, msg } = res.data;
      if (code !== 200) {
        showError(msg || t('feed_refresh_failed'));
        return;
      }
      showSuccess(data.message || t('feed_refresh_success'));
      await fetchFeedDetail();
      await fetchEpisodes(1, true);
    } catch (error) {
      showError(t('feed_refresh_failed'));
      console.error('Refresh feed error:', error);
    } finally {
      setRefreshing(false);
    }
  }, [feedId, fetchEpisodes, fetchFeedDetail, t, type]);

  const handleFetchHistory = useCallback(async () => {
    if (loadingHistory) {
      return;
    }
    setLoadingHistory(true);
    try {
      const res = await API.post(`/api/feed/${type}/history/${feedId}`);
      const { code, msg, data } = res.data;
      if (code !== 200) {
        showError(msg || t('fetch_history_episodes_failed'));
        return;
      }
      if (Array.isArray(data) && data.length > 0) {
        setEpisodes((prevEpisodes) => [...prevEpisodes, ...data]);
        showSuccess(t('fetch_history_episodes_success', { count: data.length }));
      } else {
        showSuccess(t('fetch_history_episodes_empty'));
      }
    } catch (error) {
      console.error('Fetch history episodes error:', error);
      showError(t('fetch_history_episodes_failed'));
    } finally {
      setLoadingHistory(false);
    }
  }, [feedId, loadingHistory, t, type]);

  const handleEditAppearance = () => {
    if (!feed) {
      return;
    }
    setEditingTitle(feed.customTitle || '');
    openCustomizeFeedModal();
  };

  const getDownloadStatusColor = (status) => {
    switch (status) {
      case 'READY':
        return 'gray';
      case 'COMPLETED':
        return 'green';
      case 'DOWNLOADING':
        return 'blue';
      case 'PENDING':
        return 'yellow';
      case 'FAILED':
        return 'red';
      default:
        return 'gray';
    }
  };

  // 检查是否有需要跟踪状态变化的节目（PENDING, DOWNLOADING）
  const hasActiveEpisodes = useCallback(() => {
    return episodes.some(episode => ACTIVE_STATUSES.includes(episode.downloadStatus));
  }, [episodes]);

  // 刷新活跃状态节目的状态（PENDING, DOWNLOADING）
  const refreshActiveEpisodes = useCallback(async () => {
    if (!hasActiveEpisodes()) return;

    try {
      // 获取当前活跃状态的节目ID列表
      const activeIds = episodes
        .filter(episode => ACTIVE_STATUSES.includes(episode.downloadStatus))
        .map(episode => episode.id);

      if (activeIds.length === 0) return;

      // 使用专门的API端点获取特定节目的状态
      const res = await API.post('/api/episode/status', activeIds);
      const { code, data } = res.data;

      if (code !== 200) {
        console.error('Failed to fetch episode status');
        return;
      }

      // 更新对应节目的状态，保持分页不变，只更新状态相关字段
      setEpisodes(prevEpisodes =>
        prevEpisodes.map(episode => {
          const updatedEpisode = data.find(updated => updated.id === episode.id);
          if (updatedEpisode) {
            // 只更新状态相关的字段，保持其他字段不变
            return {
              ...episode,
              downloadStatus: updatedEpisode.downloadStatus,
              errorLog: updatedEpisode.errorLog,
              mediaType: updatedEpisode.mediaType
            };
          }
          return episode;
        })
      );
    } catch (error) {
      console.error('Failed to refresh active episodes:', error);
    }
  }, [episodes, hasActiveEpisodes]);

  // 自动刷新活跃状态节目的状态（PENDING, DOWNLOADING）
  useEffect(() => {
    let timer = null;

    // 如果有活跃状态的节目，设置3秒定时器
    if (hasActiveEpisodes()) {
      timer = setInterval(() => {
        refreshActiveEpisodes();
      }, 3000);

      setRefreshTimer(timer);
    } else {
      setRefreshTimer(null);
    }

    // 清理函数
    return () => {
      if (timer) {
        clearInterval(timer);
      }
    };
  }, [hasActiveEpisodes, refreshActiveEpisodes]);

  const { play } = usePlayer();

  const handlePlay = (episode) => {
    if (episode.downloadStatus === 'COMPLETED') {
      play(episode, feed);
    } else {
      let videoId = episode.id;
      let youtubeVideoUrl = `https://www.youtube.com/watch?v=${videoId}`;
      window.open(youtubeVideoUrl, '_blank', 'noopener,noreferrer');
    }
  };

  const deleteEpisode = async (episodeId) => {
    const response = await API.delete(`/api/episode/${episodeId}`);
    const { code, msg } = response.data;

    if (code !== 200) {
      showError(msg || t('delete_episode_failed'));
      return;
    }

    showSuccess(t('episode_deleted_success'));
    // 乐观更新：从当前列表中移除该节目，避免整页刷新导致的闪烁
    setEpisodes((prevEpisodes) =>
      prevEpisodes.filter((episode) => episode.id !== episodeId),
    );
  };

  const retryEpisode = async (episodeId) => {
    const response = await API.post(`/api/episode/retry/${episodeId}`);
    const { code, msg } = response.data;

    if (code !== 200) {
      showError(msg || t('retry_failed'));
      return;
    }
    showSuccess(t('retry_submitted'));
    // 乐观更新：将状态标记为排队中，交给轮询流程同步后续状态
    setEpisodes((prevEpisodes) =>
      prevEpisodes.map((episode) =>
        episode.id === episodeId
          ? { ...episode, downloadStatus: 'PENDING', errorLog: null }
          : episode,
      ),
    );
  };

  const cancelEpisode = async (episodeId) => {
    try {
      await API.post(`/api/episode/cancel/${episodeId}`);
      showSuccess(
        t('episode_cancelled_successfully', {
          defaultValue: 'Pending episode cancelled',
        }),
      );
      // 取消后保留节目卡片，仅将状态重置为 READY
      setEpisodes((prevEpisodes) =>
        prevEpisodes.map((episode) =>
          episode.id === episodeId
            ? { ...episode, downloadStatus: 'READY', errorLog: null }
            : episode,
        ),
      );
    } catch (error) {
      console.error('Failed to cancel episode:', error);
      showError(
        t('cancel_failed', { defaultValue: 'Failed to cancel episode' }),
      );
    }
  };

  const downloadEpisode = async (episodeId) => {
    const response = await API.post(`/api/episode/download/${episodeId}`);
    const { code, msg } = response.data;

    if (code !== 200) {
      showError(msg || t('download_failed'));
      return;
    }
    showSuccess(t('download_submitted'));
    // 乐观更新本地状态：标记为排队中，交给轮询同步后续状态
    setEpisodes((prevEpisodes) =>
      prevEpisodes.map((episode) =>
        episode.id === episodeId
          ? { ...episode, downloadStatus: 'PENDING', errorLog: null }
          : episode,
      ),
    );
  };

  const downloadEpisodeToLocal = (episodeId) => {
    const baseURL = API.defaults.baseURL || '';
    const url = `${baseURL}/api/episode/download/local/${encodeURIComponent(episodeId)}`;
    const link = document.createElement('a');
    link.href = url;
    link.download = '';
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  if (!feed) {
    return (
      <Container>
        <Center h={400}>
          <Title order={2}>{t('loading_channel_details')}</Title>
        </Center>
      </Container>
    );
  }

  const isPlaylist = feed?.type && String(feed.type).toLowerCase() === 'playlist';

  return (
    <Container size="xl" py={isSmallScreen ? 'md' : 'xl'}>
      {/* Feed Header Section */}
      <FeedHeader
        feed={feed}
        isSmallScreen={isSmallScreen}
        onSubscribe={handleSubscribe}
        onOpenConfig={openEditConfig}
        onRefresh={handleRefresh}
        refreshLoading={refreshing}
        onConfirmDelete={openConfirmDeleteFeed}
        onEditAppearance={handleEditAppearance}
      />

      {/* Episodes Section */}
      <Box>
        {episodes.length === 0 ? (
          <Center py="xl">
            <Text c="dimmed">{t('no_episodes_found')}</Text>
          </Center>
        ) : (
          <Stack>
            {episodes.map((episode, index) => (
              <Card
                key={episode.id}
                padding='sm'
                radius="md"
                withBorder
                ref={index === episodes.length - 1 ? lastEpisodeElementRef : null}
              >
                <Grid>
                  {/* Episode thumbnail with hover and play button */}
                  <Grid.Col span={{ base: 12, sm: 3 }}>
                    <Box
                      style={{
                        position: 'relative',
                        cursor: 'pointer',
                        overflow: 'hidden',
                        borderRadius: 'var(--mantine-radius-md)',
                      }}
                      className="episode-image-container"
                    >
                      <Image
                        src={episode.maxCoverUrl || episode.defaultCoverUrl}
                        alt={episode.title}
                        radius="md"
                        height={160}
                        className="episode-image"
                        style={{ transition: 'filter 0.3s' }}
                      />
                      <Box
                        className="episode-play-overlay"
                        style={{
                          position: 'absolute',
                          top: 0,
                          left: 0,
                          width: '100%',
                          height: '100%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          pointerEvents: 'none',
                          zIndex: 2,
                        }}
                      >
                        <Button
                          radius="xl"
                          size="lg"
                          leftSection={<IconPlayerPlayFilled size={32} />}
                          style={{
                            backdropFilter: 'blur(8px)',
                            background: 'rgba(255,255,255,0.25)',
                            boxShadow: '0 4px 32px rgba(0,0,0,0.15)',
                            pointerEvents: 'auto',
                          }}
                          onClick={() => handlePlay(episode)}
                        >
                          {t('play')}
                        </Button>
                      </Box>

                      {/* Media Type Badge */}
                      {episode.mediaType && (
                        <Badge
                          variant="filled"
                          color={episode.mediaType?.startsWith('video') ? 'blue' : 'orange'}
                          size="sm"
                          radius="sm"
                          leftSection={episode.mediaType?.startsWith('video') ? <IconVideo size={12} /> : <IconHeadphones size={12} />}
                          style={{
                            position: 'absolute',
                            top: 10,
                            right: 10,
                            zIndex: 10,
                            pointerEvents: 'none',
                            boxShadow: '0 2px 4px rgba(0,0,0,0.2)'
                          }}
                        >
                          {episode.mediaType?.startsWith('video') ? 'Video' : 'Audio'}
                        </Badge>
                      )}
                    </Box>
                  </Grid.Col>

                  {/* Episode details */}
                  <Grid.Col span={{ base: 12, sm: 9 }}>
                    <Stack>
                      <Box>
                        <Group justify="space-between">
                          <Box
                            style={{ maxWidth: isSmallScreen ? '66%' : '85%', overflow: 'hidden' }}
                          >
                            <Title
                              order={isSmallScreen ? 5 : 4}
                              style={{
                                whiteSpace: 'nowrap',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                              }}
                              title={episode.title}
                            >
                              {episode.title}
                            </Title>
                          </Box>
                          <Text c="dimmed" style={{ whiteSpace: 'nowrap' }}>
                            {formatISODuration(episode.duration)}
                          </Text>
                        </Group>

                        <Text
                          size="sm"
                          mt="xs"
                          lineClamp={isSmallScreen ? 3 : 4}
                          style={{ minHeight: isSmallScreen ? '0' : '4rem' }}
                        >
                          {episode.description
                            ? episode.description
                            : t('no_description_available')}
                        </Text>
                      </Box>

                      <Group justify="space-between" align="center">
                        <Group>
                          <Text size="sm" c="dimmed">
                            {episode.publishedAt
                              ? formatDateWithPattern(episode.publishedAt, dateFormat)
                              : t('unknown_date')}
                          </Text>
                          {episode.downloadStatus ? (
                            episode.downloadStatus === 'FAILED' ? (
                              <Tooltip
                                multiline
                                w={300}
                                withArrow
                                transitionProps={{ duration: 200 }}
                                label={episode.errorLog || t('unknown_error')}
                              >
                                <Badge
                                  variant="light"
                                  color={getDownloadStatusColor(episode.downloadStatus)}
                                >
                                  {t(
                                    DOWNLOAD_STATUS_LABEL_KEYS[episode.downloadStatus] ||
                                    episode.downloadStatus,
                                  )}
                                </Badge>
                              </Tooltip>
                            ) : (
                              <Badge
                                color={getDownloadStatusColor(episode.downloadStatus)}
                                variant="light"
                              >
                                {t(
                                  DOWNLOAD_STATUS_LABEL_KEYS[episode.downloadStatus] ||
                                  episode.downloadStatus,
                                )}
                              </Badge>
                            )
                          ) : null}
                          {episode.downloadStatus === 'COMPLETED' ? (
                            <Button
                              size="compact-xs"
                              variant="default"
                              onClick={() => downloadEpisodeToLocal(episode.id)}
                              leftSection={<IconDownload size={16} />}
                            >
                              {t('download_to_local')}
                            </Button>
                          ) : null}
                        </Group>
                        <Group>
                          {episode.downloadStatus === 'READY' ? (
                            <Button
                              size="compact-xs"
                              variant="outline"
                              color="blue"
                              onClick={() => downloadEpisode(episode.id)}
                              leftSection={<IconDownload size={16} />}
                            >
                              {t('download')}
                            </Button>
                          ) : null}
                          {episode.downloadStatus === 'FAILED' ? (
                            <Button
                              size="compact-xs"
                              variant="outline"
                              color="orange"
                              onClick={() => retryEpisode(episode.id)}
                              leftSection={<IconRotate size={16} />}
                            >
                              {t('retry')}
                            </Button>
                          ) : null}
                          {episode.downloadStatus === 'PENDING' ? (
                            <Button
                              size="compact-xs"
                              variant="outline"
                              color="MediumSeaGreen"
                              onClick={() => cancelEpisode(episode.id)}
                              leftSection={<IconCircleX size={16} />}
                            >
                              {t('cancel')}
                            </Button>
                          ) : null}
                          {['COMPLETED', 'FAILED'].includes(episode.downloadStatus) ? (
                            <Tooltip
                              label={t('episode_delete_with_files_hint')}
                              withArrow
                              transitionProps={{ duration: 200 }}
                            >
                              <Button
                                size="compact-xs"
                                variant="outline"
                                color="pink"
                                onClick={() => deleteEpisode(episode.id)}
                                leftSection={<IconBackspace size={16} />}
                              >
                                {t('delete')}
                              </Button>
                            </Tooltip>
                          ) : null}
                        </Group>
                      </Group>
                    </Stack>
                  </Grid.Col>
                </Grid>
              </Card>
            ))}

            {/* Loader for infinite scrolling */}
            {loadingEpisodes && (
              <Center>
                <Loader />
              </Center>
            )}
            {!hasMoreEpisodes && episodes.length > 0 && !isPlaylist && (
              <Center>
                <Button
                  variant="outline"
                  fullWidth
                  onClick={handleFetchHistory}
                  loading={loadingHistory}
                  color="#ff0034"
                  leftSection={<IconBrandYoutubeFilled size={18} />}
                >
                  {t('fetch_history_episodes')}
                </Button>
              </Center>
            )}
          </Stack>
        )}
      </Box>

      {/* Delete Channel Confirmation Modal */}
      <Modal
        opened={confirmDeleteFeedOpened}
        onClose={closeConfirmDeleteFeed}
        title={t('confirm_delete_channel')}
      >
        <Text fw={500}>{t('confirm_delete_channel_tip')}</Text>
        <Group justify="flex-end" mt="md">
          <Button
            color="red"
            loading={deleting}
            onClick={() => {
              deleteFeed().then(closeConfirmDeleteFeed);
            }}
          >
            {t('confirm')}
          </Button>
        </Group>
      </Modal>

      <EditFeedModal
        opened={editConfigOpened}
        onClose={closeEditConfig}
        title={t('edit_channel_configuration')}
        feed={feed}
        onFeedChange={setFeed}
        isPlaylist={isPlaylist}
        size="lg"
        actionButtons={
          <Group mt="md" justify="flex-end">
            <Button variant="default" onClick={closeEditConfig}>
              {t('cancel')}
            </Button>
            <Button variant="filled" onClick={updateFeedConfig}>
              {t('save')}
            </Button>
          </Group>
        }
      />

      {/* Copy Modal for manual copy */}
      <CopyModal
        opened={copyModalOpened}
        onClose={closeCopyModal}
        text={copyText}
        title={t('manual_copy_title')}
      />

      {/* Customize Feed Modal */}
      <Modal
        opened={customizeFeedModalOpened}
        onClose={closeCustomizeFeedModal}
        title={t('edit_title')}
      >
        <Stack>
          <TextInput
            label={t('custom_title')}
            value={editingTitle}
            onChange={(event) => setEditingTitle(event.currentTarget.value)}
            data-autofocus
          />
          <Grid align="flex-end">
            <Grid.Col span="auto">
              <FileInput
                label={t('custom_cover')}
                placeholder={t('upload_image')}
                value={customCoverFile}
                onChange={setCustomCoverFile}
                accept="image/jpeg,image/png,image/webp"
                clearable
              />
            </Grid.Col>
            {feed.customCoverUrl && (
              <Grid.Col span="content">
                <Button
                  variant="outline"
                  color="red"
                  onClick={() => {
                    handleClearCustomCover().then(() => { });
                  }}
                >
                  {t('clear_cover')}
                </Button>
              </Grid.Col>
            )}
          </Grid>
        </Stack>
        <Group justify="flex-end" mt="xl">
          <Button variant="default" onClick={closeCustomizeFeedModal}>
            {t('cancel')}
          </Button>
          <Button
            onClick={() => {
              handleUpdateCustomFeed().then(() => { });
            }}
          >
            {t('confirm')}
          </Button>
        </Group>
      </Modal>
    </Container>
  );
};

export default FeedDetail;
