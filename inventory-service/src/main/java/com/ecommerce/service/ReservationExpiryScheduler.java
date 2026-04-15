package com.ecommerce.service;

import com.ecommerce.repository.InventoryRepository;
import com.ecommerce.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;


@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final ReservationRepository reservationRepo;
    private final InventoryRepository   inventoryRepo;

    private static final int RESERVATION_TIMEOUT_MINUTES = 30;

    @Scheduled(fixedDelay = 5 * 60 * 1000)  // every 5 minutes
    @Transactional
    public void expireStaleReservations() {
        Instant cutoff = Instant.now().minus(RESERVATION_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        Instant now    = Instant.now();

        int expired = reservationRepo.expireStaleReservations(cutoff, now);

        if (expired > 0) {
            // todo: release reservedQuantity on the Inventory
        }
    }
}
