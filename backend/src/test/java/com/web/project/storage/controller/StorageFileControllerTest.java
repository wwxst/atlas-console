package com.web.project.storage.controller;

import com.web.project.storage.entity.StorageFile;
import com.web.project.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.net.URI;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StorageFileControllerTest {
    @Test void localGetAndHeadHaveContentHeadersAndUnsupportedWriteIsRejected() throws Exception {
        StorageService service = mock(StorageService.class);
        StorageFile file = new StorageFile(); file.setContentType("image/png"); file.setSizeBytes(3);
        when(service.content(1L)).thenReturn(new StorageService.FileContent(file, new ByteArrayResource(new byte[]{1, 2, 3}), null));
        var mvc = MockMvcBuilders.standaloneSetup(new StorageFileController(service)).build();
        mvc.perform(get("/files/1")).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andExpect(header().string("Content-Type", "image/png"));
        mvc.perform(head("/files/1")).andExpect(status().isOk()).andExpect(content().string(""))
                .andExpect(header().string("Content-Length", "3"));
        mvc.perform(put("/files/1")).andExpect(status().isMethodNotAllowed());
    }

    @Test void remoteHeadRedirectsWithoutExposingCredentials() throws Exception {
        StorageService service = mock(StorageService.class);
        when(service.content(1L)).thenReturn(new StorageService.FileContent(new StorageFile(), null, URI.create("https://cdn.example.com/avatar.png")));
        var mvc = MockMvcBuilders.standaloneSetup(new StorageFileController(service)).build();
        mvc.perform(head("/files/1")).andExpect(status().isFound()).andExpect(content().string(""))
                .andExpect(header().string("Location", "https://cdn.example.com/avatar.png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/files/404")).andExpect(status().isNotFound());
    }
}
