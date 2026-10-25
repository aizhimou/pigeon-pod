export const EPISODE_COVER_MODES = ['ORIGINAL', 'FEED_COVER', 'PAD', 'CROP'];
export const EPISODE_COVER_FILLS = ['PROGRESSIVE', 'BLUR', 'COLOR'];

export const DEFAULT_EPISODE_COVER_STYLE = {
  mode: 'ORIGINAL',
  fill: 'PROGRESSIVE',
  color: '#000000',
  blur: 70,
  fade: 30,
  tint: 50,
  tintColor: null,
  position: 50,
  zoom: 100,
  pan: 50,
};

/** Reads the JSON string stored on a feed into a complete style object for the form. */
export function parseEpisodeCoverStyle(value) {
  if (!value) return { ...DEFAULT_EPISODE_COVER_STYLE };
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value;
    const style = { ...DEFAULT_EPISODE_COVER_STYLE, ...parsed };
    if (!EPISODE_COVER_FILLS.includes(style.fill)) style.fill = DEFAULT_EPISODE_COVER_STYLE.fill;
    return style;
  } catch {
    return { ...DEFAULT_EPISODE_COVER_STYLE };
  }
}

/** Keeps only the settings that apply to the selected mode, mirroring the backend. */
export function compactEpisodeCoverStyle(style) {
  const { mode, fill, color, blur, fade, tint, tintColor, position, zoom, pan } = style;
  const zoomSettings = zoom > 100 ? { zoom, pan } : {};
  if (mode === 'CROP') return { mode, position, ...zoomSettings };
  if (mode !== 'PAD') return { mode };
  if (fill === 'COLOR') return { mode, fill, color, fade, position, ...zoomSettings };
  return {
    mode,
    fill,
    blur,
    fade,
    tint,
    position,
    ...(tintColor && { tintColor }),
    ...zoomSettings,
  };
}

/** Value sent to the API: null keeps the original thumbnails. */
export function serializeEpisodeCoverStyle(style) {
  if (!style || style.mode === 'ORIGINAL') return null;
  return JSON.stringify(compactEpisodeCoverStyle(style));
}

export function requiresCoverRendering(style) {
  return style?.mode === 'PAD' || style?.mode === 'CROP';
}

export function buildEpisodeCoverPreviewUrl(baseUrl, feedType, feedId, episodeId, style) {
  const params = new URLSearchParams({
    episodeId,
    style: JSON.stringify(compactEpisodeCoverStyle(style)),
  });
  return `${baseUrl || ''}/api/feed/${feedType}/${encodeURIComponent(feedId)}/episode-cover/preview?${params}`;
}
