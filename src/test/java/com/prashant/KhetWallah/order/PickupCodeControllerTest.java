package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.config.PasswordConfig;
import com.prashant.KhetWallah.config.SecurityConfig;
import com.prashant.KhetWallah.error.GlobalExceptionHandler;
import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PickupCodeController.class)
@Import({SecurityConfig.class,PasswordConfig.class,GlobalExceptionHandler.class})
@WithMockUser(username="buyer@example.test",roles="USER")
class PickupCodeControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PickupCodeService codes;
    @MockitoBean AppUserRepository users;
    @BeforeEach void activeAccount(){when(users.findByEmail(anyString())).thenAnswer(i->Optional.of(new AppUser("Test",i.getArgument(0),"hash")));}
    @Test void issuesOnlyForSignedInPrincipalAndDoesNotCacheTheCode() throws Exception {
        when(codes.issue(3L,"buyer@example.test")).thenReturn(new PickupCodeService.Challenge("012345",Instant.parse("2099-01-01T10:00:00Z"),60));
        mvc.perform(post("/api/orders/3/pickup-code").with(csrf()))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.code").value("012345")).andExpect(jsonPath("$.codeHash").doesNotExist());
        verify(codes).issue(3L,"buyer@example.test");
    }
    @Test @WithMockUser(username="farmer@example.test",roles="USER")
    void confirmationUsesAuthenticatedFarmerAndNoStore() throws Exception {
        mvc.perform(post("/api/orders/3/confirm-pickup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"012345\"}"))
            .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","no-store"));
        verify(codes).confirm(3L,"farmer@example.test","012345");
    }
    @Test void mutationsRequireCsrf() throws Exception {
        mvc.perform(post("/api/orders/3/pickup-code")).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/3/confirm-pickup").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"012345\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(codes);
    }
    @Test @WithAnonymousUser void guestsCannotGenerateOrConfirm() throws Exception {
        mvc.perform(post("/api/orders/3/pickup-code").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/orders/3/confirm-pickup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"012345\"}"))
            .andExpect(status().is3xxRedirection());
        verifyNoInteractions(codes);
    }
    @Test void blankAndExcessivelyLongCodesAreRejectedBeforeService() throws Exception {
        for(String code:new String[]{"", "x".repeat(129)}) mvc.perform(post("/api/orders/3/confirm-pickup").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+code+"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.code").isNotEmpty());
        verifyNoInteractions(codes);
    }
}
