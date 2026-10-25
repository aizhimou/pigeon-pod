package top.asimov.pigeon.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import top.asimov.pigeon.model.dto.EpisodeCoverStyle;

/**
 * Turns a (usually 16:9) episode thumbnail into square artwork according to an
 * {@link EpisodeCoverStyle}. Pure Java2D so it works the same in tests, locally and in the
 * container.
 *
 * <p>Padding is always computed on a landscape image with empty bands above and/or below it;
 * portrait images are transposed first and back afterwards.</p>
 */
public final class EpisodeCoverRenderer {

  /** At 100% the alpha fade covers this fraction of the image height on each padded edge. */
  private static final double MAX_FADE_FRACTION = 0.25;
  /** At 100% the blurred-image background uses a Gaussian of this fraction of the side. */
  private static final double MAX_BACKGROUND_SIGMA_FRACTION = 1.0 / 6;
  /** At 100% the far end of a progressive blur uses a Gaussian of this fraction of the side. */
  private static final double MAX_PROGRESSIVE_SIGMA_FRACTION = 0.22;
  /** Blur levels between sharp and fully blurred; each one doubles the previous radius. */
  private static final int PROGRESSIVE_LEVELS = 5;
  /** At 100% the progressive blur starts this fraction of the image height inside the image. */
  private static final double MAX_PROGRESSIVE_REACH_FRACTION = 0.45;
  /** Blur amount (0-1, across the levels) reached exactly at the image edge. */
  private static final double EDGE_BLUR_AMOUNT = 0.6;
  /** Past the edge, full blur is reached after this fraction of the band (at 50%+ soft edge). */
  private static final double FULL_BLUR_BAND_FRACTION = 0.6;
  /** Strongest color wash, at 100% tint, at the far end of a band. */
  private static final double MAX_WASH_ALPHA = 0.9;
  /** Fraction of the image height sampled at each edge for the wash color. */
  private static final double WASH_SAMPLE_FRACTION = 0.3;
  /** Blurs shrink the image by up to this factor first, which keeps large radii cheap. */
  private static final int MAX_BLUR_WORKING_SCALE = 8;
  private static final float JPEG_QUALITY = 0.9f;

  private EpisodeCoverRenderer() {
  }

  /**
   * Renders square artwork no larger than {@code maxSide} pixels. Modes that don't re-draw the
   * image return the (possibly downscaled) source unchanged.
   */
  public static BufferedImage render(BufferedImage source, EpisodeCoverStyle style, int maxSide) {
    BufferedImage image = downscale(source, maxSide);
    EpisodeCoverStyle normalized = style.normalized();
    return switch (normalized.mode()) {
      case CROP -> crop(image, normalized.position(), normalized.zoomPercent(),
          normalized.panPercent());
      case PAD -> pad(image, normalized);
      case ORIGINAL, FEED_COVER -> image;
    };
  }

  public static void writeJpeg(BufferedImage image, OutputStream output) throws IOException {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      throw new IOException("No JPEG writer available");
    }
    ImageWriter writer = writers.next();
    ImageWriteParam param = writer.getDefaultWriteParam();
    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    param.setCompressionQuality(JPEG_QUALITY);
    try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
      writer.setOutput(imageOutput);
      writer.write(null, new IIOImage(toRgb(image), null, null), param);
    } finally {
      writer.dispose();
    }
  }

  /** Returns the image as RGB, scaled down so its longest side is at most {@code maxSide}. */
  public static BufferedImage downscale(BufferedImage source, int maxSide) {
    BufferedImage image = toRgb(source);
    int longest = Math.max(image.getWidth(), image.getHeight());
    if (maxSide <= 0 || longest <= maxSide) {
      return image;
    }
    double ratio = (double) maxSide / longest;
    return scale(image,
        Math.max(1, (int) Math.round(image.getWidth() * ratio)),
        Math.max(1, (int) Math.round(image.getHeight() * ratio)));
  }

  /**
   * Square crop. Unzoomed, {@code positionPercent} slides the square along the long side. Zoomed
   * in, the square is smaller and {@code positionPercent}/{@code panPercent} place it horizontally
   * and vertically; it is then scaled back up to the unzoomed size.
   */
  static BufferedImage crop(BufferedImage image, int positionPercent, int zoomPercent,
      int panPercent) {
    int width = image.getWidth();
    int height = image.getHeight();
    int fullSide = Math.min(width, height);
    if (zoomPercent <= 100) {
      int offset = (int) Math.round((Math.max(width, height) - fullSide) * positionPercent / 100.0);
      int x = width > height ? offset : 0;
      int y = height > width ? offset : 0;
      return copy(image.getSubimage(x, y, fullSide, fullSide));
    }
    int side = Math.max(1, (int) Math.round(fullSide * 100.0 / zoomPercent));
    int x = (int) Math.round((width - side) * positionPercent / 100.0);
    int y = (int) Math.round((height - side) * panPercent / 100.0);
    return scale(image.getSubimage(x, y, side, side), fullSide, fullSide);
  }

  static BufferedImage pad(BufferedImage image, EpisodeCoverStyle style) {
    if (image.getWidth() == image.getHeight()) {
      return image;
    }
    if (image.getHeight() > image.getWidth()) {
      return transpose(pad(transpose(image), style));
    }
    BufferedImage zoomed = zoomForPad(image, style.zoomPercent(), style.panPercent());
    if (zoomed.getWidth() == zoomed.getHeight()) {
      return zoomed;
    }
    return padLandscape(zoomed, style);
  }

  private static BufferedImage padLandscape(BufferedImage image, EpisodeCoverStyle style) {
    int side = image.getWidth();
    int top = (int) Math.round((side - image.getHeight()) * style.position() / 100.0);
    Layout layout = new Layout(side, top, top + image.getHeight());
    return switch (style.fill()) {
      case PROGRESSIVE -> progressive(image, layout, style);
      case BLUR -> layered(image, layout, style.fade(),
          wash(blur(coverSquare(image, side), style.blur()), image, layout, style.tint(),
              style.tintColor(), 0));
      case COLOR -> layered(image, layout, style.fade(), solid(side, Color.decode(style.color())));
    };
  }

  /** Where the image sits in the square: rows {@code [top, bottom)} hold the image. */
  private record Layout(int side, int top, int bottom) {

    boolean hasTopBand() {
      return top > 0;
    }

    boolean hasBottomBand() {
      return bottom < side;
    }

    int imageHeight() {
      return bottom - top;
    }
  }

  /**
   * iOS-style extension: the image continues into the empty bands as a mirrored copy that gets
   * progressively blurrier, starting inside the image, and is washed with a deepened color from
   * the image's edge so the band reads as one smooth field of color.
   */
  private static BufferedImage progressive(BufferedImage image, Layout layout,
      EpisodeCoverStyle style) {
    int side = layout.side();
    BufferedImage extended = mirrorExtend(image, layout);
    double maxSigma = side * MAX_PROGRESSIVE_SIGMA_FRACTION * style.blur() / 100.0;
    int[][] levels = new int[PROGRESSIVE_LEVELS + 1][];
    levels[0] = pixels(extended);
    for (int level = 1; level <= PROGRESSIVE_LEVELS; level++) {
      double sigma = maxSigma / Math.pow(2, PROGRESSIVE_LEVELS - level);
      levels[level] = pixels(gaussian(extended, sigma));
    }

    double insideReach = layout.imageHeight() * MAX_PROGRESSIVE_REACH_FRACTION
        * style.fade() / 100.0;
    double softness = Math.min(1, style.fade() / 50.0);
    BufferedImage result = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
    int[] output = pixels(result);
    for (int y = 0; y < side; y++) {
      double amount = Math.max(
          blurAmount(layout.top() - y - 0.5, layout.top(), insideReach, softness, side),
          blurAmount(y + 0.5 - layout.bottom(), side - layout.bottom(), insideReach, softness,
              side));
      double position = amount * PROGRESSIVE_LEVELS;
      int lower = Math.min((int) position, PROGRESSIVE_LEVELS - 1);
      double fraction = position - lower;
      int row = y * side;
      System.arraycopy(levels[0], row, output, row, side);
      if (amount <= 0) {
        continue;
      }
      for (int x = 0; x < side; x++) {
        output[row + x] = mix(levels[lower][row + x], levels[lower + 1][row + x], fraction);
      }
    }
    return wash(result, image, layout, style.tint(), style.tintColor(), insideReach);
  }

  /**
   * How blurred a row is (0 sharp, 1 fully blurred) relative to one band. {@code distance} is
   * measured outward from the image edge; inside the image it is negative. The blur builds up
   * inside the image so it is already strong at the edge, where the mirrored copy begins and
   * would otherwise show as a reflection line.
   */
  private static double blurAmount(double distance, int band, double insideReach, double softness,
      int side) {
    if (band <= 0) {
      return 0;
    }
    if (distance < 0) {
      return insideReach <= 0 ? 0 : EDGE_BLUR_AMOUNT * smoothstep(1 + distance / insideReach);
    }
    double outsideReach = Math.max(side * 0.01, band * FULL_BLUR_BAND_FRACTION * softness);
    double t = Math.min(1, distance / outsideReach);
    return EDGE_BLUR_AMOUNT + (1 - EDGE_BLUR_AMOUNT) * (1 - (1 - t) * (1 - t));
  }

  /** Fills the empty bands by mirroring the image at its edges (repeating if a band is taller). */
  private static BufferedImage mirrorExtend(BufferedImage image, Layout layout) {
    int side = layout.side();
    int height = layout.imageHeight();
    int[] source = pixels(image);
    BufferedImage extended = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
    int[] target = pixels(extended);
    for (int y = 0; y < side; y++) {
      int sourceRow = mirrorIndex(y - layout.top(), height);
      System.arraycopy(source, sourceRow * side, target, y * side, side);
    }
    return extended;
  }

  private static int mirrorIndex(int index, int length) {
    int period = length * 2;
    int wrapped = Math.floorMod(index, period);
    return wrapped < length ? wrapped : period - 1 - wrapped;
  }

  /**
   * Washes the bands with {@code tintColor}, or when it is {@code null} a deepened version of the
   * color found near each image edge, stronger toward the outer edge of the square, and dithers
   * them so dark gradients don't band. {@code insideReach} lets the wash begin slightly inside the
   * image.
   */
  private static BufferedImage wash(BufferedImage canvas, BufferedImage image, Layout layout,
      int tintPercent, String tintColor, double insideReach) {
    if (tintPercent <= 0) {
      return canvas;
    }
    int side = layout.side();
    int height = layout.imageHeight();
    int sample = Math.max(1, (int) Math.round(height * WASH_SAMPLE_FRACTION));
    Color topColor = averageColor(image, 0, sample);
    Color bottomColor = averageColor(image, height - sample, height);
    double maxAlpha = MAX_WASH_ALPHA * tintPercent / 100.0;
    Integer customColor = tintColor == null ? null : Color.decode(tintColor).getRGB() & 0xFFFFFF;
    double lead = insideReach / 2;
    int[] pixels = pixels(canvas);
    Random noise = new Random(side);
    for (int y = 0; y < side; y++) {
      double topT = layout.hasTopBand()
          ? (layout.top() - y - 0.5 + lead) / (layout.top() + lead) : -1;
      double bottomT = layout.hasBottomBand()
          ? (y + 0.5 - layout.bottom() + lead) / (side - layout.bottom() + lead) : -1;
      double t = Math.max(topT, bottomT);
      if (t <= 0) {
        continue;
      }
      t = Math.min(1, t);
      Color base = topT >= bottomT ? topColor : bottomColor;
      int washColor = customColor != null ? customColor : deepen(base, t);
      double alpha = maxAlpha * Math.pow(t, 0.9);
      int row = y * side;
      for (int x = 0; x < side; x++) {
        pixels[row + x] = dither(mix(pixels[row + x], washColor, alpha), noise);
      }
    }
    return canvas;
  }

  /** Darker and a little richer the further it is from the image (t = 0 at the edge, 1 far). */
  private static int deepen(Color color, double t) {
    float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    float saturation = (float) Math.min(1, hsb[1] * (1.1 + 0.15 * t));
    float brightness = (float) (hsb[2] * (0.75 - 0.3 * t));
    return Color.HSBtoRGB(hsb[0], saturation, brightness) & 0xFFFFFF;
  }

  /**
   * Characteristic color of a horizontal band: an average weighted toward saturated pixels, so a
   * colorful band gives a rich tint instead of a gray mix.
   */
  private static Color averageColor(BufferedImage image, int fromRow, int toRow) {
    BufferedImage band = scale(image.getSubimage(0, fromRow, image.getWidth(), toRow - fromRow),
        32, 8);
    double red = 0;
    double green = 0;
    double blue = 0;
    double totalWeight = 0;
    float[] hsb = new float[3];
    for (int pixel : pixels(band)) {
      int r = (pixel >> 16) & 0xFF;
      int g = (pixel >> 8) & 0xFF;
      int b = pixel & 0xFF;
      Color.RGBtoHSB(r, g, b, hsb);
      double weight = 0.15 + hsb[1] * hsb[2];
      red += r * weight;
      green += g * weight;
      blue += b * weight;
      totalWeight += weight;
    }
    return new Color((int) Math.round(red / totalWeight), (int) Math.round(green / totalWeight),
        (int) Math.round(blue / totalWeight));
  }

  /** Draws the image over a background, fading its padded edges to transparent. */
  private static BufferedImage layered(BufferedImage image, Layout layout, int fadePercent,
      BufferedImage background) {
    int fadePixels = (int) Math.round(
        layout.imageHeight() * MAX_FADE_FRACTION * fadePercent / 100.0);
    BufferedImage foreground = fadePixels > 0
        ? fadeEdges(image, layout.hasTopBand(), layout.hasBottomBand(), fadePixels)
        : image;
    Graphics2D graphics = background.createGraphics();
    graphics.drawImage(foreground, 0, layout.top(), null);
    graphics.dispose();
    return background;
  }

  /**
   * Enlarges a landscape image for padding while keeping its width: the sides are trimmed around
   * {@code panPercent}, and the image gets taller, leaving smaller bands. Zoom stops once the image
   * fills the square.
   */
  static BufferedImage zoomForPad(BufferedImage image, int zoomPercent, int panPercent) {
    int width = image.getWidth();
    int height = image.getHeight();
    double zoom = Math.min(zoomPercent / 100.0, (double) width / height);
    if (zoom <= 1.0) {
      return image;
    }
    int windowWidth = Math.max(1, (int) Math.round(width / zoom));
    int x = (int) Math.round((width - windowWidth) * panPercent / 100.0);
    int zoomedHeight = Math.min(width, (int) Math.round(height * zoom));
    return scale(image.getSubimage(x, 0, windowWidth, height), width, zoomedHeight);
  }

  /** Scales the image to cover a square, cutting the long side equally on both ends. */
  private static BufferedImage coverSquare(BufferedImage image, int side) {
    return scale(crop(image, 50, 100, 50), side, side);
  }

  /** Returns a copy whose top and/or bottom rows fade to transparent along a smoothstep curve. */
  private static BufferedImage fadeEdges(BufferedImage image, boolean fadeTop, boolean fadeBottom,
      int fadePixels) {
    int width = image.getWidth();
    int height = image.getHeight();
    BufferedImage faded = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = faded.createGraphics();
    graphics.drawImage(image, 0, 0, null);
    graphics.dispose();

    int[] pixels = pixels(faded);
    int fade = Math.min(fadePixels, height / 2);
    for (int i = 0; i < fade; i++) {
      int alphaBits = (int) Math.round(255 * smoothstep((i + 0.5) / fade)) << 24;
      if (fadeTop) {
        setRowAlpha(pixels, width, i, alphaBits);
      }
      if (fadeBottom) {
        setRowAlpha(pixels, width, height - 1 - i, alphaBits);
      }
    }
    return faded;
  }

  private static void setRowAlpha(int[] pixels, int width, int row, int alphaBits) {
    int start = row * width;
    for (int x = 0; x < width; x++) {
      pixels[start + x] = (pixels[start + x] & 0x00FFFFFF) | alphaBits;
    }
  }

  /** Blur for the blurred-image background; strength is relative to the image size. */
  static BufferedImage blur(BufferedImage image, int strengthPercent) {
    return gaussian(image, Math.min(image.getWidth(), image.getHeight())
        * MAX_BACKGROUND_SIGMA_FRACTION * strengthPercent / 100.0);
  }

  /**
   * Approximate Gaussian blur: shrink so the radius stays a few pixels wide, box-blur three times,
   * then enlarge in bilinear doubling steps so no blocks show.
   */
  static BufferedImage gaussian(BufferedImage image, double sigma) {
    if (sigma < 0.5) {
      return image;
    }
    int width = image.getWidth();
    int height = image.getHeight();
    int scale = (int) Math.max(1, Math.min(MAX_BLUR_WORKING_SCALE, Math.floor(sigma / 3)));
    BufferedImage small = scale == 1
        ? copy(image)
        : scale(image, Math.max(4, width / scale), Math.max(4, height / scale));
    int radius = Math.max(1, (int) Math.round(sigma / scale));
    for (int pass = 0; pass < 3; pass++) {
      boxBlur(small, radius);
    }
    return scale == 1 ? small : enlarge(small, width, height);
  }

  private static BufferedImage enlarge(BufferedImage image, int targetWidth, int targetHeight) {
    BufferedImage current = image;
    while (current.getWidth() * 2 < targetWidth && current.getHeight() * 2 < targetHeight) {
      current = drawScaled(current, current.getWidth() * 2, current.getHeight() * 2,
          RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
    return drawScaled(current, targetWidth, targetHeight,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
  }

  /** In-place separable box blur with clamped edges on a TYPE_INT_RGB image. */
  private static void boxBlur(BufferedImage image, int radius) {
    int width = image.getWidth();
    int height = image.getHeight();
    int[] pixels = pixels(image);
    int[] buffer = new int[pixels.length];
    blurLines(pixels, buffer, width, height, radius, true);
    blurLines(buffer, pixels, width, height, radius, false);
  }

  private static void blurLines(int[] source, int[] target, int width, int height, int radius,
      boolean isHorizontal) {
    int lines = isHorizontal ? height : width;
    int length = isHorizontal ? width : height;
    int window = radius * 2 + 1;
    for (int line = 0; line < lines; line++) {
      int red = 0;
      int green = 0;
      int blue = 0;
      for (int k = -radius; k <= radius; k++) {
        int pixel = source[index(line, clamp(k, length), width, isHorizontal)];
        red += (pixel >> 16) & 0xFF;
        green += (pixel >> 8) & 0xFF;
        blue += pixel & 0xFF;
      }
      for (int i = 0; i < length; i++) {
        target[index(line, i, width, isHorizontal)] =
            (red / window) << 16 | (green / window) << 8 | (blue / window);
        int outgoing = source[index(line, clamp(i - radius, length), width, isHorizontal)];
        int incoming = source[index(line, clamp(i + radius + 1, length), width, isHorizontal)];
        red += ((incoming >> 16) & 0xFF) - ((outgoing >> 16) & 0xFF);
        green += ((incoming >> 8) & 0xFF) - ((outgoing >> 8) & 0xFF);
        blue += (incoming & 0xFF) - (outgoing & 0xFF);
      }
    }
  }

  private static int index(int line, int position, int width, boolean isHorizontal) {
    return isHorizontal ? line * width + position : position * width + line;
  }

  private static int clamp(int value, int length) {
    return Math.max(0, Math.min(length - 1, value));
  }

  private static double smoothstep(double t) {
    double clamped = Math.max(0, Math.min(1, t));
    return clamped * clamped * (3 - 2 * clamped);
  }

  /** Linear mix of two RGB colors; {@code amount} 0 keeps {@code from}, 1 gives {@code to}. */
  private static int mix(int from, int to, double amount) {
    int red = (int) Math.round(((from >> 16) & 0xFF) * (1 - amount) + ((to >> 16) & 0xFF) * amount);
    int green = (int) Math.round(((from >> 8) & 0xFF) * (1 - amount) + ((to >> 8) & 0xFF) * amount);
    int blue = (int) Math.round((from & 0xFF) * (1 - amount) + (to & 0xFF) * amount);
    return red << 16 | green << 8 | blue;
  }

  /** Adds ±1 of noise per channel so smooth dark gradients don't show bands after JPEG. */
  private static int dither(int pixel, Random noise) {
    int offset = noise.nextInt(3) - 1;
    int red = clampChannel(((pixel >> 16) & 0xFF) + offset);
    int green = clampChannel(((pixel >> 8) & 0xFF) + offset);
    int blue = clampChannel((pixel & 0xFF) + offset);
    return red << 16 | green << 8 | blue;
  }

  private static int clampChannel(int value) {
    return Math.max(0, Math.min(255, value));
  }

  private static BufferedImage transpose(BufferedImage image) {
    BufferedImage source = toRgb(image);
    int width = source.getWidth();
    int height = source.getHeight();
    int[] input = pixels(source);
    BufferedImage transposed = new BufferedImage(height, width, BufferedImage.TYPE_INT_RGB);
    int[] output = pixels(transposed);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        output[x * height + y] = input[y * width + x];
      }
    }
    return transposed;
  }

  /** Scales with bicubic interpolation, halving first when shrinking a lot to avoid aliasing. */
  private static BufferedImage scale(BufferedImage image, int targetWidth, int targetHeight) {
    BufferedImage current = image;
    int width = image.getWidth();
    int height = image.getHeight();
    while (width / 2 >= targetWidth && height / 2 >= targetHeight) {
      width /= 2;
      height /= 2;
      current = drawScaled(current, width, height, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }
    return drawScaled(current, targetWidth, targetHeight,
        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
  }

  private static BufferedImage drawScaled(BufferedImage image, int width, int height,
      Object interpolation) {
    BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = scaled.createGraphics();
    applyQualityHints(graphics);
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
    graphics.drawImage(image, 0, 0, width, height, null);
    graphics.dispose();
    return scaled;
  }

  private static BufferedImage solid(int side, Color color) {
    BufferedImage canvas = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = canvas.createGraphics();
    graphics.setColor(color);
    graphics.fillRect(0, 0, side, side);
    graphics.dispose();
    return canvas;
  }

  private static BufferedImage copy(BufferedImage image) {
    BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(),
        BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = copy.createGraphics();
    graphics.drawImage(image, 0, 0, null);
    graphics.dispose();
    return copy;
  }

  private static BufferedImage toRgb(BufferedImage image) {
    if (image.getType() == BufferedImage.TYPE_INT_RGB) {
      return image;
    }
    return copy(image);
  }

  private static int[] pixels(BufferedImage image) {
    return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
  }

  private static void applyQualityHints(Graphics2D graphics) {
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
        RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
  }
}
