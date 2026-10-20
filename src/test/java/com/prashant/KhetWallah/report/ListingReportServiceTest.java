package com.prashant.KhetWallah.report;

import com.prashant.KhetWallah.admin.*;
import com.prashant.KhetWallah.listing.*;
import com.prashant.KhetWallah.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingReportServiceTest {
    final ListingReportRepository reports = mock(ListingReportRepository.class);
    final ListingRepository listings = mock(ListingRepository.class);
    final AppUserRepository users = mock(AppUserRepository.class);
    final AdminActionRepository actions = mock(AdminActionRepository.class);
    final ListingReportService service = new ListingReportService(reports, listings, users, actions);
    final AppUser buyer = new AppUser("Buyer", "buyer@example.com", "hash");
    final AppUser farmer = new AppUser("Farmer", "farmer@example.com", "hash");
    final ListingEntity listing = new ListingEntity("Tomato", farmer, BigDecimal.TEN, BigDecimal.ONE, "Meerut");
    ListingReportServiceTest() {
        ReflectionTestUtils.setField(buyer, "id", 2L);
        ReflectionTestUtils.setField(listing, "id", 1L);
        when(users.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(listings.findByIdForUpdate(1L)).thenReturn(Optional.of(listing));
    }
    void admin() {
        AppUser admin = new AppUser("Admin", "admin@example.com", "hash");
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        when(users.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
    }
    ListingReport report() {
        ListingReport report = new ListingReport(listing, buyer, "Misleading information");
        when(reports.findByIdForUpdate(3L)).thenReturn(Optional.of(report));
        return report;
    }
    @Test void createsReportWithoutHidingListing() {
        when(reports.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(ListingReport.Status.OPEN, service.submit(1L, "  Wrong price  ", buyer.getEmail()).status());
        verify(reports).saveAndFlush(argThat(r -> r.getReason().equals("Wrong price")));
        assertEquals(ListingStatus.ACTIVE, listing.getStatus());
    }
    @Test void duplicateReportIsRejected() {
        when(reports.existsByListing_IdAndReporter_Id(1L, 2L)).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.submit(1L, "Wrong price", buyer.getEmail())).getStatusCode().value());
        verify(reports, never()).saveAndFlush(any());
    }
    @Test void ownListingCannotBeReported() {
        when(users.findByEmail(farmer.getEmail())).thenReturn(Optional.of(farmer));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.submit(1L, "Reason", farmer.getEmail())).getStatusCode().value());
    }
    @Test void suspendedAccountCannotReport() {
        buyer.suspend();
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.submit(1L, "Reason", buyer.getEmail())).getStatusCode().value());
    }
    @Test void invalidReasonCannotBeSubmitted() {
        assertThrows(ResponseStatusException.class, () -> service.submit(1L, " ", buyer.getEmail()));
        assertThrows(ResponseStatusException.class, () -> service.submit(1L, "x".repeat(501), buyer.getEmail()));
        verifyNoInteractions(reports);
    }
    @Test void inactiveListingCannotBeReported() {
        listing.hide();
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.submit(1L, "Reason", buyer.getEmail())).getStatusCode().value());
    }
    @Test void ordinaryUserCannotResolveReports() {
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.resolve(3L, true, "Reason", buyer.getEmail())).getStatusCode().value());
        verifyNoInteractions(reports, actions);
    }
    @Test void adminCanHideAndResolveWithAuditTrail() {
        admin(); ListingReport report = report();
        service.resolve(3L, true, "Verified abuse", "admin@example.com");
        assertEquals(ListingStatus.HIDDEN, listing.getStatus());
        assertEquals(ListingReport.Status.HIDDEN, report.getStatus());
        assertEquals("admin@example.com", report.getResolvedBy());
        assertNotNull(report.getResolvedAt());
        verify(actions, times(2)).save(any());
    }
    @Test void dismissPreservesListing() {
        admin(); ListingReport report = report();
        service.resolve(3L, false, "No violation", "admin@example.com");
        assertEquals(ListingStatus.ACTIVE, listing.getStatus());
        assertEquals(ListingReport.Status.DISMISSED, report.getStatus());
        verify(actions).save(any());
    }
    @Test void repeatedResolutionIsRejected() {
        admin(); ListingReport report = report();
        report.resolve(ListingReport.Status.DISMISSED, "admin@example.com", "Reviewed");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.resolve(3L, true, "Again", "admin@example.com")).getStatusCode().value());
        assertEquals(ListingStatus.ACTIVE, listing.getStatus());
        verifyNoInteractions(actions);
    }
    @Test void anotherReportOnHiddenListingCanBeResolved() {
        admin(); report(); listing.hide();
        service.resolve(3L, true, "Already removed", "admin@example.com");
        verify(actions).save(argThat(action -> action.getAction().equals("REPORT_HIDE")));
    }
}
