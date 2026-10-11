package uk.gov.dwp.engineering.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.dwp.engineering.recruitment.domain.TicketType.ADULT;
import static uk.gov.dwp.engineering.recruitment.domain.TicketType.CHILD;
import static uk.gov.dwp.engineering.recruitment.domain.TicketType.INFANT;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

@ExtendWith(MockitoExtension.class)
class CinemaTicketsServiceImplTest {

  @Mock
  private PaymentService paymentService;

  @Mock
  private SeatReservationService seatReservationService;

  private CinemaTicketsServiceImpl cinemaTicketsService;

  @BeforeEach
  void setUp() {
    cinemaTicketsService = new CinemaTicketsServiceImpl(paymentService, seatReservationService);
  }

  @Test
  void givenValidBooking_whenPurchase_thenProvidersCalledInOrderWithExactTotals() {
    final BookingConfirmation confirmation = cinemaTicketsService.purchaseTickets(10L,
        new TicketRequest(ADULT, 2),
        new TicketRequest(CHILD, 1),
        new TicketRequest(INFANT, 1));

    final BigDecimal expectedCost = new BigDecimal("69.48");
    assertEquals(10L, confirmation.accountId());
    assertEquals(3L, confirmation.seatCount());
    assertEquals(0, expectedCost.compareTo(confirmation.totalCost()));

    final InOrder inOrder = inOrder(paymentService, seatReservationService);
    inOrder.verify(paymentService).debitAccount(eq(10L), eq(expectedCost));
    inOrder.verify(seatReservationService).reserveSeats(eq(10L), eq(3L));
  }

  @Test
  void givenRepeatedTicketTypes_whenPurchase_thenQuantitiesAreAggregated() {
    final BookingConfirmation confirmation = cinemaTicketsService.purchaseTickets(5L,
        new TicketRequest(ADULT, 1),
        new TicketRequest(CHILD, 2),
        new TicketRequest(ADULT, 1),
        new TicketRequest(INFANT, 0),
        new TicketRequest(CHILD, 1));

    assertEquals(5L, confirmation.seatCount());
    assertEquals(0, new BigDecimal("104.48").compareTo(confirmation.totalCost()));
    verify(paymentService).debitAccount(eq(5L), eq(new BigDecimal("104.48")));
    verify(seatReservationService).reserveSeats(eq(5L), eq(5L));
  }

  @Test
  void givenInfantBeforeAdult_whenTotalsValid_thenBookingSucceeds() {
    final BookingConfirmation confirmation = cinemaTicketsService.purchaseTickets(7L,
        new TicketRequest(INFANT, 1),
        new TicketRequest(ADULT, 1));

    assertEquals(1L, confirmation.seatCount());
    assertEquals(0, new BigDecimal("25.99").compareTo(confirmation.totalCost()));
    verify(paymentService).debitAccount(eq(7L), eq(new BigDecimal("25.99")));
    verify(seatReservationService).reserveSeats(eq(7L), eq(1L));
  }

  @Test
  void givenTwentyFiveTicketsIncludingInfants_whenPurchase_thenAllowedAndInfantsHaveNoSeat() {
    final BookingConfirmation confirmation = cinemaTicketsService.purchaseTickets(3L,
        new TicketRequest(ADULT, 10),
        new TicketRequest(CHILD, 10),
        new TicketRequest(INFANT, 5));

    assertEquals(20L, confirmation.seatCount());
    assertEquals(0, new BigDecimal("434.90").compareTo(confirmation.totalCost()));
    verify(seatReservationService).reserveSeats(eq(3L), eq(20L));
  }

  @Test
  void givenTwentySixTickets_whenPurchase_thenRejectedAndProvidersNotCalled() {
    final InvalidBookingException exception = assertThrows(InvalidBookingException.class,
        () -> cinemaTicketsService.purchaseTickets(3L, new TicketRequest(ADULT, 26)));

    assertTrue(exception.getMessage().contains("25"));
    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenMoreInfantsThanAdults_whenPurchase_thenMessageExplainsAdditionalAdultsNeeded() {
    final InvalidBookingException exception = assertThrows(InvalidBookingException.class,
        () -> cinemaTicketsService.purchaseTickets(3L,
            new TicketRequest(INFANT, 3),
            new TicketRequest(ADULT, 1)));

    assertTrue(exception.getMessage().contains("2 additional adult"));
    assertTrue(exception.getMessage().toLowerCase().contains("each infant requires an adult"));
    assertTrue(exception.getMessage().toLowerCase().contains("reduce"));
    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenNoAdult_whenPurchase_thenRejectedAndProvidersNotCalled() {
    assertThrows(InvalidBookingException.class,
        () -> cinemaTicketsService.purchaseTickets(3L, new TicketRequest(CHILD, 2)));
    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenNullAccountId_whenPurchase_thenRejectedAndProvidersNotCalled() {
    assertThrows(InvalidBookingException.class,
        () -> cinemaTicketsService.purchaseTickets(null, new TicketRequest(ADULT, 1)));
    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenNonPositiveAccountId_whenPurchase_thenRejectedAndProvidersNotCalled() {
    assertThrows(InvalidBookingException.class,
        () -> cinemaTicketsService.purchaseTickets(0L, new TicketRequest(ADULT, 1)));
    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenNegativeQuantity_whenPurchase_thenRejectedAndProvidersNotCalled() {
    assertThrows(InvalidBookingException.class,
        () -> cinemaTicketsService.purchaseTickets(3L, new TicketRequest(ADULT, -1)));
    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenOnlyZeroQuantities_whenPurchase_thenRejectedAndProvidersNotCalled() {
    assertThrows(InvalidBookingException.class,
        () -> cinemaTicketsService.purchaseTickets(3L,
            new TicketRequest(ADULT, 0),
            new TicketRequest(CHILD, 0)));
    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenNullTicketRequests_whenPurchase_thenRejectedAndProvidersNotCalled() {
    assertThrows(InvalidBookingException.class,
        () -> cinemaTicketsService.purchaseTickets(3L, (TicketRequest[]) null));
    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenNullTicketRequestEntry_whenPurchase_thenRejectedAndProvidersNotCalled() {
    assertThrows(InvalidBookingException.class,
        () -> cinemaTicketsService.purchaseTickets(3L, new TicketRequest[] {null}));
    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenNullTicketType_whenPurchase_thenRejectedAndProvidersNotCalled() {
    assertThrows(InvalidBookingException.class,
        () -> cinemaTicketsService.purchaseTickets(3L, new TicketRequest(null, 1)));
    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenAggregatedOverflowTowardsLimit_whenPurchase_thenRejectedWithoutProviderCalls() {
    assertThrows(InvalidBookingException.class,
        () -> cinemaTicketsService.purchaseTickets(3L,
            new TicketRequest(ADULT, Integer.MAX_VALUE),
            new TicketRequest(ADULT, Integer.MAX_VALUE)));
    verify(paymentService, never()).debitAccount(anyLong(), any());
    verify(seatReservationService, never()).reserveSeats(anyLong(), anyLong());
  }

  @Test
  void givenOneAdultAndMultipleChildren_whenPurchase_thenAllowed() {
    final BookingConfirmation confirmation = cinemaTicketsService.purchaseTickets(9L,
        new TicketRequest(ADULT, 1),
        new TicketRequest(CHILD, 4));

    assertEquals(5L, confirmation.seatCount());
    assertEquals(0, new BigDecimal("95.99").compareTo(confirmation.totalCost()));
  }
}
