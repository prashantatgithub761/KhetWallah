package com.prashant.KhetWallah.message;
import com.prashant.KhetWallah.error.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class OrderMessageControllerTest {
 final OrderMessageService service=mock(OrderMessageService.class);MockMvc mvc;
 @BeforeEach void setup(){mvc=MockMvcBuilders.standaloneSetup(new OrderMessageController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();}
 @Test void creationReturns201AndNoStoreForPrivateContent()throws Exception{when(service.send(10L,"Hello","buyer@test.example")).thenReturn(new OrderMessageService.MessageView(20L,10L,"Buyer",true,"Hello",false,Instant.now(),null));mvc.perform(post("/api/orders/10/messages").principal(()->"buyer@test.example").contentType("application/json").content("{\"body\":\"Hello\"}")).andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.body").value("Hello"));verify(service).send(10L,"Hello","buyer@test.example");}
 @Test void invalidBodiesAreRejectedBeforeCallingService()throws Exception{mvc.perform(post("/api/orders/10/messages").principal(()->"buyer@test.example").contentType("application/json").content("{\"body\":\" \"}")).andExpect(status().isBadRequest());mvc.perform(post("/api/orders/10/messages").principal(()->"buyer@test.example").contentType("application/json").content("{\"body\":\""+"x".repeat(2001)+"\"}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void listUsesPrincipalAndBoundedDefaults()throws Exception{when(service.list(10L,"farmer@test.example",0,20)).thenReturn(new OrderMessageService.MessagePage(List.of(),0,0,0,0));mvc.perform(get("/api/orders/10/messages").principal(()->"farmer@test.example")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));verify(service).list(10L,"farmer@test.example",0,20);}
 @Test void readRequiresPositiveLastSeenMessageId()throws Exception{mvc.perform(post("/api/orders/10/messages/read").principal(()->"farmer@test.example").contentType("application/json").content("{\"lastSeenId\":0}")).andExpect(status().isBadRequest());verifyNoInteractions(service);mvc.perform(post("/api/orders/10/messages/read").principal(()->"farmer@test.example").contentType("application/json").content("{\"lastSeenId\":20}")).andExpect(status().isNoContent());verify(service).markRead(10L,20L,"farmer@test.example");}
 @Test void reportRequiresReasonAndDelegatesIdentity()throws Exception{mvc.perform(post("/api/messages/20/report").principal(()->"farmer@test.example").contentType("application/json").content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());mvc.perform(post("/api/messages/20/report").principal(()->"farmer@test.example").contentType("application/json").content("{\"reason\":\"Unsafe request\"}")).andExpect(status().isNoContent());verify(service).report(20L,"Unsafe request","farmer@test.example");}
 @Test void moderationRoutesPassRequestedStateAndAuditReason()throws Exception{mvc.perform(post("/api/admin/messages/20/hide").principal(()->"admin@test.example").contentType("application/json").content("{\"reason\":\"Spam\"}")).andExpect(status().isNoContent());verify(service).moderate(20L,true,"Spam","admin@test.example");mvc.perform(post("/api/admin/messages/20/restore").principal(()->"admin@test.example").contentType("application/json").content("{\"reason\":\"Appeal accepted\"}")).andExpect(status().isNoContent());verify(service).moderate(20L,false,"Appeal accepted","admin@test.example");}
}
