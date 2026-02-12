package top.asimov.pigeon.helper;

import top.asimov.pigeon.model.enums.YoutubeApiCallContext;

public final class YoutubeQuotaContextHolder {

  private static final ThreadLocal<YoutubeApiCallContext> CONTEXT = new ThreadLocal<>();

  private YoutubeQuotaContextHolder() {
  }

  public static void set(YoutubeApiCallContext context) {
    CONTEXT.set(context);
  }

  public static YoutubeApiCallContext get() {
    YoutubeApiCallContext context = CONTEXT.get();
    return context == null ? YoutubeApiCallContext.MANUAL : context;
  }

  public static void clear() {
    CONTEXT.remove();
  }
}
