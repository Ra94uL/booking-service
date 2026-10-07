package org.example.pensionatkademina.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pensionatkademina.dto.BookingCheckResponseDto;
import org.example.pensionatkademina.service.imp.BookingServiceImp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bookings")
public class BookingApiController {

    private final BookingServiceImp bookingService;

    @GetMapping("/check")
    public BookingCheckResponseDto checkBooking(
            @RequestParam Long customerId,
            @RequestParam Long roomId) {

        log.info("Received booking check request: customerId={}, roomId={}", customerId, roomId);

        try {
            boolean booked = bookingService.hasBookedRoom(customerId, roomId);
            log.info("Booking check result: customerId={}, roomId={}, booked={}",
                customerId, roomId, booked);
            return new BookingCheckResponseDto(booked);
        } catch (Exception ex) {
            log.error("Error checking booking for customerId={}, roomId={}: {}",
                customerId, roomId, ex.getMessage(), ex);
            throw ex;
        }
    }
}