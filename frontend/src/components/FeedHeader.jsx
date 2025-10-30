import React from 'react';
import {
  Paper,
  Grid,
  Center,
  Box,
  Avatar,
  Badge,
  Tooltip,
  Group,
  Title,
  ActionIcon,
  Text,
  Flex,
  Button,
} from '@mantine/core';
import {
  IconBrandApplePodcast,
  IconSettings,
  IconBackspace,
  IconPencil,
  IconRotate,
} from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

const FeedHeader = ({
  feed,
  isSmallScreen,
  onSubscribe,
  onOpenConfig,
  onRefresh,
  refreshLoading = false,
  onConfirmDelete,
  onEditAppearance,
  actions,
  avatarSizeSmall = 100,
  avatarSizeLarge = 180,
  descriptionClampSmall = 3,
  descriptionClampLarge = 4,
}) => {
  const { t } = useTranslation();
  if (!feed) {
    return null;
  }

  const feedTypeKey = feed?.type
    ? `feed_type_${String(feed.type).toLowerCase()}`
    : 'feed_type_channel';
  const feedTypeLabel = t(feedTypeKey);
  const isPlaylist = feed?.type && String(feed.type).toLowerCase() === 'playlist';
  const badgeGradient = isPlaylist
    ? { from: '#2563eb', to: '#0ea5e9', deg: 90 }
    : { from: '#f97316', to: '#f43f5e', deg: 90 };
  const isSyncEnabled = feed?.syncState !== false;
  const pausedTooltip = t('feed_sync_paused_tooltip');
  const titleBaseStyle = {
    cursor: 'pointer',
    textDecoration: 'none',
    display: 'block',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    flex: 1,
    minWidth: 0,
  };
  const dimStyles = isSyncEnabled
    ? undefined
    : {
        filter: 'grayscale(0.8)',
        opacity: 0.6,
      };

  const avatarWithBadge = (
    <Box
      pos="relative"
      style={{
        display: 'inline-block',
        ...(dimStyles || {}),
      }}
    >
      <Avatar
        src={feed.customCoverUrl || feed.coverUrl}
        alt={feed.customTitle || feed.title}
        size={isSmallScreen ? avatarSizeSmall : avatarSizeLarge}
        radius="md"
        component="a"
        href={feed.originalUrl}
        target="_blank"
        rel="noopener noreferrer"
        style={{ cursor: 'pointer' }}
      />
      <Badge
        variant="gradient"
        gradient={badgeGradient}
        size="sm"
        radius="sm"
        style={{ position: 'absolute', bottom: 6, right: 6, opacity: 0.9, pointerEvents: 'none' }}
      >
        {feedTypeLabel}
      </Badge>
    </Box>
  );

  const defaultActions = [
    onSubscribe && {
      key: 'subscribe',
      label: t('subscribe'),
      leftSectionDesktop: <IconBrandApplePodcast size={16} />,
      leftSectionMobile: <IconBrandApplePodcast size={14} />,
      onClick: onSubscribe,
    },
    onOpenConfig && {
      key: 'config',
      label: t('config'),
      color: 'orange',
      leftSectionDesktop: <IconSettings size={16} />,
      leftSectionMobile: <IconSettings size={14} />,
      onClick: onOpenConfig,
    },
    onRefresh && {
      key: 'refresh',
      label: t('refresh'),
      color: 'teal',
      leftSectionDesktop: <IconRotate size={16} />,
      leftSectionMobile: <IconRotate size={14} />,
      onClick: onRefresh,
      loading: refreshLoading,
    },
    onConfirmDelete && {
      key: 'delete',
      label: t('delete'),
      color: 'pink',
      leftSectionDesktop: <IconBackspace size={16} />,
      leftSectionMobile: <IconBackspace size={14} />,
      onClick: onConfirmDelete,
    },
  ].filter(Boolean);

  const resolvedActions = actions && actions.length > 0 ? actions : defaultActions;
  const descriptionClamp = isSmallScreen ? descriptionClampSmall : descriptionClampLarge;

  const renderButtons = (sizeKey, visibleProps) => {
    if (!resolvedActions.length) {
      return null;
    }

    return (
      <Group {...visibleProps} gap="xs" wrap="no-wrap">
        {resolvedActions.map((action) => (
          <Button
            key={action.key || action.label}
            size={action[sizeKey] || 'xs'}
            color={action.color}
            variant={action.variant}
            leftSection={
              action[sizeKey === 'sizeDesktop' ? 'leftSectionDesktop' : 'leftSectionMobile'] ||
              action.leftSection
            }
            onClick={action.onClick}
            loading={action.loading}
            fullWidth={action.fullWidth && sizeKey === 'sizeMobile' ? action.fullWidth : action.fullWidthDesktop}
          >
            {action.label}
          </Button>
        ))}
      </Group>
    );
  };

  return (
    <Paper withBorder radius="md" mb="lg" p={{ base: 'xs', md: 'md', lg: 'lg' }}>
      <Grid>
        <Grid.Col span={{ base: 4, sm: 3 }}>
          <Center>
            {isSyncEnabled ? (
              avatarWithBadge
            ) : (
              <Tooltip label={pausedTooltip} withArrow>
                {avatarWithBadge}
              </Tooltip>
            )}
          </Center>
        </Grid.Col>

        <Grid.Col span={{ base: 8, sm: 9 }}>
          <Group mb={isSmallScreen ? '0' : 'sm'} wrap="nowrap" gap="xs" w="100%">
            {isSyncEnabled ? (
              <Title
                order={isSmallScreen ? 4 : 2}
                component="a"
                href={feed.originalUrl}
                target="_blank"
                rel="noopener noreferrer"
                style={{ ...titleBaseStyle, color: 'inherit' }}
              >
                {feed.customTitle || feed.title}
              </Title>
            ) : (
              <Tooltip label={pausedTooltip} withArrow>
                <Title
                  order={isSmallScreen ? 4 : 2}
                  component="a"
                  href={feed.originalUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ ...titleBaseStyle, color: 'var(--mantine-color-gray-6)' }}
                >
                  {feed.customTitle || feed.title}
                </Title>
              </Tooltip>
            )}
            {onEditAppearance ? (
              <ActionIcon
                variant="subtle"
                size="sm"
                aria-label="Edit title and cover"
                onClick={onEditAppearance}
                style={{ flexShrink: 0 }}
              >
                <IconPencil size={18} />
              </ActionIcon>
            ) : null}
          </Group>

          <Text
            size="sm"
            lineClamp={descriptionClamp}
            style={{ minHeight: isSmallScreen ? '2rem' : '4rem' }}
          >
            {feed.description ? feed.description : t('no_description_available')}
          </Text>

          {resolvedActions.length > 0 ? (
            <Flex visibleFrom="xs" gap="md" align="flex-center" mt="lg">
              {resolvedActions.map((action) => (
                <Button
                  key={action.key || action.label}
                  size={action.sizeDesktop || 'xs'}
                  color={action.color}
                  variant={action.variant}
                  leftSection={action.leftSectionDesktop || action.leftSection}
                  onClick={action.onClick}
                  loading={action.loading}
                >
                  {action.label}
                </Button>
              ))}
            </Flex>
          ) : null}
        </Grid.Col>
      </Grid>

      {resolvedActions.length > 0
        ? renderButtons('sizeMobile', { hiddenFrom: 'xs', mt: 'xs' })
        : null}
    </Paper>
  );
};

export default FeedHeader;
