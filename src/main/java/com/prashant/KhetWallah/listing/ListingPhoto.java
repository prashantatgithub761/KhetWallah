package com.prashant.KhetWallah.listing;
import jakarta.persistence.*;
@Entity
@Table(name="listing_photos", indexes=@Index(name="idx_listing_photos_listing", columnList="listing_id,id"))
public class ListingPhoto {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="listing_id", nullable=false) private Long listingId;
    @Column(name="file_name", nullable=false, length=60) private String fileName;
    protected ListingPhoto() {}
    public ListingPhoto(Long listingId,String fileName) { this.listingId=listingId; this.fileName=fileName; }
    public Long getId() { return id; }
    public Long getListingId() { return listingId; }
    public String getFileName() { return fileName; }
}
