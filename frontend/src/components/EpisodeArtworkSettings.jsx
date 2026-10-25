import React, { useEffect, useRef, useState } from 'react';
import {
  AspectRatio,
  Box,
  Center,
  ColorInput,
  Divider,
  Image,
  Input,
  SegmentedControl,
  SimpleGrid,
  Skeleton,
  Slider,
  Stack,
  Text,
} from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { useTranslation } from 'react-i18next';
import { API } from '../helpers';
import { episodeCoverSrc, episodeTitle } from '../helpers/episodeDisplay';
import {
  buildEpisodeCoverPreviewUrl,
  compactEpisodeCoverStyle,
  requiresCoverRendering,
} from '../helpers/episodeCoverStyle';

const PREVIEW_COUNT = 3;
const PREVIEW_DEBOUNCE_MS = 300;
const DEFAULT_CUSTOM_TINT = '#1d2b4f';
const COLOR_SWATCHES = [
  '#000000',
  '#1a1b1e',
  '#25262b',
  '#ffffff',
  '#1d2b4f',
  '#2a1f4f',
  '#3b2a1a',
];

function pickPreviewEpisodes(episodes) {
  const list = episodes || [];
  const downloaded = list.filter((episode) => episode.downloadStatus === 'COMPLETED');
  const others = list.filter((episode) => episode.downloadStatus !== 'COMPLETED');
  return [...downloaded, ...others].slice(0, PREVIEW_COUNT);
}

/** Slider whose value is shown next to its label, so no tooltip covers the text above it. */
function PercentSlider({ label, description, value, onChange, marks, min = 0, max = 100 }) {
  return (
    <Input.Wrapper label={`${label} · ${value}%`} description={description}>
      <Slider
        mt={6}
        mb={marks ? 'lg' : 0}
        mx={marks ? 'lg' : 0}
        min={min}
        max={max}
        value={value}
        onChange={onChange}
        label={null}
        marks={marks}
      />
    </Input.Wrapper>
  );
}

function PreviewTile({ src, title, fit, failedLabel }) {
  const [status, setStatus] = useState(src ? 'loading' : 'error');
  const hasLoadedRef = useRef(false);

  useEffect(() => {
    setStatus(src ? 'loading' : 'error');
  }, [src]);

  const handleLoad = () => {
    hasLoadedRef.current = true;
    setStatus('loaded');
  };

  const isFirstLoad = status === 'loading' && !hasLoadedRef.current;
  const isRefreshing = status === 'loading' && hasLoadedRef.current;

  return (
    <Stack gap={4}>
      <AspectRatio ratio={1}>
        <Box
          pos="relative"
          bg="var(--mantine-color-default-hover)"
          style={{ borderRadius: 'var(--mantine-radius-md)', overflow: 'hidden' }}
        >
          {src && status !== 'error' && (
            <Image
              src={src}
              alt={title}
              fit={fit}
              w="100%"
              h="100%"
              onLoad={handleLoad}
              onError={() => setStatus('error')}
              style={{ opacity: isRefreshing ? 0.55 : 1, transition: 'opacity 150ms ease' }}
            />
          )}
          {isFirstLoad && <Skeleton pos="absolute" inset={0} radius={0} />}
          {status === 'error' && (
            <Center pos="absolute" inset={0}>
              <Text size="xs" c="dimmed">
                {failedLabel}
              </Text>
            </Center>
          )}
        </Box>
      </AspectRatio>
      <Text size="xs" c="dimmed" lineClamp={1} title={title}>
        {title}
      </Text>
    </Stack>
  );
}

const EpisodeArtworkSettings = ({ value, onChange, feedType, feedId, feedCoverUrl, episodes }) => {
  const { t } = useTranslation();
  const [debouncedStyleKey] = useDebouncedValue(
    JSON.stringify(compactEpisodeCoverStyle(value)),
    PREVIEW_DEBOUNCE_MS,
  );
  const previewStyle = JSON.parse(debouncedStyleKey);
  const previewEpisodes = pickPreviewEpisodes(episodes);

  const updateStyle = (field, fieldValue) => onChange({ ...value, [field]: fieldValue });

  // Kept apart from the value so clearing the color picker doesn't switch back to "From image"
  const [isCustomTint, setIsCustomTint] = useState(Boolean(value.tintColor));
  const handleTintSourceChange = (source) => {
    const isCustom = source === 'CUSTOM';
    setIsCustomTint(isCustom);
    updateStyle('tintColor', isCustom ? value.tintColor || DEFAULT_CUSTOM_TINT : null);
  };

  const isZoomed = value.zoom > 100;
  const centerLabel = t('episode_artwork_center', { defaultValue: 'Center' });
  const horizontalMarks = [
    { value: 0, label: t('episode_artwork_left', { defaultValue: 'Left' }) },
    { value: 50, label: centerLabel },
    { value: 100, label: t('episode_artwork_right', { defaultValue: 'Right' }) },
  ];
  const verticalMarks = [
    { value: 0, label: t('episode_artwork_top', { defaultValue: 'Top' }) },
    { value: 50, label: centerLabel },
    { value: 100, label: t('episode_artwork_bottom', { defaultValue: 'Bottom' }) },
  ];
  const horizontalPositionLabel = t('episode_artwork_horizontal_position', {
    defaultValue: 'Horizontal position',
  });
  const zoomSlider = (
    <PercentSlider
      label={t('episode_artwork_zoom', { defaultValue: 'Zoom' })}
      description={t('episode_artwork_zoom_desc', {
        defaultValue: 'Enlarges the thumbnail. The parts that no longer fit are trimmed.',
      })}
      min={100}
      max={300}
      value={value.zoom}
      onChange={(zoom) => updateStyle('zoom', zoom)}
    />
  );

  const modeHints = {
    ORIGINAL: t('episode_artwork_original_hint', {
      defaultValue: 'Uses the thumbnail as is. Most podcast apps crop or squeeze it.',
    }),
    FEED_COVER: t('episode_artwork_feed_cover_hint', {
      defaultValue: 'Every episode shows the podcast cover instead of its own thumbnail.',
    }),
    PAD: t('episode_artwork_pad_hint', {
      defaultValue: 'Shows the whole thumbnail on a square background.',
    }),
    CROP: t('episode_artwork_crop_hint', {
      defaultValue: 'Cuts the thumbnail to a square. Choose which part to keep.',
    }),
  };

  const buildPreviewSource = (episode) => {
    if (previewStyle.mode === 'FEED_COVER') return feedCoverUrl;
    if (!requiresCoverRendering(previewStyle)) return episodeCoverSrc(episode);
    return buildEpisodeCoverPreviewUrl(
      API.defaults.baseURL,
      feedType,
      feedId,
      episode.id,
      previewStyle,
    );
  };

  return (
    <Stack gap="sm">
      <Divider
        label={t('episode_artwork', { defaultValue: 'Episode artwork' })}
        labelPosition="left"
      />

      <Input.Wrapper
        description={t('episode_artwork_desc', {
          defaultValue:
            'Podcast apps expect square episode images, while video thumbnails are wide. Choose how they are shown in the RSS feed.',
        })}
      >
        <SegmentedControl
          mt="xs"
          fullWidth
          value={value.mode}
          onChange={(mode) => updateStyle('mode', mode)}
          data={[
            {
              value: 'ORIGINAL',
              label: t('episode_artwork_original', { defaultValue: 'Original' }),
            },
            {
              value: 'FEED_COVER',
              label: t('episode_artwork_feed_cover', { defaultValue: 'Feed cover' }),
            },
            { value: 'PAD', label: t('episode_artwork_pad', { defaultValue: 'Pad' }) },
            { value: 'CROP', label: t('episode_artwork_crop', { defaultValue: 'Crop' }) },
          ]}
        />
      </Input.Wrapper>
      <Text size="xs" c="dimmed">
        {modeHints[value.mode]}
      </Text>

      {value.mode === 'PAD' && (
        <>
          <Input.Wrapper label={t('episode_artwork_background', { defaultValue: 'Background' })}>
            <SegmentedControl
              mt={4}
              fullWidth
              size="xs"
              value={value.fill}
              onChange={(fill) => updateStyle('fill', fill)}
              data={[
                {
                  value: 'PROGRESSIVE',
                  label: t('episode_artwork_fill_progressive', {
                    defaultValue: 'Progressive blur',
                  }),
                },
                {
                  value: 'BLUR',
                  label: t('episode_artwork_fill_blur', { defaultValue: 'Blurred image' }),
                },
                {
                  value: 'COLOR',
                  label: t('episode_artwork_fill_color', { defaultValue: 'Color' }),
                },
              ]}
            />
          </Input.Wrapper>

          <PercentSlider
            label={t('episode_artwork_image_position', { defaultValue: 'Image position' })}
            value={value.position}
            onChange={(position) => updateStyle('position', position)}
            marks={verticalMarks}
          />
          {zoomSlider}
          {isZoomed && (
            <PercentSlider
              label={horizontalPositionLabel}
              value={value.pan}
              onChange={(pan) => updateStyle('pan', pan)}
              marks={horizontalMarks}
            />
          )}

          {value.fill === 'COLOR' ? (
            <ColorInput
              label={t('episode_artwork_color', { defaultValue: 'Background color' })}
              format="hex"
              swatches={COLOR_SWATCHES}
              value={value.color}
              onChange={(color) => updateStyle('color', color)}
            />
          ) : (
            <>
              <PercentSlider
                label={t('episode_artwork_blur', { defaultValue: 'Blur' })}
                value={value.blur}
                onChange={(blur) => updateStyle('blur', blur)}
              />
              <Input.Wrapper
                label={t('episode_artwork_tint', { defaultValue: 'Color wash' })}
                description={t('episode_artwork_tint_desc', {
                  defaultValue: 'Tints the background, strongest at its outer edge.',
                })}
              >
                <Stack gap="xs" mt={6}>
                  <SegmentedControl
                    fullWidth
                    size="xs"
                    value={isCustomTint ? 'CUSTOM' : 'AUTO'}
                    onChange={handleTintSourceChange}
                    data={[
                      {
                        value: 'AUTO',
                        label: t('episode_artwork_tint_auto', { defaultValue: 'From image' }),
                      },
                      {
                        value: 'CUSTOM',
                        label: t('episode_artwork_tint_custom', { defaultValue: 'Custom color' }),
                      },
                    ]}
                  />
                  {isCustomTint && (
                    <ColorInput
                      aria-label={t('episode_artwork_tint_custom', {
                        defaultValue: 'Custom color',
                      })}
                      format="hex"
                      swatches={COLOR_SWATCHES}
                      value={value.tintColor || ''}
                      onChange={(tintColor) => updateStyle('tintColor', tintColor || null)}
                    />
                  )}
                </Stack>
              </Input.Wrapper>
              <PercentSlider
                label={t('episode_artwork_tint_intensity', { defaultValue: 'Wash intensity' })}
                value={value.tint}
                onChange={(tint) => updateStyle('tint', tint)}
              />
            </>
          )}

          <PercentSlider
            label={t('episode_artwork_soft_edge', { defaultValue: 'Soft edge' })}
            description={t('episode_artwork_soft_edge_desc', {
              defaultValue: 'Blends the thumbnail into the background instead of a hard line.',
            })}
            value={value.fade}
            onChange={(fade) => updateStyle('fade', fade)}
          />
        </>
      )}

      {value.mode === 'CROP' && (
        <>
          {zoomSlider}
          <PercentSlider
            label={horizontalPositionLabel}
            value={value.position}
            onChange={(position) => updateStyle('position', position)}
            marks={horizontalMarks}
          />
          {isZoomed && (
            <PercentSlider
              label={t('episode_artwork_vertical_position', { defaultValue: 'Vertical position' })}
              value={value.pan}
              onChange={(pan) => updateStyle('pan', pan)}
              marks={verticalMarks}
            />
          )}
        </>
      )}

      <Input.Wrapper label={t('episode_artwork_preview', { defaultValue: 'Preview' })}>
        {previewEpisodes.length === 0 ? (
          <Text size="xs" c="dimmed" mt={4}>
            {t('episode_artwork_preview_empty', {
              defaultValue: 'Previews appear once this podcast has episodes.',
            })}
          </Text>
        ) : (
          <SimpleGrid cols={PREVIEW_COUNT} spacing="xs" mt={4}>
            {previewEpisodes.map((episode) => (
              <PreviewTile
                key={episode.id}
                src={buildPreviewSource(episode)}
                title={episodeTitle(episode)}
                fit={previewStyle.mode === 'ORIGINAL' ? 'contain' : 'cover'}
                failedLabel={t('episode_artwork_preview_failed', { defaultValue: 'No preview' })}
              />
            ))}
          </SimpleGrid>
        )}
      </Input.Wrapper>
    </Stack>
  );
};

export default EpisodeArtworkSettings;
