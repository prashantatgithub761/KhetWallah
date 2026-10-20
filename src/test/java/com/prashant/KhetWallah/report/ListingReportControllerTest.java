package com.prashant.KhetWallah.report;

import com.prashant.KhetWallah.config.SecurityConfig;
import com.prashant.KhetWallah.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ListingReportController.class)
@Import(SecurityConfig.class)
class ListingReportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ListingReportService service;
    @MockitoBean AppUserRepository users;
    void activeBuyer() {
        when(users.findByEmail("buyer@example.com")).thenReturn(Optional.of(new AppUser("Buyer", "buyer@example.com", "hash")));
    }
    @Test @WithMockUser(username="buyer@example.com")
    void reportUsesAuthenticatedIdentity() throws Exception {
        activeBuyer();
        when(service.submit(1L,"Misleading", "buyer@example.com"))
                .thenReturn(new ListingReportService.Receipt(3L, ListingReport.Status.OPEN));
        mvc.perform(post("/api/listings/1/reports").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Misleading\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(3));
        verify(service).submit(1L,"Misleading", "buyer@example.com");
    }
    @Test @WithMockUser(username="buyer@example.com")
    void csrfIsRequired() throws Exception {
        mvc.perform(post("/api/listings/1/reports").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Misleading\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(username="buyer@example.com")
    void emptyReasonIsRejected() throws Exception {
        activeBuyer();
        mvc.perform(post("/api/listings/1/reports").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(username="buyer@example.com")
    void reportsArePrivateToAdmins() throws Exception {
        mvc.perform(get("/api/admin/reports")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/reports/3/hide").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Abuse\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void anonymousCannotSubmitReport() throws Exception {
        mvc.perform(post("/api/listings/1/reports").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Misleading\"}"))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(username="admin@example.com", roles="ADMIN")
    void adminCanResolveWithCsrfUsingOwnIdentity() throws Exception {
        AppUser admin = org.mockito.Mockito.mock(AppUser.class);
        when(admin.getRole()).thenReturn(UserRole.ADMIN);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        mvc.perform(post("/api/admin/reports/3/hide").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Verified abuse\"}"))
                .andExpect(status().isOk());
        verify(service).resolve(3L, true, "Verified abuse", "admin@example.com");
    }
}
