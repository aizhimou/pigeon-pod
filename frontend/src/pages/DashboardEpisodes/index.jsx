import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useMediaQuery } from '@mantine/hooks';
import {
  Badge,
  Box,
  Button,
  Card,
  Center,
  Container, Flex,
  Grid,
  Group,
  Image,
  Loader,
  Modal,
  Pagination,
  SegmentedControl,
  Stack,
  Text,
  Title,
  Tooltip,
} from '@mantine/core';
import {
  IconBackspace,
  IconCircleX,
  IconRotate,
  IconTrash,
} from '@tabler/icons-react';
import {
  API,
  formatISODateTime,
  formatISODuration,
  showError,
  showSuccess,
} from '../../helpers';

const PAGE_SIZE = 10;

const DashboardEpisodes = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { status: statusParam } = useParams();
  const isSmallScreen = useMediaQuery('(max-width: 36em)');
  const [bulkLoading, setBulkLoading] = useState(false);

  const statusDefinitions = {
    PENDING: {
      optionLabelKey: 'dashboard_pending',
      headingKey: 'dashboard_status_heading_pending',
      confirmLabelKey: 'dashboard_status_confirm_pending',
      bulkAction: { type: 'cancel', color: 'gray', Icon: IconCircleX },
    },
    DOWNLOADING: {
      optionLabelKey: 'dashboard_downloading',
      headingKey: 'dashboard_status_heading_downloading',
      confirmLabelKey: 'dashboard_status_confirm_downloading',
    },
    COMPLETED: {
      optionLabelKey: 'dashboard_completed',
      headingKey: 'dashboard_status_heading_completed',
      confirmLabelKey: 'dashboard_status_confirm_completed',
      bulkAction: { type: 'delete', color: 'pink', Icon: IconTrash },
    },
    FAILED: {
      optionLabelKey: 'dashboard_failed',
      headingKey: 'dashboard_status_heading_failed',
      confirmLabelKey: 'dashboard_status_confirm_failed',
      bulkAction: { type: 'retry', color: 'orange', Icon: IconRotate },
    },
  };

  const statusOrder = ['PENDING', 'DOWNLOADING', 'COMPLETED', 'FAILED'];
  const statusOptions = statusOrder.map((value) => ({
    value,
    label: t(statusDefinitions[value].optionLabelKey),
  }));

  const actionIcons = {
    retry: <IconRotate size={16} />,
    delete: <IconBackspace size={16} />,
    cancel: <IconCircleX size={16} />,
  };

  const normalizedStatus = String(statusParam || '').toUpperCase();
  const activeStatusOption = statusOptions.find((option) => option.value === normalizedStatus);
  const effectiveStatus = activeStatusOption ? activeStatusOption.value : statusOptions[0].value;
  const currentDefinition =
    statusDefinitions[effectiveStatus] || statusDefinitions[statusOrder[0]];
  const statusLabel = t(currentDefinition.headingKey);
  const statusConfirmLabel = t(currentDefinition.confirmLabelKey);
  const bulkActionDefinition = currentDefinition.bulkAction;
  const BulkIcon = bulkActionDefinition?.Icon;

  const bulkActionTextKeys = {
    cancel: {
      labelKey: 'dashboard_cancel_all',
      confirmKey: 'dashboard_confirm_cancel_all',
      successKey: 'dashboard_bulk_cancel_success',
      errorKey: 'dashboard_bulk_cancel_failed',
    },
    delete: {
      labelKey: 'dashboard_delete_all',
      confirmKey: 'dashboard_confirm_delete_all',
      successKey: 'dashboard_bulk_delete_success',
      errorKey: 'dashboard_bulk_delete_failed',
    },
    retry: {
      labelKey: 'dashboard_retry_all',
      confirmKey: 'dashboard_confirm_retry_all',
      successKey: 'dashboard_bulk_retry_success',
      errorKey: 'dashboard_bulk_retry_failed',
    },
  };

  useEffect(() => {
    if (!activeStatusOption) {
      navigate(`/dashboard/episodes/${effectiveStatus.toLowerCase()}`, { replace: true });
    }
  }, [activeStatusOption, effectiveStatus, navigate]);

  const [episodes, setEpisodes] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [totalCount, setTotalCount] = useState(0);
  const [confirmModal, setConfirmModal] = useState({
    opened: false,
    actionType: null,
    message: '',
    textKeys: null,
    status: null,
    episodeIds: null,
  });

  const fetchEpisodes = useCallback(
    async (page, showLoader = true) => {
      if (showLoader) {
        setLoading(true);
      }

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
        setTotalCount(typeof data.total === 'number' ? data.total : (data.records || []).length);
      } catch (error) {
        console.error('Failed to fetch dashboard episodes:', error);
        showError(t('failed_to_load_episodes', { defaultValue: 'Failed to load episodes' }));
      } finally {
        if (showLoader) {
          setLoading(false);
        }
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

  useEffect(() => {
    const interval = setInterval(() => {
      fetchEpisodes(currentPage, false);
    }, 3000);
    return () => clearInterval(interval);
  }, [currentPage, effectiveStatus, fetchEpisodes]);

  const executeEpisodeAction = async (episodeId, actionType) => {
    if (actionType === 'retry') {
      await API.post(`/api/episode/retry/${episodeId}`);
    } else if (actionType === 'delete') {
      await API.delete(`/api/episode/${episodeId}`);
    } else if (actionType === 'cancel') {
      await API.post(`/api/episode/cancel/${episodeId}`);
    } else {
      throw new Error(`Unsupported action: ${actionType}`);
    }
  };

  const handleRetry = async (episodeId) => {
    try {
      await executeEpisodeAction(episodeId, 'retry');
      showSuccess(t('retry_submitted'));
      fetchEpisodes(currentPage);
    } catch (error) {
      console.error('Failed to retry episode:', error);
      showError(t('retry_failed'));
    }
  };

  const handleDelete = async (episodeId) => {
    try {
      await executeEpisodeAction(episodeId, 'delete');
      showSuccess(t('episode_deleted_success'));
      fetchEpisodes(currentPage);
    } catch (error) {
      console.error('Failed to delete episode:', error);
      showError(t('episode_delete_failed', { defaultValue: 'Failed to delete episode' }));
    }
  };

  const handleCancel = async (episodeId) => {
    try {
      await executeEpisodeAction(episodeId, 'cancel');
      showSuccess(t('episode_cancelled_successfully', { defaultValue: 'Pending episode cancelled' }));
      fetchEpisodes(currentPage);
    } catch (error) {
      console.error('Failed to cancel episode:', error);
      showError(t('cancel_failed', { defaultValue: 'Failed to cancel episode' }));
    }
  };

  const openBulkActionConfirm = () => {
    if (!bulkActionDefinition || episodes.length === 0) {
      return;
    }

    const { type } = bulkActionDefinition;
    const textKeys = bulkActionTextKeys[type];
    if (!textKeys) {
      return;
    }

    const aggregateCount = totalCount > 0 ? totalCount : episodes.length;

    const confirmMessage = t(textKeys.confirmKey, {
      count: aggregateCount,
      status: statusConfirmLabel,
    });

    setConfirmModal({
      opened: true,
      actionType: type,
      message: confirmMessage,
      textKeys,
      status: effectiveStatus,
      episodeIds: null,
    });
  };

  const closeConfirmModal = () => {
    if (bulkLoading) {
      return;
    }
    setConfirmModal({
      opened: false,
      actionType: null,
      message: '',
      textKeys: null,
      status: null,
      episodeIds: null,
    });
  };

  const performBulkAction = async () => {
    if (!confirmModal.actionType || !confirmModal.textKeys) {
      return;
    }

    setBulkLoading(true);

    try {
      const payload = {
        action: confirmModal.actionType,
        status: confirmModal.status || effectiveStatus,
      };

      if (confirmModal.episodeIds && confirmModal.episodeIds.length > 0) {
        payload.episodeIds = confirmModal.episodeIds;
      }

      await API.post('/api/episode/batch', payload);
      showSuccess(t(confirmModal.textKeys.successKey));
      if (currentPage !== 1) {
        setCurrentPage(1);
      } else {
        await fetchEpisodes(1);
      }
    } catch (error) {
      console.error('Failed to perform bulk action:', error);
      showError(t(confirmModal.textKeys.errorKey));
    } finally {
      setBulkLoading(false);
      setConfirmModal({
        opened: false,
        actionType: null,
        message: '',
        textKeys: null,
        status: null,
        episodeIds: null,
      });
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

  const cardHeight = isSmallScreen ? 75 : 100;

  return (
    <Container size="lg" py={isSmallScreen ? 'md' : 'xl'}>
      <Stack gap="lg">
        <Stack gap="xs">
          <Group justify="space-between" align="center">
            <Title order={isSmallScreen ? 4 : 3}>{statusLabel}</Title>
          </Group>
          {isSmallScreen ? (
            <Stack gap="xs">
              <Box style={{ width: '100%' }}>
                <SegmentedControl
                  value={effectiveStatus}
                  onChange={(value) => navigate(`/dashboard/episodes/${value.toLowerCase()}`)}
                  data={statusOptions}
                  size="sm"
                  fullWidth
                />
              </Box>
              {bulkActionDefinition ? (
                <Button
                  variant="outline"
                  size="xs"
                  color={bulkActionDefinition.color}
                  leftSection={BulkIcon ? <BulkIcon size={16} /> : undefined}
                  onClick={openBulkActionConfirm}
                  disabled={episodes.length === 0 || loading || bulkLoading}
                  loading={bulkLoading}
                  fullWidth
                >
                  {t(bulkActionTextKeys[bulkActionDefinition.type].labelKey)}
                </Button>
              ) : null}
            </Stack>
          ) : (
            <Group justify="flex-end" align="center" wrap="wrap" gap="sm">
              {bulkActionDefinition ? (
                <Button
                  variant="outline"
                  size="sm"
                  color={bulkActionDefinition.color}
                  leftSection={BulkIcon ? <BulkIcon size={16} /> : undefined}
              onClick={openBulkActionConfirm}
                  disabled={episodes.length === 0 || loading || bulkLoading}
                  loading={bulkLoading}
                >
                  {t(bulkActionTextKeys[bulkActionDefinition.type].labelKey)}
                </Button>
              ) : null}
              <SegmentedControl
                value={effectiveStatus}
                onChange={(value) => navigate(`/dashboard/episodes/${value.toLowerCase()}`)}
                data={statusOptions}
                size="md"
              />
            </Group>
          )}
        </Stack>

        {loading ? (
          <Center py="xl">
            <Loader />
          </Center>
        ) : episodes.length === 0 ? (
          <Center py="xl">
            <Text c="dimmed">
              {t('dashboard_no_episodes_for_status', {
                status: statusLabel,
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
          <Flex justify={"flex-end"} align={"center"}>
            <Pagination
              withEdges
              value={currentPage}
              onChange={setCurrentPage}
              total={totalPages}
              size="sm" />
          </Flex>
        ) : null}
      </Stack>

      <Modal
        opened={confirmModal.opened}
        onClose={closeConfirmModal}
        title={t('dashboard_bulk_confirm_title')}
        withCloseButton={!bulkLoading}
        closeOnEscape={!bulkLoading}
        closeOnClickOutside={!bulkLoading}
      >
        <Stack gap="md">
          <Text size="sm">{confirmModal.message}</Text>
          <Group justify="flex-end" gap="sm">
            <Button variant="default" onClick={closeConfirmModal} disabled={bulkLoading}>
              {t('cancel')}
            </Button>
            <Button color="red" onClick={performBulkAction} loading={bulkLoading}>
              {t('confirm')}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Container>
  );
};

export default DashboardEpisodes;
