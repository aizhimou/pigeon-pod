import React from 'react';
import { Grid, Card, Box, AspectRatio, Image, Badge, Text, Tooltip, ThemeIcon} from '@mantine/core';
import { IconBrandYoutubeFilled, IconBrandBilibili, IconBrandYoutube } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { formatDateWithPattern } from '../../helpers/utils';
import { useDateFormat } from '../../hooks/useDateFormat';
import './FeedCard.css';

const FeedCard = ({
  feed,
  onClick,
  dimmed = false,
  withTooltip = false,
  tooltipLabel = '',
}) => {
  const { t } = useTranslation();
  const dateFormat = useDateFormat();
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

  const coverImage = (
    <AspectRatio ratio={1}>
      <Image
        src={feed.customCoverUrl || feed.coverUrl}
        alt={feed.name}
        referrerPolicy="no-referrer"
        w="100%"
        h="100%"
        fit="cover"
      />
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
        {formatDateWithPattern(feed.lastPublishedAt, dateFormat)} {t('updated')}
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
