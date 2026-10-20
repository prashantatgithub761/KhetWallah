package com.prashant.KhetWallah.listing;
import com.prashant.KhetWallah.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.UUID;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","app.email.enabled=false"})
@Transactional
class ListingGalleryPersistenceIntegrationTest {
 static final Path photos=temp();
 static Path temp(){try{return Files.createTempDirectory("khet-gallery-integration-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void directory(DynamicPropertyRegistry p){p.add("app.upload-dir",photos::toString);}
 @Autowired ListingGalleryService gallery;@Autowired ListingPhotoService files;@Autowired ListingPhotoRepository extras;@Autowired ListingRepository crops;@Autowired AppUserRepository users;@Autowired EntityManager em;
 AppUser owner;ListingEntity crop;
 @BeforeEach void setup(){owner=users.saveAndFlush(new AppUser("Gallery farmer",UUID.randomUUID()+"@example.test","unused-hash"));crop=crops.saveAndFlush(new ListingEntity("Gallery fixture",owner,new BigDecimal("30"),BigDecimal.TEN,"Test area"));}
 MockMultipartFile image()throws Exception{var img=new BufferedImage(24,24,BufferedImage.TYPE_INT_RGB);var out=new ByteArrayOutputStream();ImageIO.write(img,"png",out);return new MockMultipartFile("photo","fixture.png","image/png",out.toByteArray());}
 @Test void coverExtraAndPromotionStayConsistentAndNewFilesDisappearOnRollback()throws Exception{
  gallery.add(crop.getId(),image(),owner.getEmail());gallery.add(crop.getId(),image(),owner.getEmail());assertEquals(2,gallery.list(crop.getId()).size());String first=crop.getPhotoFileName();var extra=extras.findByListingIdOrderByIdAsc(crop.getId()).getFirst();assertTrue(Files.exists(photos.resolve(first)));assertTrue(gallery.read(crop.getId(),extra.getId()).length>0);
  gallery.remove(crop.getId(),0L,owner.getEmail());assertEquals(extra.getFileName(),crop.getPhotoFileName());assertEquals(1,gallery.list(crop.getId()).size());assertTrue(Files.exists(photos.resolve(first)));TestTransaction.flagForRollback();TestTransaction.end();assertFalse(Files.exists(photos.resolve(first)));assertFalse(Files.exists(photos.resolve(extra.getFileName())));
 }
 @Test void hiddenGalleryAndOtherAccountCannotReadOrEditPrivatePhotos()throws Exception{
  gallery.add(crop.getId(),image(),owner.getEmail());assertEquals(403,assertThrows(ResponseStatusException.class,()->gallery.add(crop.getId(),image(),"outsider@example.test")).getStatusCode().value());crop.hide();em.flush();assertEquals(404,assertThrows(ResponseStatusException.class,()->gallery.list(crop.getId())).getStatusCode().value());assertEquals(404,assertThrows(ResponseStatusException.class,()->files.read(crop.getId())).getStatusCode().value());
 }
}
