package com.prashant.KhetWallah.listing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class ListingPhotoService {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 8_000_000L;

    private final ListingRepository listings;
    private final Path uploadDirectory;

    public ListingPhotoService(
            ListingRepository listings,
            @Value("${app.upload-dir}") String uploadDirectory
    ) {
        this.listings = listings;
        this.uploadDirectory = Path.of(uploadDirectory)
                .toAbsolutePath()
                .normalize();
    }

    @Transactional
    public void upload(
            Long listingId,
            MultipartFile file,
            String authenticatedEmail
    ) {
        ListingEntity listing = listings.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Listing not found"
                ));

        if (listing.getOwner() == null
                || !listing.getOwner().getEmail().equals(authenticatedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only change photos for your own listings"
            );
        }

        if (listing.getStatus() == ListingStatus.CLOSED || listing.getStatus() == ListingStatus.HIDDEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Closed listings cannot be edited"
            );
        }

        String newName = writeImage(file);
        String oldName = listing.getPhotoFileName();

        try {
            listing.changePhoto(newName);
            listings.saveAndFlush(listing);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status == STATUS_COMMITTED) {
                                    deleteQuietly(oldName);
                                } else {
                                    deleteQuietly(newName);
                                }
                            }
                        }
                );
            } else {
                deleteQuietly(oldName);
            }
        } catch (RuntimeException exception) {
            deleteQuietly(newName);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public byte[] read(Long listingId) {
        ListingEntity listing = listings.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Listing not found"
                ));

        if (listing.getStatus() == ListingStatus.HIDDEN || listing.getOwner() != null && listing.getOwner().isSuspended()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Photo not found"
            );
        }

        return readFile(listing.getPhotoFileName());
    }

    byte[] readFile(String filename) {
        if (filename == null || !filename.matches("[0-9a-f-]{36}\\.jpg"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
        try { return Files.readAllBytes(uploadDirectory.resolve(filename)); }
        catch (IOException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"); }
    }

    String writeImage(MultipartFile file) {
        BufferedImage image = decodeImage(file);
        String filename = UUID.randomUUID() + ".jpg";
        try {
            Files.createDirectories(uploadDirectory);
            try (var output = ImageIO.createImageOutputStream(Files.newOutputStream(uploadDirectory.resolve(filename),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
                var writer = ImageIO.getImageWritersByFormatName("jpg").next();
                try {
                    var parameters = writer.getDefaultWriteParam();
                    parameters.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    parameters.setCompressionQuality(0.82f);
                    writer.setOutput(output);
                    writer.write(null, new javax.imageio.IIOImage(image, null, null), parameters);
                } finally { writer.dispose(); }
            }
            return filename;
        } catch (IOException ex) {
            deleteQuietly(filename);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the photo. Please try again.");
        }
    }

    private BufferedImage decodeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badPhoto("Select a photo");
        }

        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "The photo must be 5 MB or smaller"
            );
        }

        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);

            if (bytes.length > MAX_BYTES) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "The photo must be 5 MB or smaller"
                );
            }

            try (var imageInput = new MemoryCacheImageInputStream(
                    new ByteArrayInputStream(bytes)
            )) {
                var readers = ImageIO.getImageReaders(imageInput);

                if (!readers.hasNext()) {
                    throw badPhoto("Upload a valid JPEG or PNG image");
                }

                ImageReader reader = readers.next();

                try {
                    String format = reader.getFormatName();

                    if (!format.equalsIgnoreCase("JPEG")
                            && !format.equalsIgnoreCase("PNG")) {
                        throw badPhoto("Only JPEG and PNG images are supported");
                    }

                    reader.setInput(imageInput);

                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);

                    if (width <= 0 || height <= 0
                            || width > 4096 || height > 4096
                            || (long) width * height > MAX_PIXELS) {
                        throw badPhoto(
                                "Resize the photo to at most 4096 pixels per side "
                                        + "and 8 megapixels"
                        );
                    }

                    BufferedImage original = reader.read(0);

                    double scale = Math.min(1d, 1600d / Math.max(width, height));
                    int targetWidth = Math.max(1, (int)Math.round(width * scale));
                    int targetHeight = Math.max(1, (int)Math.round(height * scale));
                    BufferedImage clean = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

                    Graphics2D graphics = clean.createGraphics();

                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, targetWidth, targetHeight);
                        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
                    } finally {
                        graphics.dispose();
                    }

                    return clean;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException exception) {
            throw badPhoto("The image could not be read. Try another JPEG or PNG.");
        }
    }

    private ResponseStatusException badPhoto(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    void deleteQuietly(String filename) {
        if (filename == null
                || !filename.matches("[0-9a-f-]{36}\\.jpg")) {
            return;
        }

        try {
            Files.deleteIfExists(uploadDirectory.resolve(filename));
        } catch (IOException exception) {
            System.getLogger(ListingPhotoService.class.getName())
                    .log(System.Logger.Level.WARNING,
                            "Could not remove an unused listing photo");
        }
    }
}
