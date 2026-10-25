package top.asimov.pigeon.model.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import top.asimov.pigeon.exception.BusinessException;

class PodcastCategoryTest {

  @Test
  void parsesCategoryAndSubcategory() {
    assertEquals(new PodcastCategory("Health & Fitness", "Nutrition"),
        PodcastCategory.parse("Health & Fitness|Nutrition"));
    assertEquals(new PodcastCategory("True Crime", null), PodcastCategory.parse("True Crime"));
  }

  @Test
  void blankOrMalformedValuesFallBackToTheDefault() {
    assertEquals(PodcastCategory.DEFAULT, PodcastCategory.parse(null));
    assertEquals(PodcastCategory.DEFAULT, PodcastCategory.parse("  "));
    assertEquals(PodcastCategory.DEFAULT, PodcastCategory.parse("a|b|c"));
  }

  @Test
  void normalizeTrimsAndDropsEmptySubcategory() {
    assertNull(PodcastCategory.normalize(""));
    assertEquals("Arts|Books", PodcastCategory.normalize(" Arts | Books "));
    assertEquals("Comedy", PodcastCategory.normalize("Comedy|"));
  }

  @Test
  void normalizeRejectsInvalidValues() {
    assertThrows(BusinessException.class, () -> PodcastCategory.normalize("|Books"));
    assertThrows(BusinessException.class, () -> PodcastCategory.normalize("a|b|c"));
    assertThrows(BusinessException.class, () -> PodcastCategory.normalize("x".repeat(65)));
  }
}
