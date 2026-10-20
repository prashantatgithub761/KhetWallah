package com.prashant.KhetWallah.admin;

import com.prashant.KhetWallah.config.SecurityConfig;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminService service;
    @MockitoBean AppUserRepository users;

    @Test
    @WithMockUser(roles = "USER")
    void ordinaryAccountCannotOpenAdminApi() throws Exception {
        mvc.perform(get("/api/admin/me")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanOpenAdminApi() throws Exception {
        var account = org.mockito.Mockito.mock(com.prashant.KhetWallah.user.AppUser.class);
        org.mockito.Mockito.when(account.getRole()).thenReturn(com.prashant.KhetWallah.user.UserRole.ADMIN);
        org.mockito.Mockito.when(users.findByEmail("user")).thenReturn(java.util.Optional.of(account));
        mvc.perform(get("/api/admin/me")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void moderationRequiresCsrfToken() throws Exception {
        mvc.perform(post("/api/admin/listings/1/hide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Abusive listing\"}"))
                .andExpect(status().isForbidden());
    }
}
