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
import top.asimov.pigeon.model.entity.Episode;
import top.asimov.pigeon.model.enums.EpisodeStatus;
import top.asimov.pigeon.util.KeywordExpressionMatcher;

@Component
public class BilibiliChannelHelper {

  private static final int PAGE_SIZE = 50;

  private final BilibiliApiClient bilibiliApiClient;

  public BilibiliChannelHelper(BilibiliApiClient bilibiliApiClient) {
    this.bilibiliApiClient = bilibiliApiClient;
  }

  public UpProfile fetchUpProfile(String mid) {
    JsonNode data = bilibiliApiClient.getData("/x/web-interface/card", java.util.Map.of("mid", mid));
    JsonNode card = data.path("card");
    return new UpProfile(
        mid,
        card.path("name").asText(""),
        normalizeImageUrl(card.path("face").asText("")),
        card.path("sign").asText(""));
  }

  public List<Episode> fetchUpVideos(String channelFeedId, String mid, int maxPagesToCheck,
      String titleContainKeywords, String titleExcludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords,
      Integer minimumDuration, Integer maximumDuration) {
    int pagesLimit = maxPagesToCheck <= 0 ? 1 : maxPagesToCheck;
    List<Episode> result = new ArrayList<>();
    long positionCounter = 0L;
    int totalPages = Integer.MAX_VALUE;

    for (int page = 1; page <= pagesLimit && page <= totalPages; page++) {
      JsonNode data = bilibiliApiClient.getData("/x/series/recArchivesByKeywords",
          java.util.Map.of(
              "keywords", "",
              "mid", mid,
              "pn", String.valueOf(page),
              "ps", String.valueOf(PAGE_SIZE)));
      JsonNode pageNode = data.path("page");
      int total = pageNode.path("total").asInt(0);
      totalPages = total <= 0 ? page : (int) Math.ceil((double) total / PAGE_SIZE);

      JsonNode archives = data.path("archives");
      if (!archives.isArray() || archives.isEmpty()) {
        break;
      }

      for (JsonNode archive : archives) {
        positionCounter++;
        Episode episode = mapArchiveToEpisode(archive, channelFeedId, positionCounter);
        if (episode == null) {
          continue;
        }
        if (!matchesFilter(episode, titleContainKeywords, titleExcludeKeywords,
            descriptionContainKeywords, descriptionExcludeKeywords, minimumDuration, maximumDuration)) {
          continue;
        }
        result.add(episode);
      }
    }
    return result;
  }

  public List<Episode> fetchUpHistoryPage(String channelFeedId, String mid, int pageIndex,
      String titleContainKeywords, String titleExcludeKeywords,
      String descriptionContainKeywords, String descriptionExcludeKeywords,
      Integer minimumDuration, Integer maximumDuration) {
    int safePage = Math.max(1, pageIndex);
    JsonNode data = bilibiliApiClient.getData("/x/series/recArchivesByKeywords",
        java.util.Map.of(
            "keywords", "",
            "mid", mid,
            "pn", String.valueOf(safePage),
            "ps", String.valueOf(PAGE_SIZE)));
    JsonNode archives = data.path("archives");
    if (!archives.isArray() || archives.isEmpty()) {
      return List.of();
    }

    long positionStart = (long) (safePage - 1) * PAGE_SIZE;
    List<Episode> result = new ArrayList<>();
    long index = 0L;
    for (JsonNode archive : archives) {
      index++;
      Episode episode = mapArchiveToEpisode(archive, channelFeedId, positionStart + index);
      if (episode == null) {
        continue;
      }
      if (!matchesFilter(episode, titleContainKeywords, titleExcludeKeywords,
          descriptionContainKeywords, descriptionExcludeKeywords, minimumDuration, maximumDuration)) {
        continue;
      }
      result.add(episode);
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

  public record UpProfile(String mid, String name, String avatarUrl, String signature) {

  }
}

