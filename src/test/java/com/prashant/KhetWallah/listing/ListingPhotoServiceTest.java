package com.prashant.KhetWallah.listing;

import com.prashant.KhetWallah.user.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingPhotoServiceTest {

    @TempDir
    Path directory;

    private ListingRepository repository;
    private ListingPhotoService service;
    private ListingEntity listing;

    @BeforeEach
    void setUp() {
        repository = mock(ListingRepository.class);
        service = new ListingPhotoService(repository, directory.toString());

        listing = new ListingEntity(
                "Tomato",
                new AppUser("Farmer", "farmer@example.com", "hash"),
                new BigDecimal("20.00"),
                new BigDecimal("30.00"),
                "Meerut"
        );

        when(repository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(listing));
        when(repository.findById(1L))
                .thenReturn(Optional.of(listing));
    }

    private MockMultipartFile validPhoto() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ImageIO.write(
                new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB),
                "png",
                output
        );

        return new MockMultipartFile(
                "photo",
                "crop.png",
                "image/png",
                output.toByteArray()
        );
    }

    @Test
    void ownerCanUploadAndReadPhoto() throws Exception {
        service.upload(1L, validPhoto(), "farmer@example.com");

        assertNotNull(listing.getPhotoFileName());
        assertTrue(Files.exists(directory.resolve(listing.getPhotoFileName())));
        assertTrue(service.read(1L).length > 0);
        verify(repository).saveAndFlush(listing);
    }

    @Test
    void anotherAccountCannotUpload() throws Exception {
        MockMultipartFile photo = validPhoto();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.upload(1L, photo, "buyer@example.com")
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertNull(listing.getPhotoFileName());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void fakeImageIsRejected() {
        MockMultipartFile fake = new MockMultipartFile(
                "photo", "fake.png", "image/png", "not an image".getBytes()
        );

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.upload(1L, fake, "farmer@example.com")
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertNull(listing.getPhotoFileName());
    }

    @Test
    void oversizedFileIsRejected() {
        MockMultipartFile large = new MockMultipartFile(
                "photo", "large.png", "image/png",
                new byte[5 * 1024 * 1024 + 1]
        );

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.upload(1L, large, "farmer@example.com")
        );

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatusCode());
    }

    @Test
    void closedListingRejectsPhoto() throws Exception {
        listing.close();
        MockMultipartFile photo = validPhoto();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.upload(1L, photo, "farmer@example.com")
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }
}