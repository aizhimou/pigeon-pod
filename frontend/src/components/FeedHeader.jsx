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
  Loader,
  Stack,
} from '@mantine/core';
import {
  IconBrandApplePodcast,
  IconSettings,
  IconBackspace,
  IconPencil,
  IconRotate, IconBrandBilibili, IconBrandYoutubeFilled,
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
  footerRight,
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
    ? { from: 'green', to: 'lime', deg: 90 }
    : { from: 'yellow', to: 'orange', deg: 90 };
  const normalizedSource = String(feed?.source || 'YOUTUBE').toUpperCase();
  const isBilibiliSource = normalizedSource === 'BILIBILI';
  const sourceColor = isBilibiliSource ? '#0387bd' : '#ff0034';
  const isAutoDownloadEnabled = feed?.autoDownloadEnabled !== false;
  const pausedTooltip = t('auto_download_paused_tooltip');
  const titleBaseStyle = {
    cursor: 'pointer',
    color: 'inherit',
    textDecoration: 'none',
    display: 'block',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    minWidth: 0,
  };
  const dimStyles = isAutoDownloadEnabled
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
        imageProps={{ referrerPolicy: 'no-referrer' }}
        size={isSmallScreen ? avatarSizeSmall : avatarSizeLarge}
        radius="md"
        component="a"
        href={feed.originalUrl}
        target="_blank"
        rel="noopener noreferrer"
        style={{ cursor: 'pointer' }}
      />
      {isBilibiliSource ? (
        <IconBrandBilibili
          color={sourceColor}
          stroke={3}
          style={{
            position: 'absolute',
            top: 8,
            left: 8,
            pointerEvents: 'none',
          }}
        />
      ) : (
        <IconBrandYoutubeFilled
          color={sourceColor}
          stroke={3}
          style={{
            position: 'absolute',
            top: 8,
            left: 8,
            pointerEvents: 'none',
          }}
        />
      )}
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
    <Paper mb="lg">
      <Flex gap="md">
        <Box>
          {isAutoDownloadEnabled ? (
              avatarWithBadge
          ) : (
              <Tooltip label={pausedTooltip} withArrow>
                {avatarWithBadge}
              </Tooltip>
          )}
        </Box>

        <Stack gap={"xs"} style={{ flex: 1, minWidth: 0 }}>
          <Group>
            <Title
                order={isSmallScreen ? 4 : 2}
                component="a"
                href={feed.originalUrl}
                target="_blank"
                rel="noopener noreferrer"
                style={{ ...titleBaseStyle }}
            >
              {feed.customTitle || feed.title}
            </Title>
            {onRefresh ? (
                <ActionIcon
                    variant="subtle"
                    size="sm"
                    aria-label={t('refresh')}
                    onClick={onRefresh}
                    disabled={refreshLoading}
                    style={{ flexShrink: 0 }}
                >
                  {refreshLoading ? <Loader size="xs" /> : <IconRotate size={18} />}
                </ActionIcon>
            ) : null}
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
          <Group>
            <Text
                size="sm"
                lineClamp={descriptionClamp}
                style={{ minHeight: isSmallScreen ? '2rem' : '4rem' }}
            >
              {feed.description ? feed.description : t('no_description_available')}
            </Text>
          </Group>
          <Flex
            visibleFrom="xs"
            align="center"
            justify="space-between"
            w="100%"
            gap="md"
            wrap="nowrap"
          >
            {resolvedActions.length > 0 ? (
              <Group gap="xs" wrap="nowrap">
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
              </Group>
            ) : (
              <Box />
            )}
            {footerRight ? <Box style={{ marginLeft: 'auto', flexShrink: 0 }}>{footerRight}</Box> : null}
          </Flex>
        </Stack>
      </Flex>
      {resolvedActions.length > 0
        ? renderButtons('sizeMobile', { hiddenFrom: 'xs', mt: 'xs' })
        : null}
      {footerRight ? (
        <Box hiddenFrom="xs" mt="xs" w="100%">
          {footerRight}
        </Box>
      ) : null}
    </Paper>
  );
};

export default FeedHeader;
