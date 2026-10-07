package org.example.pensionatkademina.service.imp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pensionatkademina.client.CustomerClient;
import org.example.pensionatkademina.dto.BookingDto;
import org.example.pensionatkademina.dto.RoomDetailedDto;
import org.example.pensionatkademina.model.Booking;
import org.example.pensionatkademina.model.Room;
import org.example.pensionatkademina.repository.BookingRepository;
import org.example.pensionatkademina.repository.RoomRepository;
import org.example.pensionatkademina.service.BookingService;
import org.example.pensionatkademina.utility.RoomSize;
import org.example.pensionatkademina.utility.RoomType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImp implements BookingService {

    private final BookingRepository bookingRepository;
    private final CustomerClient customerClient;
    private final RoomRepository roomRepository;

    @Override
    public List<BookingDto> getAllBookings() {
        return bookingRepository.findAll()
                .stream()
                .map(this::toBookingDto)
                .toList();
    }

    @Override
    public BookingDto getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found!"));

        return toBookingDto(booking);
    }

    @Override
    public BookingDto createBooking(BookingDto bookingDto) {
        log.info("Creating new booking for customerId={}, roomId={}, checkIn={}, checkOut={}",
            bookingDto.getCustomerId(), bookingDto.getRoomId(),
            bookingDto.getCheckInDate(), bookingDto.getCheckOutDate());

        Booking booking = new Booking();
        BookingDto result = saveBooking(booking, bookingDto, null);

        log.info("Booking created successfully with ID={}", result.getId());
        return result;
    }

    @Override
    public BookingDto updateBooking(Long id, BookingDto bookingDto) {
        log.info("Updating booking ID={} with customerId={}, roomId={}",
            id, bookingDto.getCustomerId(), bookingDto.getRoomId());

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Booking not found for update: ID={}", id);
                    return new IllegalArgumentException("Booking not found!");
                });

        BookingDto result = saveBooking(booking, bookingDto, id);
        log.info("Booking ID={} updated successfully", id);
        return result;
    }

    @Override
    public void deleteBooking(Long id) {
        log.info("Deleting booking ID={}", id);

        if (!bookingRepository.existsById(id)) {
            log.warn("Attempted to delete non-existent booking: ID={}", id);
            throw new IllegalArgumentException("Booking does not exist!");
        }

        bookingRepository.deleteById(id);
        log.info("Booking ID={} deleted successfully", id);
    }

    @Override
    public boolean hasBookedRoom(Long customerId, Long roomId) {
        return bookingRepository.existsByCustomerIdAndRoom_Id(customerId, roomId);
    }

    @Override
    public List<RoomDetailedDto> searchAvailableRooms(LocalDate checkInDate,
                                                      LocalDate checkOutDate,
                                                      int numberOfGuests) {

        log.info("Searching for available rooms: checkIn={}, checkOut={}, guests={}",
            checkInDate, checkOutDate, numberOfGuests);

        if (!checkOutDate.isAfter(checkInDate)) {
            log.warn("Invalid search parameters: checkOut must be after checkIn. checkIn={}, checkOut={}",
                checkInDate, checkOutDate);
            throw new IllegalArgumentException("Check out must occur after check in!");
        }

        if (numberOfGuests < 1) {
            log.warn("Invalid guest count: {}", numberOfGuests);
            throw new IllegalArgumentException("Minimum amount of guests is 1!");
        }

        List<RoomDetailedDto> availableRooms = roomRepository.findAll()
                .stream()
                .filter(room -> numberOfGuests <= getMaxGuests(room))
                .filter(room -> !bookingRepository.roomIsBooked(
                        room.getId(),
                        checkInDate,
                        checkOutDate,
                        null
                ))
                .map(this::toRoomDto)
                .toList();

        log.info("Found {} available rooms for the search criteria", availableRooms.size());
        return availableRooms;
    }

    private BookingDto saveBooking(Booking booking,
                                   BookingDto bookingDto,
                                   Long bookingId) {

        Long customerId = customerClient.findCustomerById(bookingDto.getCustomerId()).getId();
                //.orElseThrow(() -> new IllegalArgumentException("Customer does not exist!"));

        Room room = roomRepository.findById(bookingDto.getRoomId())
                .orElseThrow(() -> {
                    log.warn("Room not found for booking: roomId={}", bookingDto.getRoomId());
                    return new IllegalArgumentException("Room does not exist!");
                });

        if (!bookingDto.getCheckOutDate().isAfter(bookingDto.getCheckInDate())) {
            log.warn("Invalid booking dates: checkIn={}, checkOut={}",
                bookingDto.getCheckInDate(), bookingDto.getCheckOutDate());
            throw new IllegalArgumentException("Check out must occur after check in!");
        }

        if (bookingDto.getNumberOfGuests() < 1) {
            log.warn("Invalid number of guests: {}", bookingDto.getNumberOfGuests());
            throw new IllegalArgumentException("Minimum amount of guests is 1!");
        }

        if (room.getType() == RoomType.SINGLE && bookingDto.getExtraBeds() > 0) {
            log.warn("Single room cannot have extra beds. roomId={}, extraBeds={}",
                bookingDto.getRoomId(), bookingDto.getExtraBeds());
            throw new IllegalArgumentException("Single rooms can't have extra beds!");
        }

        if (room.getType() == RoomType.DOUBLE
                && room.getSize() == RoomSize.SMALL
                && bookingDto.getExtraBeds() > 1) {

            log.warn("Small double room exceeds extra bed limit. roomId={}, extraBeds={}",
                bookingDto.getRoomId(), bookingDto.getExtraBeds());
            throw new IllegalArgumentException("Small double rooms can have a maximum of 1 extra beds!");
        }

        if (room.getType() == RoomType.DOUBLE
                && room.getSize() == RoomSize.LARGE
                && bookingDto.getExtraBeds() > 2) {

            log.warn("Large double room exceeds extra bed limit. roomId={}, extraBeds={}",
                bookingDto.getRoomId(), bookingDto.getExtraBeds());
            throw new IllegalArgumentException("Large double rooms can have a maximum of 2 extra beds!");
        }

        if (bookingDto.getNumberOfGuests() > getMaxGuests(room)) {
            log.warn("Too many guests for room. roomId={}, guests={}, maxGuests={}",
                bookingDto.getRoomId(), bookingDto.getNumberOfGuests(), getMaxGuests(room));
            throw new IllegalArgumentException("Too many guests!");
        }

        boolean roomBooked = bookingRepository.roomIsBooked(
                bookingDto.getRoomId(),
                bookingDto.getCheckInDate(),
                bookingDto.getCheckOutDate(),
                bookingId
        );

        if (roomBooked) {
            log.warn("Room is already booked for selected dates. roomId={}, checkIn={}, checkOut={}",
                bookingDto.getRoomId(), bookingDto.getCheckInDate(), bookingDto.getCheckOutDate());
            throw new IllegalArgumentException("The room is already booked on the selected dates.");
        }

        booking.setCustomerId(customerId);
        booking.setRoom(room);
        booking.setCheckInDate(bookingDto.getCheckInDate());
        booking.setCheckOutDate(bookingDto.getCheckOutDate());
        booking.setNumberOfGuests(bookingDto.getNumberOfGuests());
        booking.setExtraBeds(bookingDto.getExtraBeds());

        Booking savedBooking = bookingRepository.save(booking);
        log.debug("Booking saved to database with ID={}", savedBooking.getId());

        return toBookingDto(savedBooking);
    }

    private int getMaxGuests(Room room) {
        if (room.getType() == RoomType.SINGLE) {
            return 1;
        }
        if (room.getSize() == RoomSize.SMALL) {
            return 3;
        }else
            return 4;
    }

    private BookingDto toBookingDto(Booking booking) {
        BookingDto dto = new BookingDto();

        dto.setId(booking.getId());
        dto.setCustomerId(booking.getCustomerId());
        dto.setRoomId(booking.getRoom().getId());
        dto.setCheckInDate(booking.getCheckInDate());
        dto.setCheckOutDate(booking.getCheckOutDate());
        dto.setNumberOfGuests(booking.getNumberOfGuests());
        dto.setExtraBeds(booking.getExtraBeds());

        return dto;
    }

    private RoomDetailedDto toRoomDto(Room room) {
        RoomDetailedDto dto = new RoomDetailedDto();

        dto.setId(room.getId());
        dto.setType(room.getType());
        dto.setSize(room.getSize());

        return dto;
    }
}