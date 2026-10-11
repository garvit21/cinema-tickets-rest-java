package uk.gov.dwp.engineering.recruitment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BookingConfirmationTest {

  @Test
  void givenBookingConfirmation_whenGetAccount_thenExpectedValueReturned() {
    final BookingConfirmation bookingConfirmation = new BookingConfirmation(99L);
    assertEquals(99L, bookingConfirmation.accountId());
  }

  @Test
  void givenCompatibilityConstructor_whenCreated_thenSeatCountAndTotalCostHaveSafeDefaults() {
    final BookingConfirmation bookingConfirmation = new BookingConfirmation(99L);
    assertEquals(0L, bookingConfirmation.seatCount());
    assertEquals(0, BigDecimal.ZERO.compareTo(bookingConfirmation.totalCost()));
  }

  @Test
  void givenCalculatedConfirmation_whenAccessed_thenAllFieldsReturned() {
    final BookingConfirmation bookingConfirmation =
        new BookingConfirmation(12L, 3L, new BigDecimal("69.48"));
    assertEquals(12L, bookingConfirmation.accountId());
    assertEquals(3L, bookingConfirmation.seatCount());
    assertEquals(0, new BigDecimal("69.48").compareTo(bookingConfirmation.totalCost()));
  }
}
