package top.asimov.pigeon.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.asimov.pigeon.service.EpisodeCoverService;
import top.asimov.pigeon.service.MediaService;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

  @Mock
  private MediaService mediaService;

  @Mock
  private EpisodeCoverService episodeCoverService;

  @InjectMocks
  private MediaController mediaController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(mediaController).build();
  }

  @Test
  void testGetEpisodeCoverWithoutExtension() throws Exception {
    when(mediaService.buildEpisodeCoverResponse("ep_001"))
        .thenAnswer(invocation -> ResponseEntity.ok().build());

    mockMvc.perform(get("/media/ep_001/cover"))
        .andExpect(status().isOk());

    verify(mediaService).buildEpisodeCoverResponse("ep_001");
  }

  @Test
  void testGetStyledEpisodeCoverRoutesToEpisodeCoverService() throws Exception {
    when(episodeCoverService.buildCoverResponse("playlist", "PL_123", "ep_003", false))
        .thenAnswer(invocation -> ResponseEntity.ok().build());

    mockMvc.perform(get("/media/feed/playlist/PL_123/episodes/ep_003/cover.jpg").param("v", "abc"))
        .andExpect(status().isOk());

    verify(episodeCoverService).buildCoverResponse("playlist", "PL_123", "ep_003", false);
  }

  @Test
  void testGetSmallStyledEpisodeCover() throws Exception {
    when(episodeCoverService.buildCoverResponse("channel", "UC_1", "ep_004", true))
        .thenAnswer(invocation -> ResponseEntity.ok().build());

    mockMvc.perform(get("/media/feed/channel/UC_1/episodes/ep_004/cover.jpg")
            .param("size", "small"))
        .andExpect(status().isOk());

    verify(episodeCoverService).buildCoverResponse("channel", "UC_1", "ep_004", true);
  }

  @Test
  void testGetEpisodeCoverWithJpgExtension() throws Exception {
    when(mediaService.buildEpisodeCoverResponse("ep_002"))
        .thenAnswer(invocation -> ResponseEntity.ok().build());

    mockMvc.perform(get("/media/ep_002/cover.jpg"))
        .andExpect(status().isOk());

    verify(mediaService).buildEpisodeCoverResponse("ep_002");
  }
}
