package top.asimov.pigeon.model.dto;

import java.util.List;
import top.asimov.pigeon.model.enums.DownloadType;

public record FeedContext(String title, DownloadType downloadType, Integer audioQuality,
                          String videoQuality, String videoEncoding, String subtitleLanguages,
                          String subtitleFormat, List<String> ytDlpArgs) {

}
