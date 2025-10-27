package top.asimov.pigeon.model.enums;

public enum EpisodeStatus {
  PENDING, // 初始状态，等待下载
  DOWNLOADING, // 正在下载
  COMPLETED, // 下载完成
  FAILED // 下载失败
}
