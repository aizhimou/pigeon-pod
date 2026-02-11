package top.asimov.pigeon.model.dto;

import java.time.LocalDateTime;

public record PlaylistSnapshotEntry(
    String videoId,
    Long position,
    String title,
    LocalDateTime approximatePublishedAt
) {

}
