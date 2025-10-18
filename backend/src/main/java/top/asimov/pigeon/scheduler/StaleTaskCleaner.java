package top.asimov.pigeon.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.constant.EpisodeStatus;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.Episode;

import java.util.List;

@Log4j2
@Component
public class StaleTaskCleaner implements ApplicationRunner {

    private final EpisodeMapper episodeMapper;

    public StaleTaskCleaner(EpisodeMapper episodeMapper) {
        this.episodeMapper = episodeMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking for stale 'DOWNLOADING' tasks at startup...");
        QueryWrapper<Episode> query = new QueryWrapper<>();
        query.eq("download_status", EpisodeStatus.DOWNLOADING.name());

        List<Episode> staleEpisodes = episodeMapper.selectList(query);

        if (staleEpisodes.isEmpty()) {
            log.info("No stale tasks found. System is clean.");
            return;
        }

        log.warn("Found {} stale 'DOWNLOADING' tasks. Resetting them to 'PENDING'.", staleEpisodes.size());
        for (Episode episode : staleEpisodes) {
            log.debug("Resetting episode: id={}, title='{}'", episode.getId(), episode.getTitle());
            episode.setDownloadStatus(EpisodeStatus.PENDING.name());
            episodeMapper.updateById(episode);
        }
        log.info("Finished cleaning up {} stale tasks.", staleEpisodes.size());
    }
}
