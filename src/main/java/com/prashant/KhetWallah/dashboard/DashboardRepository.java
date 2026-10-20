package com.prashant.KhetWallah.dashboard;

import com.prashant.KhetWallah.listing.ListingStatus;
import com.prashant.KhetWallah.order.OrderEntity;
import com.prashant.KhetWallah.order.OrderStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class DashboardRepository {
    private final EntityManager entityManager;

    public DashboardRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public long activeListings(Long userId) {
        return entityManager.createQuery("""
                select count(l) from ListingEntity l
                where l.owner.id = :userId and l.status = :status
                    and (l.availableUntil is null or l.availableUntil >= :today)
                    and l.owner.suspended = false
                """, Long.class)
                .setParameter("userId", userId)
                .setParameter("status", ListingStatus.ACTIVE)
                .setParameter("today", java.time.LocalDate.now())
                .getSingleResult();
    }

    public long pendingOrders(Long userId) {
        return entityManager.createQuery("""
                select count(o) from OrderEntity o
                where o.buyer.id = :userId and o.status = :status
                """, Long.class)
                .setParameter("userId", userId)
                .setParameter("status", OrderStatus.PENDING)
                .getSingleResult();
    }

    public long pendingReceivedOrders(Long userId) {
        return entityManager.createQuery("""
                select count(o) from OrderEntity o
                where o.listing.owner.id = :userId and o.status = :status
                """, Long.class)
                .setParameter("userId", userId)
                .setParameter("status", OrderStatus.PENDING)
                .getSingleResult();
    }

    public long upcomingPickups(Long userId, Instant now) {
        return entityManager.createQuery("""
                select count(o) from OrderEntity o
                left join o.listing.owner farmer
                where (o.buyer.id = :userId or farmer.id = :userId)
                    and o.status = :status and o.pickupAt >= :now
                    and o.pickupInstructions is not null
                    and length(trim(o.pickupInstructions)) > 0
                """, Long.class)
                .setParameter("userId", userId)
                .setParameter("status", OrderStatus.ACCEPTED)
                .setParameter("now", now)
                .getSingleResult();
    }

    public List<OrderEntity> upcomingPickupDetails(Long userId, Instant now) {
        return entityManager.createQuery("""
                select o from OrderEntity o
                join fetch o.listing l
                left join fetch l.owner farmer
                join fetch o.buyer buyer
                where (buyer.id = :userId or farmer.id = :userId)
                    and o.status = :status and o.pickupAt >= :now
                    and o.pickupInstructions is not null
                    and length(trim(o.pickupInstructions)) > 0
                order by o.pickupAt asc, o.id asc
                """, OrderEntity.class)
                .setParameter("userId", userId)
                .setParameter("status", OrderStatus.ACCEPTED)
                .setParameter("now", now)
                .setMaxResults(5)
                .getResultList();
    }
}
