package com.prashant.KhetWallah.farmer;
import com.prashant.KhetWallah.config.*;
import com.prashant.KhetWallah.error.GlobalExceptionHandler;
import com.prashant.KhetWallah.insights.*;
import com.prashant.KhetWallah.saved.*;
import com.prashant.KhetWallah.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({FarmerProfileController.class,SavedController.class,SellerInsightsController.class})
@Import({SecurityConfig.class,PasswordConfig.class,GlobalExceptionHandler.class})
@WithMockUser(username="owner@example.test",roles="USER")
class FarmerMarketplaceControllerTest {
 @Autowired MockMvc mvc;
 @MockitoBean FarmerProfileService profiles;@MockitoBean SavedService saved;@MockitoBean SellerInsightsService insights;@MockitoBean AppUserRepository users;
 @BeforeEach void active(){when(users.findByEmail(anyString())).thenAnswer(i->Optional.of(new AppUser("Owner",i.getArgument(0),"hash")));}
 @Test @WithAnonymousUser void publicProfileOnlyReturnsSafePublicDto() throws Exception {
  var p=new FarmerProfileService.ProfileView(7L,"Farmer","Farm","About crops","Meerut","250001");
  when(profiles.publicProfile(7L,0,12)).thenReturn(new FarmerProfileService.FarmerPage(p,new FarmerProfileService.Rating(0,null),List.of(),0,12,0,0));
  mvc.perform(get("/api/farmers/7")).andExpect(status().isOk()).andExpect(jsonPath("$.profile.displayName").value("Farmer")).andExpect(jsonPath("$.profile.email").doesNotExist()).andExpect(jsonPath("$.profile.passwordHash").doesNotExist());
 }
 @Test void privateProfileUsesPrincipalAndNoStore() throws Exception {
  when(profiles.own("owner@example.test")).thenReturn(new FarmerProfileService.ProfileView(1L,"Owner",null,null,null,null));
  mvc.perform(get("/api/me/farmer-profile")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));verify(profiles).own("owner@example.test");
 }
 @Test void editsRequireCsrfAndValidIndianPin() throws Exception {
  String body="{\"farmName\":\"Farm\",\"pinCode\":\"250001\"}";
  mvc.perform(put("/api/me/farmer-profile").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  mvc.perform(put("/api/me/farmer-profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"pinCode\":\"000000\"}")).andExpect(status().isBadRequest());
  verifyNoInteractions(profiles);
 }
 @Test void profileEditUsesAuthenticatedAccount() throws Exception {
  mvc.perform(put("/api/me/farmer-profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"farmName\":\"My farm\",\"pinCode\":\"250001\"}")).andExpect(status().isOk());
  verify(profiles).update(eq("owner@example.test"),argThat(r->r.farmName().equals("My farm")&&r.pinCode().equals("250001")));
 }
 @Test void savedMutationsArePrivateAndCsrfProtected() throws Exception {
  mvc.perform(put("/api/me/saved-listings/9")).andExpect(status().isForbidden());
  mvc.perform(put("/api/me/saved-listings/9").with(csrf())).andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(delete("/api/me/saved-farmers/4").with(csrf())).andExpect(status().isNoContent());
  verify(saved).save("LISTING",9L,"owner@example.test");verify(saved).remove("FARMER",4L,"owner@example.test");
 }
 @Test @WithAnonymousUser void guestsCannotReadPrivateSavedOrInsights() throws Exception {
  for(String url:List.of("/api/me/saved-listings","/api/me/saved-farmers","/api/me/insights","/api/me/farmer-profile"))mvc.perform(get(url)).andExpect(status().is3xxRedirection());verifyNoInteractions(saved,insights,profiles);
 }
 @Test void privateSavedStateDoesNotCache() throws Exception {
  when(saved.state("LISTING",8L,"owner@example.test")).thenReturn(new SavedService.SavedState(true));
  mvc.perform(get("/api/me/saved-listings/8")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.saved").value(true));
 }
}
