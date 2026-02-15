package top.asimov.pigeon.service;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import top.asimov.pigeon.mapper.YoutubeApiDailyUsageMapper;
import top.asimov.pigeon.mapper.YoutubeApiDailyUsageMethodMapper;
import top.asimov.pigeon.model.entity.YoutubeApiDailyUsage;
import top.asimov.pigeon.model.entity.YoutubeApiDailyUsageMethod;
import top.asimov.pigeon.model.enums.YoutubeApiCallContext;
import top.asimov.pigeon.model.enums.YoutubeApiMethod;
import top.asimov.pigeon.model.response.YoutubeQuotaMethodUsageResponse;
import top.asimov.pigeon.model.response.YoutubeQuotaTodayResponse;

@Log4j2
@Service
public class YoutubeQuotaService {

  private static final ZoneId PACIFIC_ZONE_ID = ZoneId.of("America/Los_Angeles");
  private static final String BLOCK_REASON_LOCAL_LIMIT = "LOCAL_LIMIT_REACHED";
  private static final String BLOCK_REASON_REMOTE_LIMIT = "REMOTE_QUOTA_EXCEEDED";

  private final YoutubeApiDailyUsageMapper dailyUsageMapper;
  private final YoutubeApiDailyUsageMethodMapper dailyUsageMethodMapper;
  private final SystemConfigService systemConfigService;

  public YoutubeQuotaService(YoutubeApiDailyUsageMapper dailyUsageMapper,
      YoutubeApiDailyUsageMethodMapper dailyUsageMethodMapper,
      SystemConfigService systemConfigService) {
    this.dailyUsageMapper = dailyUsageMapper;
    this.dailyUsageMethodMapper = dailyUsageMethodMapper;
    this.systemConfigService = systemConfigService;
  }

  @Transactional
  public boolean reserveAndRecord(YoutubeApiMethod method, YoutubeApiCallContext callContext) {
    String usageDatePt = todayPtString();
    Integer dailyLimitUnits = resolveDailyLimitUnits();

    dailyUsageMapper.ensureDayRow(usageDatePt);

    if (callContext == YoutubeApiCallContext.AUTO_SYNC && isAutoSyncBlockedToday()) {
      return false;
    }

    int affected;
    if (callContext == YoutubeApiCallContext.AUTO_SYNC && hasDailyLimit(dailyLimitUnits)) {
      affected = dailyUsageMapper.tryIncrementUsageWithinLimit(usageDatePt, method.quotaCost(),
          dailyLimitUnits);
      if (affected <= 0) {
        dailyUsageMapper.blockAutoSync(usageDatePt, BLOCK_REASON_LOCAL_LIMIT);
        log.warn("YouTube 配额达到上限，自动同步已阻断。date={}, limit={}", usageDatePt, dailyLimitUnits);
        return false;
      }
    } else {
      affected = dailyUsageMapper.incrementUsage(usageDatePt, method.quotaCost());
    }

    if (affected <= 0) {
      return false;
    }

    dailyUsageMethodMapper.incrementUsage(usageDatePt, method.methodName(), method.quotaCost());
    return true;
  }

  @Transactional
  public void markAutoSyncBlockedByRemoteQuota() {
    String usageDatePt = todayPtString();
    dailyUsageMapper.ensureDayRow(usageDatePt);
    dailyUsageMapper.blockAutoSync(usageDatePt, BLOCK_REASON_REMOTE_LIMIT);
  }

  public boolean isAutoSyncBlockedToday() {
    YoutubeApiDailyUsage usage = dailyUsageMapper.selectByDate(todayPtString());
    return usage != null && usage.getAutoSyncBlocked() != null && usage.getAutoSyncBlocked() == 1;
  }

  public YoutubeQuotaTodayResponse getTodayUsage() {
    String usageDatePt = todayPtString();
    YoutubeApiDailyUsage usage = dailyUsageMapper.selectByDate(usageDatePt);
    List<YoutubeApiDailyUsageMethod> methodUsages = dailyUsageMethodMapper.selectByDate(usageDatePt);

    int requestCount = usage == null || usage.getRequestCount() == null ? 0 : usage.getRequestCount();
    int usedUnits = usage == null || usage.getQuotaUnits() == null ? 0 : usage.getQuotaUnits();
    boolean autoSyncBlocked =
        usage != null && usage.getAutoSyncBlocked() != null && usage.getAutoSyncBlocked() == 1;

    Integer dailyLimitUnits = resolveDailyLimitUnits();
    Integer remainingUnits = null;
    boolean warningReached = false;
    if (hasDailyLimit(dailyLimitUnits)) {
      remainingUnits = Math.max(0, dailyLimitUnits - usedUnits);
      warningReached = usedUnits >= Math.ceil(dailyLimitUnits * 0.8);
    }

    List<YoutubeQuotaMethodUsageResponse> breakdown;
    if (CollectionUtils.isEmpty(methodUsages)) {
      breakdown = Collections.emptyList();
    } else {
      breakdown = methodUsages.stream()
          .map(item -> YoutubeQuotaMethodUsageResponse.builder()
              .apiMethod(item.getApiMethod())
              .requestCount(item.getRequestCount())
              .quotaUnits(item.getQuotaUnits())
              .build())
          .toList();
    }

    return YoutubeQuotaTodayResponse.builder()
        .usageDatePt(usageDatePt)
        .dailyLimitUnits(dailyLimitUnits)
        .requestCount(requestCount)
        .usedUnits(usedUnits)
        .remainingUnits(remainingUnits)
        .autoSyncBlocked(autoSyncBlocked)
        .blockedReason(usage == null ? null : usage.getBlockedReason())
        .warningReached(warningReached)
        .methodBreakdown(breakdown)
        .build();
  }

  public boolean isQuotaExceededError(GoogleJsonResponseException e) {
    if (e == null || e.getDetails() == null) {
      return false;
    }

    GoogleJsonError details = e.getDetails();
    if (details.getErrors() != null) {
      for (GoogleJsonError.ErrorInfo errorInfo : details.getErrors()) {
        if (errorInfo == null || errorInfo.getReason() == null) {
          continue;
        }
        String reason = errorInfo.getReason();
        if ("quotaExceeded".equals(reason) || "dailyLimitExceeded".equals(reason)) {
          return true;
        }
      }
    }

    String message = details.getMessage();
    return message != null && message.toLowerCase().contains("quota");
  }

  private Integer resolveDailyLimitUnits() {
    Integer limitUnits = systemConfigService.getYoutubeDailyLimitUnits();
    if (limitUnits == null || limitUnits <= 0) {
      return null;
    }
    return limitUnits;
  }

  private boolean hasDailyLimit(Integer dailyLimitUnits) {
    return dailyLimitUnits != null && dailyLimitUnits > 0;
  }

  private String todayPtString() {
    return LocalDate.now(PACIFIC_ZONE_ID).toString();
  }
}
