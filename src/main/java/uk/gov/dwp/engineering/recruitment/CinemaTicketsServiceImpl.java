package uk.gov.dwp.engineering.recruitment;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

@Service
public class CinemaTicketsServiceImpl implements CinemaTicketsService {

  private static final BigDecimal ADULT_PRICE = new BigDecimal("25.99");
  private static final BigDecimal CHILD_PRICE = new BigDecimal("17.50");
  private static final BigDecimal INFANT_PRICE = new BigDecimal("0.00");
  private static final long MAX_TICKETS = 25L;

  private final PaymentService paymentService;

  private final SeatReservationService seatReservationService;

  public CinemaTicketsServiceImpl(PaymentService paymentService,
      SeatReservationService seatReservationService) {
    this.paymentService = paymentService;
    this.seatReservationService = seatReservationService;
  }

  @Override
  public BookingConfirmation purchaseTickets(final Long accountId,
      final TicketRequest... ticketRequests)
      throws InvalidBookingException {

    if (accountId == null || accountId <= 0L) {
      throw new InvalidBookingException("Account ID must be a positive number.");
    }

    if (ticketRequests == null || ticketRequests.length == 0) {
      throw new InvalidBookingException("At least one ticket request is required.");
    }

    long adultCount = 0L;
    long childCount = 0L;
    long infantCount = 0L;

    for (final TicketRequest ticketRequest : ticketRequests) {
      if (ticketRequest == null) {
        throw new InvalidBookingException("Ticket request entries must not be null.");
      }
      if (ticketRequest.type() == null) {
        throw new InvalidBookingException("Ticket type must be provided.");
      }
      if (ticketRequest.ticketCount() < 0) {
        throw new InvalidBookingException("Ticket quantities must not be negative.");
      }

      final long quantity = ticketRequest.ticketCount();
      switch (ticketRequest.type()) {
        case ADULT -> adultCount = addExact(adultCount, quantity);
        case CHILD -> childCount = addExact(childCount, quantity);
        case INFANT -> infantCount = addExact(infantCount, quantity);
      }
    }

    final long totalTickets = addExact(addExact(adultCount, childCount), infantCount);
    if (totalTickets == 0L) {
      throw new InvalidBookingException("A booking must contain at least one ticket.");
    }
    if (totalTickets > MAX_TICKETS) {
      throw new InvalidBookingException(
          "A maximum of " + MAX_TICKETS + " tickets can be purchased at a time.");
    }
    if (adultCount < 1L) {
      throw new InvalidBookingException(
          "At least one adult ticket is required for the booking.");
    }
    if (infantCount > adultCount) {
      final long additionalAdultsNeeded = infantCount - adultCount;
      throw new InvalidBookingException(
          additionalAdultsNeeded + " additional adult ticket(s) are required because each infant "
              + "requires an adult. Alternatively, reduce the number of infant tickets. "
              + "The purchase must also stay within the maximum of " + MAX_TICKETS + " tickets.");
    }

    final BigDecimal totalCost = ADULT_PRICE.multiply(BigDecimal.valueOf(adultCount))
        .add(CHILD_PRICE.multiply(BigDecimal.valueOf(childCount)))
        .add(INFANT_PRICE.multiply(BigDecimal.valueOf(infantCount)));
    final long seatCount = adultCount + childCount;

    paymentService.debitAccount(accountId, totalCost);
    seatReservationService.reserveSeats(accountId, seatCount);

    return new BookingConfirmation(accountId, seatCount, totalCost);
  }

  private static long addExact(final long left, final long right) {
    try {
      return Math.addExact(left, right);
    } catch (final ArithmeticException ex) {
      throw new InvalidBookingException(
          "A maximum of " + MAX_TICKETS + " tickets can be purchased at a time.");
    }
  }
}
