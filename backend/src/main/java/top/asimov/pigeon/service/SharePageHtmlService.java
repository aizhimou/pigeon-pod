package top.asimov.pigeon.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import top.asimov.pigeon.model.response.PublicEpisodeShareResponse;

@Log4j2
@Service
public class SharePageHtmlService {

  private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title>.*?</title>");
  private static final int DESCRIPTION_LENGTH = 140;

  private final PublicEpisodeService publicEpisodeService;

  public SharePageHtmlService(PublicEpisodeService publicEpisodeService) {
    this.publicEpisodeService = publicEpisodeService;
  }

  public SharePageHtmlResult buildEpisodeSharePage(String episodeId, String requestUrl) {
    PublicEpisodeShareResponse episode = publicEpisodeService.getPublicEpisode(episodeId);
    String html = loadIndexTemplate();
    if (!StringUtils.hasText(html)) {
      html = buildMinimalHtmlShell();
    }

    String unavailableMessage = publicEpisodeService.localizeUnavailableMessage();
    String pageTitle = episode == null ? unavailableMessage + " | PigeonPod"
        : buildPageTitle(episode.getTitle());
    String description = episode == null ? unavailableMessage
        : summarizeDescription(episode.getDescription(), episode.getTitle());
    String shareUrl = StringUtils.hasText(requestUrl) ? requestUrl : "/share/episode/" + episodeId;
    String imageUrl = resolveAbsoluteUrl(episode == null ? null : episode.getCoverUrl(), shareUrl);

    String withTitle = replaceTitle(html, pageTitle);
    String finalHtml = injectHeadMeta(withTitle, pageTitle, description, imageUrl, shareUrl);
    HttpStatus status = episode == null ? HttpStatus.NOT_FOUND : HttpStatus.OK;
    return new SharePageHtmlResult(status, finalHtml);
  }

  private String buildPageTitle(String episodeTitle) {
    if (!StringUtils.hasText(episodeTitle)) {
      return "PigeonPod";
    }
    return episodeTitle.trim() + " | PigeonPod";
  }

  private String summarizeDescription(String description, String title) {
    String normalized = normalizeWhitespace(stripHtml(description));
    if (!StringUtils.hasText(normalized)) {
      if (StringUtils.hasText(title)) {
        return "Listen to " + title.trim() + " on PigeonPod";
      }
      return "Listen on PigeonPod";
    }
    if (normalized.length() <= DESCRIPTION_LENGTH) {
      return normalized;
    }
    return normalized.substring(0, DESCRIPTION_LENGTH).trim() + "...";
  }

  private String stripHtml(String raw) {
    if (!StringUtils.hasText(raw)) {
      return "";
    }
    return raw.replaceAll("<[^>]+>", " ");
  }

  private String normalizeWhitespace(String raw) {
    if (!StringUtils.hasText(raw)) {
      return "";
    }
    return raw.replaceAll("\\s+", " ").trim();
  }

  private String replaceTitle(String html, String title) {
    String escapedTitle = HtmlUtils.htmlEscape(title, StandardCharsets.UTF_8.name());
    if (TITLE_PATTERN.matcher(html).find()) {
      return TITLE_PATTERN.matcher(html).replaceFirst(
          Matcher.quoteReplacement("<title>" + escapedTitle + "</title>"));
    }
    return html;
  }

  private String injectHeadMeta(String html, String title, String description, String imageUrl,
      String shareUrl) {
    String escapedTitle = escapeHtml(title);
    String escapedDescription = escapeHtml(description);
    String escapedShareUrl = escapeHtml(shareUrl);
    String escapedImageUrl = escapeHtml(StringUtils.hasText(imageUrl) ? imageUrl : "/pigeonpod.svg");

    String metaBlock = """
        <meta name="description" content="%s" />
        <meta property="og:type" content="website" />
        <meta property="og:title" content="%s" />
        <meta property="og:description" content="%s" />
        <meta property="og:image" content="%s" />
        <meta property="og:url" content="%s" />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content="%s" />
        <meta name="twitter:description" content="%s" />
        <meta name="twitter:image" content="%s" />
        <link rel="canonical" href="%s" />
        """.formatted(
        escapedDescription,
        escapedTitle,
        escapedDescription,
        escapedImageUrl,
        escapedShareUrl,
        escapedTitle,
        escapedDescription,
        escapedImageUrl,
        escapedShareUrl
    );

    int headCloseIndex = html.toLowerCase().lastIndexOf("</head>");
    if (headCloseIndex < 0) {
      return metaBlock + html;
    }
    return html.substring(0, headCloseIndex) + metaBlock + html.substring(headCloseIndex);
  }

  private String escapeHtml(String value) {
    return HtmlUtils.htmlEscape(StringUtils.hasText(value) ? value : "", StandardCharsets.UTF_8.name());
  }

  private String resolveAbsoluteUrl(String url, String requestUrl) {
    if (StringUtils.hasText(url) && (url.startsWith("http://") || url.startsWith("https://"))) {
      return url;
    }
    if (!StringUtils.hasText(requestUrl)) {
      return StringUtils.hasText(url) ? url : "/pigeonpod.svg";
    }

    int pathStart = requestUrl.indexOf('/', requestUrl.indexOf("://") + 3);
    String origin = pathStart >= 0 ? requestUrl.substring(0, pathStart) : requestUrl;
    if (!StringUtils.hasText(url)) {
      return origin + "/pigeonpod.svg";
    }
    if (url.startsWith("/")) {
      return origin + url;
    }
    return origin + "/" + url;
  }

  private String loadIndexTemplate() {
    try {
      ClassPathResource classPathResource = new ClassPathResource("static/index.html");
      if (classPathResource.exists()) {
        return classPathResource.getContentAsString(StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      log.warn("Captain failed to read classpath static index.html", e);
    }

    try {
      Path frontendIndexPath = Path.of("frontend", "index.html");
      if (Files.exists(frontendIndexPath)) {
        return Files.readString(frontendIndexPath, StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      log.warn("Captain failed to read frontend/index.html", e);
    }
    return "";
  }

  private String buildMinimalHtmlShell() {
    return """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <title>PigeonPod</title>
          </head>
          <body>
            <div id="root"></div>
          </body>
        </html>
        """;
  }

  public record SharePageHtmlResult(HttpStatus status, String html) {
  }
}
