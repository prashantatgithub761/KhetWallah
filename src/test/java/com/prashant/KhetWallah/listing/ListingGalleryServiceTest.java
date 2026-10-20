package com.prashant.KhetWallah.listing;
import com.prashant.KhetWallah.user.AppUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ListingGalleryServiceTest {
    @TempDir Path directory;
    ListingRepository listings;ListingPhotoRepository photos;ListingPhotoService files;ListingGalleryService gallery;ListingEntity crop;
    @BeforeEach void setup() {
        listings=mock(ListingRepository.class);photos=mock(ListingPhotoRepository.class);files=new ListingPhotoService(listings,directory.toString());
        gallery=new ListingGalleryService(listings,photos,files);
        crop=new ListingEntity("Tomato",new AppUser("Farmer","farmer@example.test","hash"),new BigDecimal("10"),new BigDecimal("20"),"Meerut");
        when(listings.findByIdForUpdate(1L)).thenReturn(Optional.of(crop));when(listings.findById(1L)).thenReturn(Optional.of(crop));
        when(photos.findByListingIdOrderByIdAsc(1L)).thenReturn(List.of());
    }
    MockMultipartFile image(int width,int height) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",output);
        return new MockMultipartFile("photo","crop.png","image/png",output.toByteArray());
    }
    @Test void firstGalleryUploadBecomesCoverAndResizes() throws Exception {
        gallery.add(1L,image(2000,1000),"farmer@example.test");
        var decoded=ImageIO.read(new ByteArrayInputStream(files.read(1L)));
        assertEquals(1600,decoded.getWidth());assertEquals(800,decoded.getHeight());
        assertEquals(0L,gallery.list(1L).getFirst().id());
    }
    @Test void fourPhotoLimitIsEnforcedUnderListingLock() throws Exception {
        crop.changePhoto("00000000-0000-0000-0000-000000000000.jpg");when(photos.countByListingId(1L)).thenReturn(3L);
        assertThrows(ResponseStatusException.class,()->gallery.add(1L,image(10,10),"farmer@example.test"));
        verify(photos,never()).saveAndFlush(any());
    }
    @Test void otherOwnerAndHiddenListingCannotBeChangedOrRead() throws Exception {
        assertThrows(ResponseStatusException.class,()->gallery.add(1L,image(10,10),"other@example.test"));
        crop.hide();assertThrows(ResponseStatusException.class,()->gallery.add(1L,image(10,10),"farmer@example.test"));
        assertThrows(ResponseStatusException.class,()->gallery.list(1L));
    }
    @Test void failedDatabaseSaveCleansNewFile() throws Exception {
        doThrow(new IllegalStateException("test rollback")).when(listings).saveAndFlush(crop);
        assertThrows(IllegalStateException.class,()->gallery.add(1L,image(10,10),"farmer@example.test"));
        try(var paths=Files.list(directory)) {assertEquals(0,paths.count());}
    }
    @Test void photoIdFromAnotherListingCannotBeRemoved() {
        when(photos.findById(9L)).thenReturn(Optional.of(new ListingPhoto(2L,"00000000-0000-0000-0000-000000000000.jpg")));
        assertThrows(ResponseStatusException.class,()->gallery.remove(1L,9L,"farmer@example.test"));
        verify(photos,never()).delete(any());
    }
}
