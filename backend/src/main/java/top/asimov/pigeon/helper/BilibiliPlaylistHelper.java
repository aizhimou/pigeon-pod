package top.asimov.pigeon.helper;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.util.BilibiliIdUtil;
import top.asimov.pigeon.util.KeywordExpressionMatcher;

@Component
public class BilibiliPlaylistHelper {

  private static final int PAGE_SIZE = 50;

  private final BilibiliApiClient bilibiliApiClient;
  private final BilibiliResolverHelper bilibiliResolverHelper;

  public BilibiliPlaylistHelper(BilibiliApiClient bilibiliApiClient,
      BilibiliResolverHelper bilibiliResolverHelper) {
    this.bilibiliApiClient = bilibiliApiClient;
    this.bilibiliResolverHelper = bilibiliResolverHelper;
  }

  public PlaylistFetchResult fetchPlaylistByInput(String input) {
    BilibiliResolverHelper.PlaylistResolveResult resolved = bilibiliResolverHelper.resolvePlaylist(input);
    String type = resolved.type();
    String collectionId = resolved.collectionId();
    String mid = resolved.mid();

    if ("season".equals(type)) {
      if (!StringUtils.hasText(mid)) {
        throw new BusinessException("Bilibili season source must include UP mid");
      }
      SeasonPage firstPage = fetchSeasonPage(mid, collectionId, 1);
      List<Episode> episodes = mapArchives(firstPage.archives(), null, 0L);
      return new PlaylistFetchResult(
          BilibiliIdUtil.buildSeasonPlaylistId(collectionId),
          "season",
          mid,
          firstPage.title(),
          firstPage.description(),
          firstPage.coverUrl(),
          episodes);
    }

    SeriesMeta seriesMeta = fetchSeriesMeta(collectionId);
    String resolvedMid = StringUtils.hasText(mid) ? mid : seriesMeta.mid();
    SeriesPage firstPage = fetchSeriesPage(resolvedMid, collectionId, 1);
    List<Episode> episodes = mapArchives(firstPage.archives(), null, 0L);
    String coverUrl = !episodes.isEmpty()
        ? firstNonBlank(episodes.get(0).getMaxCoverUrl(), episodes.get(0).getDefaultCoverUrl())
        : null;
    if (!StringUtils.hasText(coverUrl)) {
      coverUrl = seriesMeta.coverUrl();
    }
    return new PlaylistFetchResult(
        BilibiliIdUtil.buildSeriesPlaylistId(collectionId),
        "series",
        resolvedMid,
        seriesMeta.name(),
        seriesMeta.description(),
        coverUrl,
        episodes);
  }

  public List<Episode> fetchPlaylistVideos(String playlistFeedId, String ownerMid, int maxPagesToCheck,
      String titleContainKeywords, String titleExcludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords,
      Integer minimumDuration, Integer maximumDuration) {
    String type = BilibiliIdUtil.extractCollectionType(playlistFeedId);
    String collectionId = BilibiliIdUtil.extractCollectionId(playlistFeedId);
    if (!StringUtils.hasText(type) || !StringUtils.hasText(collectionId)) {
      return List.of();
    }
    String effectiveMid = ownerMid;
    if ("series".equals(type) && !StringUtils.hasText(effectiveMid)) {
      effectiveMid = fetchSeriesMeta(collectionId).mid();
    }
    if (!StringUtils.hasText(effectiveMid)) {
      throw new BusinessException("Bilibili playlist owner mid is missing");
    }

    int pagesLimit = maxPagesToCheck <= 0 ? 1 : maxPagesToCheck;
    int totalPages = Integer.MAX_VALUE;
    List<Episode> result = new ArrayList<>();
    long offset = 0L;
    for (int page = 1; page <= pagesLimit && page <= totalPages; page++) {
      PageWithTotal pageData = "season".equals(type)
          ? fetchSeasonPage(effectiveMid, collectionId, page).toPageWithTotal()
          : fetchSeriesPage(effectiveMid, collectionId, page).toPageWithTotal();
      totalPages = pageData.totalPages();
      if (pageData.archives() == null || pageData.archives().isEmpty()) {
        break;
      }
      List<Episode> mapped = mapArchives(pageData.archives(), null, offset);
      offset += pageData.archives().size();
      for (Episode episode : mapped) {
        if (!matchesFilter(episode, titleContainKeywords, titleExcludeKeywords,
            descriptionContainKeywords, descriptionExcludeKeywords, minimumDuration, maximumDuration)) {
          continue;
        }
        result.add(episode);
      }
    }
    return result;
  }

  public List<Episode> fetchPlaylistHistoryPage(String playlistFeedId, String ownerMid, int pageIndex,
      String titleContainKeywords, String titleExcludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords,
      Integer minimumDuration, Integer maximumDuration) {
    String type = BilibiliIdUtil.extractCollectionType(playlistFeedId);
    String collectionId = BilibiliIdUtil.extractCollectionId(playlistFeedId);
    if (!StringUtils.hasText(type) || !StringUtils.hasText(collectionId)) {
      return List.of();
    }
    String effectiveMid = ownerMid;
    if ("series".equals(type) && !StringUtils.hasText(effectiveMid)) {
      effectiveMid = fetchSeriesMeta(collectionId).mid();
    }
    if (!StringUtils.hasText(effectiveMid)) {
      throw new BusinessException("Bilibili playlist owner mid is missing");
    }

    int safePage = Math.max(1, pageIndex);
    List<JsonNode> archives = "season".equals(type)
        ? fetchSeasonPage(effectiveMid, collectionId, safePage).archives()
        : fetchSeriesPage(effectiveMid, collectionId, safePage).archives();
    if (archives == null || archives.isEmpty()) {
      return List.of();
    }
    long offset = (long) (safePage - 1) * PAGE_SIZE;
    List<Episode> mapped = mapArchives(archives, null, offset);
    List<Episode> result = new ArrayList<>();
    for (Episode episode : mapped) {
      if (!matchesFilter(episode, titleContainKeywords, titleExcludeKeywords,
          descriptionContainKeywords, descriptionExcludeKeywords, minimumDuration, maximumDuration)) {
        continue;
      }
      result.add(episode);
    }
    return result;
  }

  private SeasonPage fetchSeasonPage(String mid, String seasonId, int pageNum) {
    JsonNode data = bilibiliApiClient.getData("/x/polymer/web-space/seasons_archives_list",
        java.util.Map.of(
            "season_id", seasonId,
            "mid", mid,
            "page_num", String.valueOf(pageNum),
            "page_size", String.valueOf(PAGE_SIZE)));
    JsonNode meta = data.path("meta");
    JsonNode page = data.path("page");
    int total = page.path("total").asInt(meta.path("total").asInt(0));
    return new SeasonPage(
        asArray(data.path("archives")),
        totalPages(total),
        meta.path("name").asText(""),
        meta.path("description").asText(""),
        normalizeImageUrl(meta.path("cover").asText("")),
        String.valueOf(meta.path("mid").asLong(0)));
  }

  private SeriesMeta fetchSeriesMeta(String seriesId) {
    JsonNode data = bilibiliApiClient.getData("/x/series/series",
        java.util.Map.of("series_id", seriesId));
    JsonNode meta = data.path("meta");
    return new SeriesMeta(
        String.valueOf(meta.path("mid").asLong(0)),
        meta.path("name").asText(""),
        meta.path("description").asText(""),
        null);
  }

  private SeriesPage fetchSeriesPage(String mid, String seriesId, int pageNum) {
    JsonNode data = bilibiliApiClient.getData("/x/series/archives",
        java.util.Map.of(
            "mid", mid,
            "series_id", seriesId,
            "ps", String.valueOf(PAGE_SIZE),
            "pn", String.valueOf(pageNum)));
    JsonNode page = data.path("page");
    int total = page.path("count").asInt(0);
    return new SeriesPage(asArray(data.path("archives")), totalPages(total));
  }

  private List<JsonNode> asArray(JsonNode node) {
    List<JsonNode> result = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return result;
    }
    node.forEach(result::add);
    return result;
  }

  private int totalPages(int total) {
    if (total <= 0) {
      return 1;
    }
    return (int) Math.ceil((double) total / PAGE_SIZE);
  }

  private List<Episode> mapArchives(List<JsonNode> archives, String channelFeedId, long offset) {
    if (archives == null || archives.isEmpty()) {
      return List.of();
    }
    List<Episode> result = new ArrayList<>();
    long index = 0L;
    for (JsonNode archive : archives) {
      index++;
      Episode episode = mapArchiveToEpisode(archive, channelFeedId, offset + index);
      if (episode != null) {
        result.add(episode);
      }
    }
    return result;
  }

  private Episode mapArchiveToEpisode(JsonNode archive, String channelFeedId, long position) {
    if (archive == null || archive.isMissingNode()) {
      return null;
    }
    if (archive.path("state").asInt(0) != 0) {
      return null;
    }
    String bvid = archive.path("bvid").asText(null);
    if (!StringUtils.hasText(bvid)) {
      return null;
    }
    int durationSeconds = archive.path("duration").asInt(-1);
    if (durationSeconds <= 0) {
      return null;
    }

    LocalDateTime publishedAt = LocalDateTime.ofInstant(
        Instant.ofEpochSecond(archive.path("pubdate").asLong(0)),
        ZoneId.systemDefault());
    String pic = normalizeImageUrl(archive.path("pic").asText(""));
    return Episode.builder()
        .id(bvid)
        .channelId(channelFeedId)
        .title(archive.path("title").asText(""))
        .description(archive.path("desc").asText(""))
        .publishedAt(publishedAt)
        .duration(Duration.ofSeconds(durationSeconds).toString())
        .position(position)
        .downloadStatus(EpisodeStatus.READY.name())
        .createdAt(LocalDateTime.now())
        .defaultCoverUrl(pic)
        .maxCoverUrl(pic)
        .build();
  }

  private boolean matchesFilter(Episode episode, String titleContainKeywords, String titleExcludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords,
      Integer minimumDuration, Integer maximumDuration) {
    if (KeywordExpressionMatcher.notMatchesKeywordFilter(
        episode.getTitle(), titleContainKeywords, titleExcludeKeywords)) {
      return false;
    }
    if (KeywordExpressionMatcher.notMatchesKeywordFilter(
        episode.getDescription(), descriptionContainKeywords, descriptionExcludeKeywords)) {
      return false;
    }
    return matchesDuration(episode.getDuration(), minimumDuration, maximumDuration);
  }

  private boolean matchesDuration(String duration, Integer minimumDuration, Integer maximumDuration) {
    if (!StringUtils.hasText(duration)) {
      return false;
    }
    try {
      long minutes = Duration.parse(duration).toMinutes();
      if (minimumDuration != null && minutes < minimumDuration) {
        return false;
      }
      if (maximumDuration != null && minutes > maximumDuration) {
        return false;
      }
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private String normalizeImageUrl(String raw) {
    if (!StringUtils.hasText(raw)) {
      return raw;
    }
    if (raw.startsWith("//")) {
      return "https:" + raw;
    }
    return raw;
  }

  private String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value;
      }
    }
    return null;
  }

  public record PlaylistFetchResult(String playlistId, String type, String ownerMid,
                                    String title, String description, String coverUrl,
                                    List<Episode> previewEpisodes) {

  }

  private record PageWithTotal(List<JsonNode> archives, int totalPages) {

  }

  private record SeasonPage(List<JsonNode> archives, int totalPages, String title,
                            String description, String coverUrl, String ownerMid) {

    private PageWithTotal toPageWithTotal() {
      return new PageWithTotal(archives, totalPages);
    }
  }

  private record SeriesMeta(String mid, String name, String description, String coverUrl) {

  }

  private record SeriesPage(List<JsonNode> archives, int totalPages) {

    private PageWithTotal toPageWithTotal() {
      return new PageWithTotal(archives, totalPages);
    }
  }
}

