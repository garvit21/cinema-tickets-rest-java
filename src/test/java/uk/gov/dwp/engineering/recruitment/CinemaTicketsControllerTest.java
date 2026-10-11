package uk.gov.dwp.engineering.recruitment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

@SpringBootTest
class CinemaTicketsControllerTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @MockitoBean
  private PaymentService paymentService;

  @MockitoBean
  private SeatReservationService seatReservationService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    clearInvocations(paymentService, seatReservationService);
  }

  @Test
  void givenValidBooking_whenPost_thenCreatedWithConfirmation() throws Exception {
    when(paymentService.debitAccount(anyLong(), any()))
        .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
    when(seatReservationService.reserveSeats(anyLong(), anyLong()))
        .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));

    mockMvc.perform(post("/cinema/bookings")
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "accountId": 42,
                  "ticketRequests": [
                    {"type": "ADULT", "ticketCount": 1},
                    {"type": "CHILD", "ticketCount": 1}
                  ]
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accountId").value(42))
        .andExpect(jsonPath("$.seatCount").value(2))
        .andExpect(jsonPath("$.totalCost").value(43.49));
  }

  @Test
  void givenBusinessRuleFailure_whenPost_thenBadRequestProblemDetail() throws Exception {
    mockMvc.perform(post("/cinema/bookings")
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "accountId": 42,
                  "ticketRequests": [
                    {"type": "CHILD", "ticketCount": 1}
                  ]
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").exists());

    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenFractionalTicketCount_whenPost_thenBadRequestAndProvidersNotCalled() throws Exception {
    mockMvc.perform(post("/cinema/bookings")
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "accountId": 1,
                  "ticketRequests": [
                    {"type": "ADULT", "ticketCount": 1.5}
                  ]
                }
                """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenFractionalAccountId_whenPost_thenBadRequestAndProvidersNotCalled() throws Exception {
    mockMvc.perform(post("/cinema/bookings")
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "accountId": 1.5,
                  "ticketRequests": [
                    {"type": "ADULT", "ticketCount": 1}
                  ]
                }
                """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenUnsupportedMethod_whenGet_thenMethodNotAllowed() throws Exception {
    mockMvc.perform(get("/cinema/bookings"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void givenUnsupportedMediaType_whenPost_thenUnsupportedMediaType() throws Exception {
    mockMvc.perform(post("/cinema/bookings")
            .contentType(TEXT_PLAIN)
            .content("not-json"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void givenMalformedJson_whenPost_thenBadRequestAndProvidersNotCalled() throws Exception {
    mockMvc.perform(post("/cinema/bookings")
            .contentType(APPLICATION_JSON)
            .content("{\"accountId\":1,"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(paymentService, seatReservationService);
  }

  @ParameterizedTest
  @MethodSource("businessValidationRequestBodies")
  void givenBusinessValidationFailure_whenPost_thenProblemDetailAndProvidersNotCalled(
      final String requestBody) throws Exception {
    mockMvc.perform(post("/cinema/bookings")
            .contentType(APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").isNotEmpty());

    verifyNoInteractions(paymentService, seatReservationService);
  }

  static Stream<String> businessValidationRequestBodies() {
    return Stream.of(
        // 26 total tickets
        """
            {
              "accountId": 1,
              "ticketRequests": [{"type": "ADULT", "ticketCount": 26}]
            }
            """,
        // More infants than adults
        """
            {
              "accountId": 1,
              "ticketRequests": [
                {"type": "ADULT", "ticketCount": 1},
                {"type": "INFANT", "ticketCount": 2}
              ]
            }
            """,
        // Missing accountId
        """
            {
              "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]
            }
            """,
        // Null accountId
        """
            {
              "accountId": null,
              "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]
            }
            """,
        // Zero accountId
        """
            {
              "accountId": 0,
              "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]
            }
            """,
        // Negative accountId
        """
            {
              "accountId": -1,
              "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]
            }
            """,
        // Missing ticketRequests
        """
            {
              "accountId": 1
            }
            """,
        // Null ticketRequests
        """
            {
              "accountId": 1,
              "ticketRequests": null
            }
            """,
        // Empty ticketRequests
        """
            {
              "accountId": 1,
              "ticketRequests": []
            }
            """,
        // Null ticketRequests entry
        """
            {
              "accountId": 1,
              "ticketRequests": [null]
            }
            """,
        // Missing ticket type
        """
            {
              "accountId": 1,
              "ticketRequests": [{"ticketCount": 1}]
            }
            """,
        // Null ticket type
        """
            {
              "accountId": 1,
              "ticketRequests": [{"type": null, "ticketCount": 1}]
            }
            """,
        // Negative ticket quantity
        """
            {
              "accountId": 1,
              "ticketRequests": [{"type": "ADULT", "ticketCount": -1}]
            }
            """,
        // Only zero quantities
        """
            {
              "accountId": 1,
              "ticketRequests": [
                {"type": "ADULT", "ticketCount": 0},
                {"type": "CHILD", "ticketCount": 0}
              ]
            }
            """);
  }

  @ParameterizedTest
  @MethodSource("requestDeserializationFailureBodies")
  void givenRequestDeserializationFailure_whenPost_thenBadRequestAndProvidersNotCalled(
      final String requestBody) throws Exception {
    mockMvc.perform(post("/cinema/bookings")
            .contentType(APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(paymentService, seatReservationService);
  }

  static Stream<String> requestDeserializationFailureBodies() {
    return Stream.of(
        // Unknown ticket type
        """
            {
              "accountId": 1,
              "ticketRequests": [{"type": "SENIOR", "ticketCount": 1}]
            }
            """,
        // Missing ticketCount with another valid adult (silent zero would still be valid)
        """
            {
              "accountId": 1,
              "ticketRequests": [
                {"type": "ADULT"},
                {"type": "ADULT", "ticketCount": 1}
              ]
            }
            """,
        // Null ticketCount with another valid adult
        """
            {
              "accountId": 1,
              "ticketRequests": [
                {"type": "ADULT", "ticketCount": null},
                {"type": "ADULT", "ticketCount": 1}
              ]
            }
            """);
  }

  @Test
  void givenTwentyFiveTicketBoundary_whenPost_thenCreatedAndProvidersCalledInOrder()
      throws Exception {
    when(paymentService.debitAccount(anyLong(), any()))
        .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
    when(seatReservationService.reserveSeats(anyLong(), anyLong()))
        .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));

    mockMvc.perform(post("/cinema/bookings")
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "accountId": 1234,
                  "ticketRequests": [
                    {"type": "ADULT", "ticketCount": 1},
                    {"type": "CHILD", "ticketCount": 23},
                    {"type": "INFANT", "ticketCount": 1}
                  ]
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accountId").value(1234))
        .andExpect(jsonPath("$.seatCount").value(24))
        .andExpect(jsonPath("$.totalCost").value(428.49));

    final InOrder inOrder = inOrder(paymentService, seatReservationService);
    inOrder.verify(paymentService).debitAccount(eq(1234L), eq(new BigDecimal("428.49")));
    inOrder.verify(seatReservationService).reserveSeats(eq(1234L), eq(24L));
    verifyNoMoreInteractions(paymentService, seatReservationService);
  }
}
