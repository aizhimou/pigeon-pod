package top.asimov.pigeon.util;

public final class YtDlpOutputTemplateUtil {

  private YtDlpOutputTemplateUtil() {
  }

  public static String buildMediaOutputTemplate(String outputDirPath, String outputBaseName) {
    return escapeLiteral(outputDirPath) + escapeLiteral(outputBaseName) + ".%(ext)s";
  }

  public static String escapeLiteral(String literal) {
    if (literal == null || literal.isEmpty()) {
      return "";
    }
    return literal.replace("%", "%%");
  }
}
