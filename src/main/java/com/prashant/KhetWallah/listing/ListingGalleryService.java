package com.prashant.KhetWallah.listing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class ListingGalleryService {
    private final ListingRepository listings;
    private final ListingPhotoRepository photos;
    private final ListingPhotoService files;
    public ListingGalleryService(ListingRepository listings,ListingPhotoRepository photos,ListingPhotoService files) {
        this.listings=listings; this.photos=photos; this.files=files;
    }
    public record Photo(Long id,String url,boolean cover) {}
    private ListingEntity visible(Long id) {
        ListingEntity l=listings.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Listing not found"));
        if(l.getStatus()==ListingStatus.HIDDEN || l.getOwner()!=null && l.getOwner().isSuspended())
            throw error(HttpStatus.NOT_FOUND,"Photo not found");
        return l;
    }
    public List<Photo> list(Long id) {
        ListingEntity l=visible(id);
        List<Photo> result=new ArrayList<>();
        if(l.getPhotoFileName()!=null) result.add(new Photo(0L,"/api/listings/"+id+"/photo",true));
        for(ListingPhoto p:photos.findByListingIdOrderByIdAsc(id)) result.add(new Photo(p.getId(),"/api/listings/"+id+"/photos/"+p.getId(),false));
        return result;
    }
    public byte[] read(Long id,Long photoId) {
        visible(id);
        ListingPhoto p=photos.findById(photoId).filter(v -> id.equals(v.getListingId()))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Photo not found"));
        return files.readFile(p.getFileName());
    }
    private ListingEntity editable(Long id,String email) {
        ListingEntity l=listings.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Listing not found"));
        if(l.getOwner()==null || !l.getOwner().getEmail().equals(email)) throw error(HttpStatus.FORBIDDEN,"Only the owner can change photos");
        if(l.getStatus()==ListingStatus.HIDDEN || l.getStatus()==ListingStatus.CLOSED) throw error(HttpStatus.CONFLICT,"Hidden or closed listings cannot be edited");
        return l;
    }
    @Transactional
    public void add(Long id,MultipartFile file,String email) {
        ListingEntity l=editable(id,email);
        if(photos.countByListingId(id)+(l.getPhotoFileName()==null?0:1)>=4) throw error(HttpStatus.CONFLICT,"A listing can have up to four photos");
        String filename=files.writeImage(file);
        try {
            if(l.getPhotoFileName()==null) { l.changePhoto(filename); listings.saveAndFlush(l); }
            else photos.saveAndFlush(new ListingPhoto(id,filename));
            cleanupAfter(false,filename);
        } catch(RuntimeException ex) { files.deleteQuietly(filename); throw ex; }
    }
    @Transactional
    public void remove(Long id,Long photoId,String email) {
        ListingEntity l=editable(id,email);
        String old;
        if(photoId==0L) {
            old=l.getPhotoFileName();
            if(old==null) throw error(HttpStatus.NOT_FOUND,"Photo not found");
            List<ListingPhoto> extra=photos.findByListingIdOrderByIdAsc(id);
            if(extra.isEmpty()) l.changePhoto(null);
            else { l.changePhoto(extra.get(0).getFileName()); photos.delete(extra.get(0)); }
            listings.saveAndFlush(l);
        } else {
            ListingPhoto p=photos.findById(photoId).filter(v -> id.equals(v.getListingId()))
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Photo not found"));
            old=p.getFileName(); photos.delete(p); photos.flush();
        }
        cleanupAfter(true,old);
    }
    private void cleanupAfter(boolean committed,String filename) {
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCompletion(int status) { if((status==STATUS_COMMITTED)==committed) files.deleteQuietly(filename); }
        });
        else if(committed) files.deleteQuietly(filename);
    }
    private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
