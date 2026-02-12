package top.asimov.pigeon.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeywordExpressionMatcherTest {

  @Test
  void shouldMatchLegacyOrSyntaxForContain() {
    assertFalse(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "This is a RAW episode",
        "raw,smackdown",
        null));
    assertTrue(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "This is a NXT episode",
        "raw,smackdown",
        null));
  }

  @Test
  void shouldMatchAndOrCombinationForContain() {
    String contain = "raw+full highlights,smackdown+full highlights";

    assertFalse(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "xxxx raw xxx xxx xx full highlights",
        contain,
        null));
    assertFalse(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "xx xxx smackdown xxxx xx full highlights",
        contain,
        null));

    assertTrue(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "xxx xx raw xxxx",
        contain,
        null));
    assertTrue(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "xxxx xx xxxx smackdown xxxx",
        contain,
        null));
    assertTrue(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "xxxx full highlights xxxx xx",
        contain,
        null));
  }

  @Test
  void shouldApplyExcludeWithSameSyntax() {
    String exclude = "reaction+full highlights,rumor";

    assertTrue(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "wwe reaction and full highlights",
        null,
        exclude));
    assertTrue(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "breaking rumor from today",
        null,
        exclude));
    assertFalse(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "reaction only without highlights",
        null,
        exclude));
  }

  @Test
  void shouldIgnoreEmptyTokensAndSpaces() {
    String contain = "  raw + full highlights , , smackdown + full highlights  ";

    assertFalse(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "RAW xx full highlights",
        contain,
        null));
    assertFalse(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "smackdown yy full highlights",
        contain,
        null));
  }

  @Test
  void shouldHandleMixedContainAndExclude() {
    String contain = "raw+full highlights,smackdown+full highlights";
    String exclude = "rumor,fan cam";

    assertFalse(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "raw full highlights official",
        contain,
        exclude));
    assertTrue(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "raw full highlights rumor",
        contain,
        exclude));
  }

  @Test
  void shouldSupportNonAsciiKeywords() {
    assertFalse(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "这是一条 WWE 精彩集锦",
        "wwe+精彩集锦",
        null));
    assertTrue(KeywordExpressionMatcher.notMatchesKeywordFilter(
        "这是一条 WWE 回放",
        "wwe+精彩集锦",
        null));
  }
}
