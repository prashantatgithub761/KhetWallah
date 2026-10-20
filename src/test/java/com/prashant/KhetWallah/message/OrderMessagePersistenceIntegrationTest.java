package com.prashant.KhetWallah.message;
import com.prashant.KhetWallah.admin.AdminActionRepository;
import com.prashant.KhetWallah.order.*;
import com.prashant.KhetWallah.listing.*;
import com.prashant.KhetWallah.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;

/** Synthetic rows roll back; the parent runner supplies explicitly local database settings. */
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","app.email.enabled=false"})
@Transactional
class OrderMessagePersistenceIntegrationTest {
 @Autowired OrderMessageService service;@Autowired OrderMessageRepository messages;@Autowired AppUserRepository users;@Autowired ListingRepository listings;@Autowired OrderRepository orders;@Autowired MessageReportRepository reports;@Autowired AdminActionRepository actions;@Autowired EntityManager entityManager;
 @Test void conversationsReadUpdatesAndRateBudgetUsePersistedPrivateRows(){String marker=UUID.randomUUID().toString();AppUser farmer=user("Farmer",marker+"f@example.test"),buyer=user("Buyer",marker+"b@example.test"),other=user("Other",marker+"o@example.test");ListingEntity crop=listings.saveAndFlush(new ListingEntity("Test crop",farmer,new BigDecimal("500"),BigDecimal.TEN,"Test area"));OrderEntity order=orders.saveAndFlush(new OrderEntity(crop,buyer,BigDecimal.ONE));var sent=service.send(order.getId(),"Only these participants",buyer.getEmail());entityManager.flush();entityManager.clear();assertEquals(1,service.unread(farmer.getEmail()));assertEquals(0,service.unread(buyer.getEmail()));assertEquals(1,service.conversations(farmer.getEmail(),0,20).getTotalElements());assertEquals(0,service.conversations(other.getEmail(),0,20).getTotalElements());assertEquals(404,assertThrows(ResponseStatusException.class,()->service.list(order.getId(),other.getEmail(),0,20)).getStatusCode().value());service.markRead(order.getId(),sent.id(),buyer.getEmail());assertEquals(1,service.unread(farmer.getEmail()));service.markRead(order.getId(),sent.id(),farmer.getEmail());assertEquals(0,service.unread(farmer.getEmail()));for(int i=0;i<9;i++)service.send(order.getId(),"Message "+i,buyer.getEmail());entityManager.flush();entityManager.clear();assertEquals(10,messages.countBySender_IdAndCreatedAtAfter(buyer.getId(),java.time.Instant.now().minusSeconds(60))); // persisted across context reload
 // Use a fresh service object to prove the budget is database-backed, not process memory.
 OrderMessageService fresh=new OrderMessageService(messages,reports,orders,users,actions);assertEquals(429,assertThrows(ResponseStatusException.class,()->fresh.send(order.getId(),"Too many",buyer.getEmail())).getStatusCode().value());}
 private AppUser user(String name,String email){return users.saveAndFlush(new AppUser(name,email,"unused-test-hash"));}
}
