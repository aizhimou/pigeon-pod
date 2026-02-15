package top.asimov.pigeon.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

public final class KeywordExpressionMatcher {

  private KeywordExpressionMatcher() {
  }

  /**
   * 统一关键字过滤规则： - 逗号`,`：OR（组间） - 加号`+`：AND（组内）
   *
   * <p>返回值语义与历史实现保持一致：true 表示“不匹配，应被过滤掉”。</p>
   */
  public static boolean notMatchesKeywordFilter(String text, String containExpression,
      String excludeExpression) {
    String normalizedText = normalizeText(text);

    List<List<String>> containGroups = parseExpression(containExpression);
    if (!containGroups.isEmpty() && !matchesExpression(normalizedText, containGroups)) {
      return true;
    }

    List<List<String>> excludeGroups = parseExpression(excludeExpression);
    return !excludeGroups.isEmpty() && matchesExpression(normalizedText, excludeGroups);
  }

  static List<List<String>> parseExpression(String expression) {
    if (!StringUtils.hasText(expression)) {
      return Collections.emptyList();
    }

    List<List<String>> groups = new ArrayList<>();
    String[] rawGroups = expression.split(",");
    for (String rawGroup : rawGroups) {
      if (!StringUtils.hasText(rawGroup)) {
        continue;
      }

      List<String> tokens = new ArrayList<>();
      String[] rawTokens = rawGroup.split("\\+");
      for (String rawToken : rawTokens) {
        String normalizedToken = normalizeText(rawToken);
        if (StringUtils.hasText(normalizedToken)) {
          tokens.add(normalizedToken);
        }
      }

      if (!tokens.isEmpty()) {
        groups.add(tokens);
      }
    }

    return groups;
  }

  static boolean matchesExpression(String normalizedText, List<List<String>> groups) {
    if (!StringUtils.hasText(normalizedText) || groups == null || groups.isEmpty()) {
      return false;
    }

    for (List<String> group : groups) {
      boolean allMatched = true;
      for (String token : group) {
        if (!normalizedText.contains(token)) {
          allMatched = false;
          break;
        }
      }
      if (allMatched) {
        return true;
      }
    }

    return false;
  }

  private static String normalizeText(String text) {
    if (text == null) {
      return "";
    }
    return text.trim().toLowerCase(Locale.ROOT);
  }
}
