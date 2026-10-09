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
import top.asimov.pigeon.service.MediaService;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

  @Mock
  private MediaService mediaService;

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
  void testGetEpisodeCoverWithJpgExtension() throws Exception {
    when(mediaService.buildEpisodeCoverResponse("ep_002"))
        .thenAnswer(invocation -> ResponseEntity.ok().build());

    mockMvc.perform(get("/media/ep_002/cover.jpg"))
        .andExpect(status().isOk());

    verify(mediaService).buildEpisodeCoverResponse("ep_002");
  }
}
