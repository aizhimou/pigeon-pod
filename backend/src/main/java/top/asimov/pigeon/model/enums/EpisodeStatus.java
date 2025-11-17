package top.asimov.pigeon.model.enums;

public enum EpisodeStatus {
  READY, // 仅保存节目信息，尚未排队下载
  PENDING, // 已排队，等待下载
  DOWNLOADING, // 正在下载
  COMPLETED, // 下载完成
  FAILED // 下载失败
}
