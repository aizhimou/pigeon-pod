package top.asimov.pigeon.model.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.enums.EpisodeCoverFill;
import top.asimov.pigeon.model.enums.EpisodeCoverMode;

class EpisodeCoverStyleTest {

  @Test
  void parseFallsBackToOriginalForBlankOrBrokenValues() {
    assertEquals(EpisodeCoverStyle.ORIGINAL, EpisodeCoverStyle.parse(null));
    assertEquals(EpisodeCoverStyle.ORIGINAL, EpisodeCoverStyle.parse(""));
    assertEquals(EpisodeCoverStyle.ORIGINAL, EpisodeCoverStyle.parse("{not json"));
    assertEquals(EpisodeCoverStyle.ORIGINAL, EpisodeCoverStyle.parse("{\"mode\":\"SPIRAL\"}"));
  }

  @Test
  void padStyleDefaultsToProgressiveBlur() {
    EpisodeCoverStyle style = EpisodeCoverStyle.parse("{\"mode\":\"PAD\"}");

    assertEquals(EpisodeCoverMode.PAD, style.mode());
    assertEquals(EpisodeCoverFill.PROGRESSIVE, style.fill());
    assertEquals(EpisodeCoverStyle.DEFAULT_BLUR, style.blur());
    assertEquals(EpisodeCoverStyle.DEFAULT_FADE, style.fade());
    assertEquals(EpisodeCoverStyle.DEFAULT_TINT, style.tint());
    assertEquals(EpisodeCoverStyle.DEFAULT_POSITION, style.position());
    assertNull(style.color());
    assertTrue(style.requiresRendering());
  }

  @Test
  void padStyleKeepsImagePosition() {
    EpisodeCoverStyle style = EpisodeCoverStyle.parse("{\"mode\":\"PAD\",\"position\":10}");

    assertEquals(10, style.position());
  }

  @Test
  void unknownFillFallsBackToProgressive() {
    EpisodeCoverStyle style = EpisodeCoverStyle.parse("{\"mode\":\"PAD\",\"fill\":\"EDGE\"}");

    assertEquals(EpisodeCoverFill.PROGRESSIVE, style.fill());
  }

  @Test
  void colorFillKeepsValidHexAndClampsPercentages() {
    EpisodeCoverStyle style = EpisodeCoverStyle.parse(
        "{\"mode\":\"PAD\",\"fill\":\"COLOR\",\"color\":\"#1D2B4F\",\"blur\":80,\"fade\":250,"
            + "\"tint\":40}");

    assertEquals("#1d2b4f", style.color());
    assertNull(style.blur());
    assertNull(style.tint());
    assertEquals(100, style.fade());
  }

  @Test
  void invalidColorFallsBackToDefault() {
    EpisodeCoverStyle style = EpisodeCoverStyle.parse(
        "{\"mode\":\"PAD\",\"fill\":\"COLOR\",\"color\":\"red; background:url(x)\"}");

    assertEquals(EpisodeCoverStyle.DEFAULT_COLOR, style.color());
  }

  @Test
  void cropKeepsOnlyClampedPosition() {
    EpisodeCoverStyle style = EpisodeCoverStyle.parse(
        "{\"mode\":\"CROP\",\"position\":-20,\"fill\":\"COLOR\",\"fade\":30}");

    assertEquals(0, style.position());
    assertNull(style.fill());
    assertNull(style.fade());
  }

  @Test
  void zoomIsClampedAndOnlyStoredWhenZoomedIn() {
    EpisodeCoverStyle notZoomed = EpisodeCoverStyle.parse(
        "{\"mode\":\"CROP\",\"zoom\":100,\"pan\":10}");
    EpisodeCoverStyle tooFar = EpisodeCoverStyle.parse("{\"mode\":\"CROP\",\"zoom\":900}");
    EpisodeCoverStyle zoomed = EpisodeCoverStyle.parse(
        "{\"mode\":\"PAD\",\"zoom\":150,\"pan\":120}");

    assertNull(notZoomed.zoom());
    assertNull(notZoomed.pan());
    assertEquals(100, notZoomed.zoomPercent());
    assertEquals(50, notZoomed.panPercent());
    assertEquals(300, tooFar.zoom());
    assertEquals(50, tooFar.pan());
    assertEquals(150, zoomed.zoom());
    assertEquals(100, zoomed.pan());
  }

  @Test
  void unzoomedStylesKeepTheirFingerprint() {
    assertEquals(EpisodeCoverStyle.parse("{\"mode\":\"CROP\",\"position\":30}").fingerprint(),
        EpisodeCoverStyle.parse("{\"mode\":\"CROP\",\"position\":30,\"zoom\":100}")
            .fingerprint());
  }

  @Test
  void washColorIsKeptForImageBackgroundsOnly() {
    EpisodeCoverStyle progressive = EpisodeCoverStyle.parse(
        "{\"mode\":\"PAD\",\"fill\":\"PROGRESSIVE\",\"tintColor\":\"#FF8800\"}");
    EpisodeCoverStyle invalid = EpisodeCoverStyle.parse(
        "{\"mode\":\"PAD\",\"fill\":\"BLUR\",\"tintColor\":\"orange\"}");
    EpisodeCoverStyle colorFill = EpisodeCoverStyle.parse(
        "{\"mode\":\"PAD\",\"fill\":\"COLOR\",\"tintColor\":\"#ff8800\"}");

    assertEquals("#ff8800", progressive.tintColor());
    assertNull(invalid.tintColor());
    assertNull(colorFill.tintColor());
  }

  @Test
  void feedCoverModeDoesNotRequireRendering() {
    EpisodeCoverStyle style = EpisodeCoverStyle.parse("{\"mode\":\"FEED_COVER\",\"blur\":40}");

    assertEquals(EpisodeCoverMode.FEED_COVER, style.mode());
    assertNull(style.blur());
    assertFalse(style.requiresRendering());
  }

  @Test
  void normalizeForStorageStoresCompactJsonAndClearsOriginal() {
    assertNull(EpisodeCoverStyle.normalizeForStorage(null));
    assertNull(EpisodeCoverStyle.normalizeForStorage("{\"mode\":\"ORIGINAL\"}"));
    assertEquals("{\"mode\":\"CROP\",\"position\":75}",
        EpisodeCoverStyle.normalizeForStorage("{\"mode\":\"CROP\",\"position\":75,\"extra\":1}"));
  }

  @Test
  void normalizeForStorageRejectsMalformedJson() {
    assertThrows(BusinessException.class, () -> EpisodeCoverStyle.normalizeForStorage("{oops"));
  }

  @Test
  void fingerprintChangesWithSettingsButNotWithIgnoredFields() {
    EpisodeCoverStyle crop30 = EpisodeCoverStyle.parse("{\"mode\":\"CROP\",\"position\":30}");
    EpisodeCoverStyle crop30WithNoise = EpisodeCoverStyle.parse(
        "{\"mode\":\"CROP\",\"position\":30,\"blur\":99}");
    EpisodeCoverStyle crop70 = EpisodeCoverStyle.parse("{\"mode\":\"CROP\",\"position\":70}");

    assertEquals(10, crop30.fingerprint().length());
    assertEquals(crop30.fingerprint(), crop30WithNoise.fingerprint());
    assertNotEquals(crop30.fingerprint(), crop70.fingerprint());
  }
}
