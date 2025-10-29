import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useMediaQuery } from '@mantine/hooks';
import {
  Badge,
  Box,
  Button,
  Card,
  Center,
  Container,
  Grid,
  Group,
  Image,
  Loader,
  Pagination,
  SegmentedControl,
  Stack,
  Text,
  Title,
  Tooltip,
} from '@mantine/core';
import { IconBackspace, IconCircleX, IconClock, IconRotate } from '@tabler/icons-react';
import {
  API,
  formatISODateTime,
  formatISODuration,
  showError,
  showSuccess,
} from '../../helpers';

const PAGE_SIZE = 10;

const actionIcons = {
  retry: <IconRotate size={16} />,
  delete: <IconBackspace size={16} />,
  cancel: <IconCircleX size={16} />,
};

const DashboardEpisodes = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { status: statusParam } = useParams();
  const isSmallScreen = useMediaQuery('(max-width: 36em)');

  const statusOptions = useMemo(
    () => [
      { value: 'PENDING', label: t('dashboard_pending') },
      { value: 'DOWNLOADING', label: t('dashboard_downloading') },
      { value: 'COMPLETED', label: t('dashboard_completed') },
      { value: 'FAILED', label: t('dashboard_failed') },
    ],
    [t],
  );

  const normalizedStatus = String(statusParam || '').toUpperCase();
  const activeStatusOption = statusOptions.find((option) => option.value === normalizedStatus);
  const effectiveStatus = activeStatusOption ? activeStatusOption.value : statusOptions[0].value;

  useEffect(() => {
    if (!activeStatusOption) {
      navigate(`/dashboard/episodes/${effectiveStatus.toLowerCase()}`, { replace: true });
    }
  }, [activeStatusOption, effectiveStatus, navigate]);

  const [episodes, setEpisodes] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);

  const fetchEpisodes = useCallback(
    async (page) => {
      setLoading(true);

      try {
        const res = await API.get('/api/dashboard/episodes', {
          params: {
            status: effectiveStatus,
            page,
            size: PAGE_SIZE,
          },
        });

        const { code, msg, data } = res.data;
        if (code !== 200) {
          showError(msg || t('failed_to_load_episodes', { defaultValue: 'Failed to load episodes' }));
          return;
        }

        setEpisodes(data.records || []);
        setTotalPages(data.pages || 1);
        setCurrentPage(data.current || page);
      } catch (error) {
        console.error('Failed to fetch dashboard episodes:', error);
        showError(t('failed_to_load_episodes', { defaultValue: 'Failed to load episodes' }));
      } finally {
        setLoading(false);
      }
    },
    [effectiveStatus, t],
  );

  useEffect(() => {
    setCurrentPage(1);
  }, [effectiveStatus]);

  useEffect(() => {
    fetchEpisodes(currentPage);
  }, [currentPage, fetchEpisodes]);

  const handleRetry = async (episodeId) => {
    try {
      await API.post(`/api/episode/retry/${episodeId}`);
      showSuccess(t('retry_submitted'));
      fetchEpisodes(currentPage);
    } catch (error) {
      console.error('Failed to retry episode:', error);
      showError(t('retry_failed'));
    }
  };

  const handleDelete = async (episodeId) => {
    try {
      await API.delete(`/api/episode/${episodeId}`);
      showSuccess(t('episode_deleted_success'));
      fetchEpisodes(currentPage);
    } catch (error) {
      console.error('Failed to delete episode:', error);
      showError(t('episode_delete_failed', { defaultValue: 'Failed to delete episode' }));
    }
  };

  const handleCancel = async (episodeId) => {
    try {
      await API.post(`/api/episode/cancel/${episodeId}`);
      showSuccess(t('episode_cancelled_successfully', { defaultValue: 'Pending episode cancelled' }));
      fetchEpisodes(currentPage);
    } catch (error) {
      console.error('Failed to cancel episode:', error);
      showError(t('cancel_failed', { defaultValue: 'Failed to cancel episode' }));
    }
  };

  const renderActions = (episode) => {
    const actions = [];

    if (episode.downloadStatus === 'FAILED') {
      actions.push({
        key: 'retry',
        label: t('retry'),
        color: 'orange',
        onClick: () => handleRetry(episode.id),
      });
      actions.push({
        key: 'delete',
        label: t('delete'),
        color: 'pink',
        onClick: () => handleDelete(episode.id),
      });
    } else if (episode.downloadStatus === 'COMPLETED') {
      actions.push({
        key: 'delete',
        label: t('delete'),
        color: 'pink',
        onClick: () => handleDelete(episode.id),
      });
    } else if (episode.downloadStatus === 'PENDING') {
      actions.push({
        key: 'cancel',
        label: t('cancel'),
        color: 'gray',
        onClick: () => handleCancel(episode.id),
      });
    }

    if (actions.length === 0) {
      return null;
    }

    return (
      <Group gap="xs">
        {actions.map((action) => (
          <Button
            key={action.key}
            size="compact-xs"
            variant="outline"
            color={action.color}
            leftSection={actionIcons[action.key]}
            onClick={action.onClick}
          >
            {action.label}
          </Button>
        ))}
      </Group>
    );
  };

  const statusLabel = activeStatusOption?.label || statusOptions[0].label;
  const pageTitle = t('dashboard_status_detail_title', {
    status: statusLabel,
    defaultValue: `${statusLabel} ${t('episodes', { defaultValue: 'Episodes' })}`,
  });

  const cardHeight = isSmallScreen ? 75 : 100;

  return (
    <Container size="lg" py={isSmallScreen ? 'md' : 'xl'}>
      <Stack gap="lg">
        <Group justify="space-between" wrap="wrap" align="center">
          <Title order={isSmallScreen ? 4 : 3}>{pageTitle}</Title>
          <SegmentedControl
            value={effectiveStatus}
            onChange={(value) => navigate(`/dashboard/episodes/${value.toLowerCase()}`)}
            data={statusOptions}
            size={isSmallScreen ? 'sm' : 'md'}
          />
        </Group>

        {loading ? (
          <Center py="xl">
            <Loader />
          </Center>
        ) : episodes.length === 0 ? (
          <Center py="xl">
            <Text c="dimmed">
              {t('dashboard_no_episodes_for_status', {
                status: statusLabel,
                defaultValue: t('no_data_available', {
                  defaultValue: 'No episodes for this status yet',
                }),
              })}
            </Text>
          </Center>
        ) : (
          <Stack gap="sm">
            {episodes.map((episode) => {
              const actions = renderActions(episode);
              return (
                <Card
                  key={episode.id}
                  padding="sm"
                  radius="md"
                  withBorder
                  style={{ minHeight: cardHeight }}
                >
                  <Grid gutter="md" align="stretch">
                    <Grid.Col span={{ base: 4, sm: 2.8 }}>
                      <Box
                        style={{
                          borderRadius: 'var(--mantine-radius-md)',
                          overflow: 'hidden',
                          height: cardHeight,
                        }}
                      >
                        <Image
                          src={episode.maxCoverUrl || episode.defaultCoverUrl}
                          alt={episode.title}
                          height={cardHeight}
                          fit="cover"
                        />
                      </Box>
                    </Grid.Col>

                    <Grid.Col span={{ base: 8, sm: 9.2 }}>
                      <Stack
                        gap="xs"
                        justify="space-between"
                        style={{ height: cardHeight, minHeight: cardHeight }}
                      >
                        <Stack gap={4}>
                          <Group justify="space-between" align="flex-start" gap="sm">
                            <Box style={{ flex: 1, minWidth: 0 }}>
                              <Text
                                size={isSmallScreen ? 'sm' : 'md'}
                                fw={600}
                                lineClamp={1}
                                title={episode.title}
                              >
                                {episode.title}
                              </Text>
                            </Box>
                            <Text size="sm" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
                              {formatISODuration(episode.duration)}
                            </Text>
                          </Group>
                          <Text
                            size="sm"
                            c="dimmed"
                            lineClamp={isSmallScreen ? 1 : 2}
                            style={{ minHeight: isSmallScreen ? 'auto' : '2rem' }}
                          >
                            {episode.description
                              ? episode.description
                              : t('no_description_available')}
                          </Text>
                        </Stack>

                        <Group
                          justify="space-between"
                          align={isSmallScreen ? 'flex-start' : 'center'}
                          gap="sm"
                        >
                          <Group gap="sm" align="center">
                            <Text size="sm" c="dimmed">
                              {episode.publishedAt
                                ? formatISODateTime(episode.publishedAt)
                                : t('unknown_date')}
                            </Text>
                            {episode.downloadStatus === 'FAILED' && episode.errorLog ? (
                              <Tooltip
                                multiline
                                w={300}
                                label={episode.errorLog}
                                withArrow
                                transitionProps={{ duration: 200 }}
                              >
                                <Badge color="red" variant="outline">
                                  {t('details', { defaultValue: 'Details' })}
                                </Badge>
                              </Tooltip>
                            ) : null}
                          </Group>
                          {actions}
                        </Group>
                      </Stack>
                    </Grid.Col>
                  </Grid>
                </Card>
              );
            })}
          </Stack>
        )}

        {episodes.length > 0 && totalPages > 1 ? (
          <Pagination value={currentPage} onChange={setCurrentPage} total={totalPages} size="sm" />
        ) : null}
      </Stack>
    </Container>
  );
};

export default DashboardEpisodes;
