import { parseEpisodeCoverStyle } from './episodeCoverStyle';

/** Title shown for an episode, after the user's per-episode edits. */
export function episodeTitle(episode) {
  return episode?.customTitle || episode?.title || '';
}

/** Description shown for an episode, after the user's per-episode edits. */
export function episodeDescription(episode) {
  return episode?.customDescription || episode?.description || '';
}

/** Thumbnail URL for an episode: the uploaded one if any, else the source thumbnail. */
export function episodeCoverSrc(episode) {
  if (!episode) return '';
  if (episode.customCoverExt) {
    const version = episode.customizedAt ? `?v=${encodeURIComponent(episode.customizedAt)}` : '';
    return `/media/${encodeURIComponent(episode.id)}/cover${version}`;
  }
  return episode.maxCoverUrl || episode.defaultCoverUrl || '';
}

/** Short, stable cache-busting token for a string. */
function shortHash(text) {
  let hash = 5381;
  for (let i = 0; i < text.length; i += 1) {
    hash = ((hash << 5) + hash + text.charCodeAt(i)) | 0;
  }
  return (hash >>> 0).toString(36);
}

/** Whether the feed shows square episode artwork (anything but the original thumbnails). */
export function hasSquareEpisodeArtwork(feed) {
  return parseEpisodeCoverStyle(feed?.episodeCoverStyle).mode !== 'ORIGINAL';
}

/**
 * Small cover for an episode as this feed shows it in podcast apps, following the feed's episode
 * artwork setting. {@code feedType} is "channel" or "playlist".
 */
export function feedEpisodeCoverSrc(episode, feed, feedType) {
  const { mode } = parseEpisodeCoverStyle(feed?.episodeCoverStyle);
  if (!episode || !feed || !feedType || mode === 'ORIGINAL') return episodeCoverSrc(episode);
  if (mode === 'FEED_COVER') {
    return feed.customCoverUrl || feed.coverUrl || episodeCoverSrc(episode);
  }
  const version = shortHash(`${feed.episodeCoverStyle}|${episode.customizedAt || ''}`);
  return `/media/feed/${feedType}/${encodeURIComponent(feed.id)}/episodes/${encodeURIComponent(
    episode.id,
  )}/cover.jpg?size=small&v=${version}`;
}

export function isEpisodeCustomized(episode) {
  return Boolean(episode?.customTitle || episode?.customDescription || episode?.customCoverExt);
}

const LINK_PATTERN = /<a href="([^"]+)">([^<]*)<\/a>/g;

/**
 * Splits RSS description text into plain-text and link parts, so it can be shown without
 * rendering any HTML from the source.
 */
export function splitDescriptionLinks(text) {
  const parts = [];
  let lastIndex = 0;
  for (const match of (text || '').matchAll(LINK_PATTERN)) {
    if (match.index > lastIndex) parts.push({ text: text.slice(lastIndex, match.index) });
    parts.push({ text: decodeEntities(match[2]), href: decodeEntities(match[1]) });
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < (text || '').length) parts.push({ text: text.slice(lastIndex) });
  return parts;
}

function decodeEntities(value) {
  return value
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&amp;', '&');
}
