package uk.gov.dwp.engineering.recruitment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
