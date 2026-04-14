package top.asimov.pigeon.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import top.asimov.pigeon.model.enums.DownloadType;

class MediaIntegrityValidatorTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldAcceptAudioFileWhenFfprobeReportsAudioStream() throws Exception {
    Path mediaFile = Files.writeString(tempDir.resolve("episode.m4a"), "not-empty",
        StandardCharsets.UTF_8);
    writeExecutable(tempDir.resolve("ffprobe"), """
        #!/bin/sh
        printf '%s' '{"format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"123.45"},"streams":[{"codec_type":"audio"}]}'
        """);

    MediaIntegrityValidator validator = new MediaIntegrityValidator(new ObjectMapper());
    ReflectionTestUtils.setField(validator, "ffmpegLocation", tempDir.toString());

    MediaIntegrityValidator.ValidationResult result =
        validator.validate(mediaFile, DownloadType.AUDIO);

    assertTrue(result.valid());
  }

  @Test
  void shouldRejectVideoFileWhenFfprobeReportsNoVideoStream() throws Exception {
    Path mediaFile = Files.writeString(tempDir.resolve("episode.mp4"), "not-empty",
        StandardCharsets.UTF_8);
    writeExecutable(tempDir.resolve("ffprobe"), """
        #!/bin/sh
        printf '%s' '{"format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"123.45"},"streams":[{"codec_type":"audio"}]}'
        """);

    MediaIntegrityValidator validator = new MediaIntegrityValidator(new ObjectMapper());
    ReflectionTestUtils.setField(validator, "ffmpegLocation", tempDir.resolve("ffmpeg").toString());

    MediaIntegrityValidator.ValidationResult result =
        validator.validate(mediaFile, DownloadType.VIDEO);

    assertFalse(result.valid());
    assertTrue(result.message().contains("no video stream"));
  }

  @Test
  void shouldRejectFileWhenFfprobeFails() throws Exception {
    Path mediaFile = Files.writeString(tempDir.resolve("episode.mp4"), "not-empty",
        StandardCharsets.UTF_8);
    writeExecutable(tempDir.resolve("ffprobe"), """
        #!/bin/sh
        echo 'moov atom not found' >&2
        exit 1
        """);

    MediaIntegrityValidator validator = new MediaIntegrityValidator(new ObjectMapper());
    ReflectionTestUtils.setField(validator, "ffmpegLocation", tempDir.toString());

    MediaIntegrityValidator.ValidationResult result =
        validator.validate(mediaFile, DownloadType.VIDEO);

    assertFalse(result.valid());
    assertTrue(result.message().contains("ffprobe exited with code 1"));
  }

  private void writeExecutable(Path path, String content) throws IOException {
    Files.writeString(path, content, StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(path, Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE));
  }
}
