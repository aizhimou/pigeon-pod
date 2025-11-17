import React from 'react';
import { Grid, Card, Box, AspectRatio, Image, Badge, Text, Tooltip } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import './FeedCard.css';

const FeedCard = ({
  feed,
  onClick,
  dimmed = false,
  withTooltip = false,
  tooltipLabel = '',
}) => {
  const { t } = useTranslation();
  const feedTypeKey = feed?.type
    ? `feed_type_${String(feed.type).toLowerCase()}`
    : 'feed_type_channel';
  const feedTypeLabel = t(feedTypeKey);
  const isPlaylist = feed?.type && String(feed.type).toLowerCase() === 'playlist';
  const badgeGradient = isPlaylist
    ? { from: '#2563eb', to: '#0ea5e9', deg: 90 }
    : { from: '#f97316', to: '#f43f5e', deg: 90 };

  const coverImage = (
    <AspectRatio ratio={1}>
      <Image src={feed.customCoverUrl || feed.coverUrl} alt={feed.name} w="100%" h="100%" fit="cover" />
    </AspectRatio>
  );

  const dimStyles = dimmed
    ? {
        filter: 'grayscale(0.8)',
        opacity: 0.6,
      }
    : undefined;

  const cardContent = (
    <Card
      shadow="sm"
      padding="sm"
      radius="sm"
      onClick={onClick}
      className="feed-card-hover"
      style={{ cursor: 'pointer', ...dimStyles }}
    >
      <Card.Section>
        <Box pos="relative">
          {coverImage}
          <Badge
            variant="gradient"
            gradient={badgeGradient}
            size="sm"
            radius="sm"
            style={{ position: 'absolute', bottom: 5, right: 5, opacity: 0.9 }}
          >
            {feedTypeLabel}
          </Badge>
        </Box>
      </Card.Section>
      <Text
        fw={500}
        mt="sm"
        size="sm"
        style={{
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          display: 'block',
        }}
      >
        {feed.customTitle || feed.title}
      </Text>
      <Text c="dimmed" size="xs">
        {new Date(feed.lastPublishedAt).toLocaleDateString()} {t('updated')}
      </Text>
    </Card>
  );

  return (
    <Grid.Col span={{ base: 6, xs: 4, sm: 3, md: 2, lg: 2, xl: 2 }}>
      {withTooltip ? (
        <Tooltip label={tooltipLabel} withArrow>
          {cardContent}
        </Tooltip>
      ) : (
        cardContent
      )}
    </Grid.Col>
  );
};

export default FeedCard;
