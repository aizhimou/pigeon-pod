package top.asimov.pigeon.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import top.asimov.pigeon.model.dto.EpisodeCoverStyle;

class EpisodeCoverRendererTest {

  private static final Color LEFT = new Color(220, 40, 40);
  private static final Color MIDDLE = new Color(40, 200, 40);
  private static final Color RIGHT = new Color(40, 40, 220);

  /** 320x180 thumbnail split into red / green / blue vertical thirds. */
  private static BufferedImage landscapeThumbnail() {
    BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(LEFT);
    graphics.fillRect(0, 0, 107, 180);
    graphics.setColor(MIDDLE);
    graphics.fillRect(107, 0, 106, 180);
    graphics.setColor(RIGHT);
    graphics.fillRect(213, 0, 107, 180);
    graphics.dispose();
    return image;
  }

  private static EpisodeCoverStyle style(String json) {
    return EpisodeCoverStyle.parse(json);
  }

  private static void assertColorClose(Color expected, int actualRgb) {
    Color actual = new Color(actualRgb);
    int distance = Math.abs(expected.getRed() - actual.getRed())
        + Math.abs(expected.getGreen() - actual.getGreen())
        + Math.abs(expected.getBlue() - actual.getBlue());
    assertTrue(distance < 30, "expected ~" + expected + " but was " + actual);
  }

  @Test
  void cropProducesSquareAtChosenPosition() {
    BufferedImage left = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"CROP\",\"position\":0}"), 0);
    BufferedImage right = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"CROP\",\"position\":100}"), 0);

    assertEquals(180, left.getWidth());
    assertEquals(180, left.getHeight());
    assertColorClose(LEFT, left.getRGB(5, 90));
    assertColorClose(RIGHT, right.getRGB(right.getWidth() - 5, 90));
  }

  @Test
  void padWithColorKeepsWholeImageCentered() {
    BufferedImage result = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"PAD\",\"fill\":\"COLOR\",\"color\":\"#ffffff\",\"fade\":0}"), 0);

    assertEquals(320, result.getWidth());
    assertEquals(320, result.getHeight());
    assertColorClose(Color.WHITE, result.getRGB(160, 10));
    assertColorClose(Color.WHITE, result.getRGB(160, 310));
    // image occupies rows 70..249, untouched edge to edge
    assertColorClose(LEFT, result.getRGB(2, 160));
    assertColorClose(MIDDLE, result.getRGB(160, 72));
    assertColorClose(RIGHT, result.getRGB(317, 160));
  }

  @Test
  void softEdgeBlendsImageIntoBackground() {
    BufferedImage hard = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"PAD\",\"fill\":\"COLOR\",\"color\":\"#ffffff\",\"fade\":0}"), 0);
    BufferedImage soft = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"PAD\",\"fill\":\"COLOR\",\"color\":\"#ffffff\",\"fade\":100}"), 0);

    // first image row: fully opaque without fade, mostly background with fade
    assertColorClose(MIDDLE, hard.getRGB(160, 70));
    Color softEdge = new Color(soft.getRGB(160, 70));
    assertTrue(softEdge.getRed() > 200 && softEdge.getBlue() > 200,
        "edge row should be close to the white background but was " + softEdge);
    // centre of the image is unaffected
    assertColorClose(MIDDLE, soft.getRGB(160, 160));
  }

  @Test
  void blurFillCoversPaddingWithImageColors() {
    BufferedImage result = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"PAD\",\"fill\":\"BLUR\",\"blur\":60,\"fade\":0,\"tint\":0}"), 0);

    assertEquals(320, result.getWidth());
    // the zoomed background is centred on the green third, so the padding is greenish
    Color padding = new Color(result.getRGB(160, 20));
    assertTrue(padding.getGreen() > padding.getRed() && padding.getGreen() > padding.getBlue(),
        "padding should take the image's colors but was " + padding);
  }

  @Test
  void progressiveBlurContinuesTheImageAndKeepsItsMiddleSharp() {
    BufferedImage result = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"PAD\",\"fill\":\"PROGRESSIVE\",\"tint\":0,\"position\":0}"), 0);

    assertEquals(320, result.getWidth());
    assertEquals(320, result.getHeight());
    // image sits at the top and stays sharp away from its bottom edge
    assertColorClose(LEFT, result.getRGB(20, 60));
    assertColorClose(MIDDLE, result.getRGB(160, 60));
    // the band below continues each third's color
    Color belowLeft = new Color(result.getRGB(10, 300));
    Color belowRight = new Color(result.getRGB(310, 300));
    assertTrue(belowLeft.getRed() > belowLeft.getGreen() && belowLeft.getRed() > belowLeft.getBlue(),
        "band under the red third should stay red but was " + belowLeft);
    assertTrue(belowRight.getBlue() > belowRight.getRed()
            && belowRight.getBlue() > belowRight.getGreen(),
        "band under the blue third should stay blue but was " + belowRight);
  }

  @Test
  void progressiveBlurSoftensTheImageNearItsEdge() {
    BufferedImage result = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"PAD\",\"fill\":\"PROGRESSIVE\",\"tint\":0,\"fade\":100,"
            + "\"position\":0}"), 0);

    // just inside the bottom edge, the hard red/green boundary at x=107 is blurred
    Color atBoundary = new Color(result.getRGB(107, 178));
    assertTrue(atBoundary.getRed() > 70 && atBoundary.getGreen() > 70,
        "colors should mix across the boundary near the edge but was " + atBoundary);
    assertColorClose(LEFT, result.getRGB(100, 20));
  }

  @Test
  void colorWashDarkensTheFarEndOfTheBand() {
    BufferedImage plain = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"PAD\",\"fill\":\"PROGRESSIVE\",\"tint\":0,\"position\":0}"), 0);
    BufferedImage washed = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"PAD\",\"fill\":\"PROGRESSIVE\",\"tint\":100,\"position\":0}"), 0);

    assertTrue(brightness(washed.getRGB(160, 315)) < brightness(plain.getRGB(160, 315)) - 20,
        "the wash should deepen the bottom of the band");
    assertColorClose(MIDDLE, washed.getRGB(160, 40));
  }

  @Test
  void progressiveRenderingIsDeterministic() {
    EpisodeCoverStyle progressive = style("{\"mode\":\"PAD\",\"fill\":\"PROGRESSIVE\"}");
    BufferedImage first = EpisodeCoverRenderer.render(landscapeThumbnail(), progressive, 0);
    BufferedImage second = EpisodeCoverRenderer.render(landscapeThumbnail(), progressive, 0);

    for (int y = 0; y < first.getHeight(); y += 7) {
      for (int x = 0; x < first.getWidth(); x += 7) {
        assertEquals(first.getRGB(x, y), second.getRGB(x, y));
      }
    }
  }

  @Test
  void imagePositionMovesTheImageInsideTheSquare() {
    BufferedImage atTop = EpisodeCoverRenderer.render(landscapeThumbnail(), style(
        "{\"mode\":\"PAD\",\"fill\":\"COLOR\",\"color\":\"#ffffff\",\"fade\":0,\"position\":0}"), 0);
    BufferedImage atBottom = EpisodeCoverRenderer.render(landscapeThumbnail(), style(
        "{\"mode\":\"PAD\",\"fill\":\"COLOR\",\"color\":\"#ffffff\",\"fade\":0,\"position\":100}"),
        0);

    assertColorClose(MIDDLE, atTop.getRGB(160, 2));
    assertColorClose(Color.WHITE, atTop.getRGB(160, 317));
    assertColorClose(Color.WHITE, atBottom.getRGB(160, 2));
    assertColorClose(MIDDLE, atBottom.getRGB(160, 317));
  }

  @Test
  void zoomedCropKeepsTheChosenCorner() {
    BufferedImage bands = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = bands.createGraphics();
    graphics.setColor(LEFT);
    graphics.fillRect(0, 0, 320, 90);
    graphics.setColor(RIGHT);
    graphics.fillRect(0, 90, 320, 90);
    graphics.dispose();

    BufferedImage top = EpisodeCoverRenderer.render(bands,
        style("{\"mode\":\"CROP\",\"zoom\":200,\"position\":0,\"pan\":0}"), 0);
    BufferedImage bottom = EpisodeCoverRenderer.render(bands,
        style("{\"mode\":\"CROP\",\"zoom\":200,\"position\":0,\"pan\":100}"), 0);

    assertEquals(180, top.getWidth());
    assertEquals(180, top.getHeight());
    assertColorClose(LEFT, top.getRGB(90, 90));
    assertColorClose(RIGHT, bottom.getRGB(90, 90));
  }

  @Test
  void zoomedPadMakesTheImageTallerAndTrimsItsSides() {
    BufferedImage result = EpisodeCoverRenderer.render(landscapeThumbnail(), style(
        "{\"mode\":\"PAD\",\"fill\":\"COLOR\",\"color\":\"#ffffff\",\"fade\":0,"
            + "\"zoom\":150,\"pan\":0}"), 0);

    assertEquals(320, result.getWidth());
    // 180px tall image zoomed to 270px leaves 25px bands
    assertColorClose(Color.WHITE, result.getRGB(160, 10));
    assertColorClose(LEFT, result.getRGB(5, 40));
    // the window starts at the left, so the right third is trimmed away
    assertColorClose(MIDDLE, result.getRGB(315, 160));
  }

  @Test
  void padZoomStopsWhenTheImageFillsTheSquare() {
    BufferedImage result = EpisodeCoverRenderer.render(landscapeThumbnail(), style(
        "{\"mode\":\"PAD\",\"fill\":\"COLOR\",\"color\":\"#ffffff\",\"fade\":0,"
            + "\"zoom\":300}"), 0);

    assertEquals(320, result.getWidth());
    assertEquals(320, result.getHeight());
    assertColorClose(MIDDLE, result.getRGB(160, 2));
    assertColorClose(MIDDLE, result.getRGB(160, 317));
  }

  @Test
  void customWashColorTintsTheBand() {
    BufferedImage result = EpisodeCoverRenderer.render(landscapeThumbnail(), style(
        "{\"mode\":\"PAD\",\"fill\":\"PROGRESSIVE\",\"tint\":100,"
            + "\"tintColor\":\"#ff0000\",\"position\":0}"), 0);

    Color bottom = new Color(result.getRGB(160, 318));
    assertTrue(bottom.getRed() > 200 && bottom.getGreen() < 80 && bottom.getBlue() < 80,
        "the far end of the band should be close to the wash color but was " + bottom);
    assertColorClose(MIDDLE, result.getRGB(160, 40));
  }

  private static int brightness(int rgb) {
    Color color = new Color(rgb);
    return (color.getRed() + color.getGreen() + color.getBlue()) / 3;
  }

  @Test
  void portraitImagesArePaddedSideways() {
    BufferedImage portrait = new BufferedImage(90, 160, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = portrait.createGraphics();
    graphics.setColor(MIDDLE);
    graphics.fillRect(0, 0, 90, 160);
    graphics.dispose();

    BufferedImage result = EpisodeCoverRenderer.render(portrait,
        style("{\"mode\":\"PAD\",\"fill\":\"COLOR\",\"color\":\"#000000\",\"fade\":0}"), 0);

    assertEquals(160, result.getWidth());
    assertEquals(160, result.getHeight());
    assertColorClose(Color.BLACK, result.getRGB(5, 80));
    assertColorClose(MIDDLE, result.getRGB(80, 80));

    BufferedImage progressive = EpisodeCoverRenderer.render(portrait,
        style("{\"mode\":\"PAD\",\"fill\":\"PROGRESSIVE\",\"tint\":0}"), 0);
    assertEquals(160, progressive.getWidth());
    assertEquals(160, progressive.getHeight());
    assertColorClose(MIDDLE, progressive.getRGB(5, 80));
  }

  @Test
  void maxSideLimitsOutputSize() {
    BufferedImage result = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"PAD\"}"), 100);

    assertEquals(100, result.getWidth());
    assertEquals(100, result.getHeight());
  }

  @Test
  void originalModeReturnsSourceUnchanged() {
    BufferedImage source = landscapeThumbnail();

    assertSame(source, EpisodeCoverRenderer.render(source, EpisodeCoverStyle.ORIGINAL, 0));
  }

  @Test
  void writesReadableJpeg() throws Exception {
    BufferedImage result = EpisodeCoverRenderer.render(landscapeThumbnail(),
        style("{\"mode\":\"PAD\",\"fade\":50}"), 0);
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    EpisodeCoverRenderer.writeJpeg(result, output);

    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(output.toByteArray()));
    assertNotNull(decoded);
    assertEquals(320, decoded.getWidth());
    assertEquals(320, decoded.getHeight());
  }
}
