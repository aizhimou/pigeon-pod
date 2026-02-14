package top.asimov.pigeon.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.asimov.pigeon.model.enums.StorageType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig {

  public static final int SINGLETON_ID = 0;
  public static final String DEFAULT_TEMP_DIR = "/tmp/pigeon-pod";
  public static final String DEFAULT_LOCAL_AUDIO_PATH = "/data/audio/";
  public static final String DEFAULT_LOCAL_VIDEO_PATH = "/data/video/";
  public static final String DEFAULT_LOCAL_COVER_PATH = "/data/cover/";
  public static final String DEFAULT_S3_REGION = "us-east-1";
  public static final int DEFAULT_S3_CONNECT_TIMEOUT_SECONDS = 30;
  public static final int DEFAULT_S3_SOCKET_TIMEOUT_SECONDS = 1800;
  public static final int DEFAULT_S3_READ_TIMEOUT_SECONDS = 1800;
  public static final int DEFAULT_S3_PRESIGN_EXPIRE_HOURS = 72;

  @TableId
  private Integer id;

  private String baseUrl;

  private String youtubeApiKey;
  private String cookiesContent;
  private String ytDlpArgs;
  private Boolean loginCaptchaEnabled;
  private Integer youtubeDailyLimitUnits;

  private StorageType storageType;
  private String storageTempDir;

  private String localAudioPath;
  private String localVideoPath;
  private String localCoverPath;

  private String s3Endpoint;
  private String s3Region;
  private String s3Bucket;
  private String s3AccessKey;
  private String s3SecretKey;
  private Boolean s3PathStyleAccess;
  private Integer s3ConnectTimeoutSeconds;
  private Integer s3SocketTimeoutSeconds;
  private Integer s3ReadTimeoutSeconds;
  private Integer s3PresignExpireHours;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @TableField(exist = false)
  private transient Boolean hasS3SecretKey;

  @TableField(exist = false)
  private transient Boolean hasCookie;
}
