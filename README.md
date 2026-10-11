# Cinema Tickets Code Test

## Objective

To assess your ability to build a simple API.

The scenario and requirements will be provided separately.

## Implementation approach

The controller accepts booking requests and delegates business logic to CinemaTicketsServiceImpl.

The service validates inputs, aggregates quantities by ticket type and checks the complete booking before calling either provider. It calculates the price using BigDecimal and reserves seats for adults and children only. Payment is requested first, followed by seat reservation.

The supplied CinemaTicketsService interface and protected domain classes are unchanged. BookingConfirmation includes seatCount and totalCost while retaining its original one-argument constructor and accountId accessor.

## Business rules

This service implements cinema ticket purchases with the following behaviour:

| Ticket type | Price (GBP) | Seat reserved |
|-------------|-------------|----------------|
| ADULT       | 25.99       | Yes            |
| CHILD       | 17.50       | Yes            |
| INFANT      | 0.00        | No             |

- Money is calculated with `BigDecimal` (no floating-point arithmetic).
- Total tickets = adults + children + infants. A purchase may contain at most **25** tickets.
- Infant tickets count towards the 25-ticket maximum but do not receive a seat.
- Account ID must be present and greater than zero.
- At least one adult ticket is required.
- At most one infant per adult (documented interpretation of the lap rule). Reducing infants is an alternative when the ratio is exceeded; the 25-ticket cap still applies.
- One adult may accompany multiple children, within the 25-ticket limit.
- Repeated ticket-type entries are allowed and are aggregated across the complete purchase.
- Explicit zero quantities are permitted; negative quantities are rejected.
- All validation completes before payment or seat reservation.
- For a valid booking, payment is taken once with the exact total cost, then seats are reserved once with the exact seat count. Third-party providers are assumed to succeed.
- There is no idempotency mechanism: repeated valid requests are processed as separate purchases.
- Fractional JSON numbers for integer fields such as `ticketCount` and `accountId` are rejected (`spring.jackson.deserialization.accept-float-as-int=false`).

## Input validation and assumptions

Invalid requests include missing, null or non-positive account IDs;
missing, null or empty ticket collections; null ticket entries;
missing or unknown ticket types; missing, null or negative quantities;
and bookings containing only zero quantities.

Bookings exceeding 25 total tickets, containing no adults, or containing more infants than adults are rejected before either provider is called.

The one-infant-per-adult rule interprets the requirement that each infant sits on an adult's lap. No additional adult-to-child ratio is imposed.
Repeated ticket types are aggregated, and zero quantities are accepted when the remaining booking is valid.

Fractional numeric values for account IDs and quantities are rejected.
Numeric strings remain accepted where the framework can convert them
to the required integer type.

The supplied payment and reservation implementations are stubs returning successful responses. Running this project does not debit real accounts or reserve real cinema seats. External integration and provider failure handling are outside the exercise scope.

## API

`POST /cinema/bookings` with `Content-Type: application/json` returns **201** and a confirmation containing `accountId`, `seatCount`, and `totalCost`.

Business-rule failures return **400** with a Problem Detail body. Malformed JSON and other framework-level request errors also return **400**. Unsupported methods return **405**; unsupported media types return **415**.

### Request example

```json
{
  "accountId": 1234,
  "ticketRequests": [
    { "type": "ADULT", "ticketCount": 2 },
    { "type": "CHILD", "ticketCount": 1 },
    { "type": "INFANT", "ticketCount": 1 }
  ]
}
```

### Success response example (`201`)

```json
{
  "accountId": 1234,
  "seatCount": 3,
  "totalCost": 69.48
}
```

OpenAPI describing this contract is in `src/main/resources/cinema-tickets.yaml`. Request property names match the Java model (`ticketRequests`, `ticketCount`).

## Build, test and run (Java 21)

Requires **JDK 21** and **Apache Maven 3.9+** with `java` and `mvn` on your `PATH`
(there is no Maven Wrapper in this repository). Confirm the versions, then build and run:

```bash
java -version
mvn -version
mvn clean verify
mvn spring-boot:run
```

Representative call (expected **201** with `seatCount` 1 and `totalCost` 25.99):

```bash
curl -i -X POST http://localhost:8080/cinema/bookings \
  -H 'Content-Type: application/json' \
  -d '{"accountId":1234,"ticketRequests":[{"type":"ADULT","ticketCount":1}]}'
```

## Testing

Run the complete automated suite:

    mvn clean verify

Service tests cover pricing, aggregation, ticket limits, accompanying-adult rules, invalid inputs and provider interactions.

Controller tests exercise the Spring application context with mocked providers. They verify successful responses, the 25-ticket boundary, business-validation errors, malformed requests, fractional inputs, unsupported methods and unsupported media types. Rejected booking cases verify that neither provider is called.

The successful HTTP boundary test verifies the exact payment amount, seat count and provider call order, with no additional provider calls.

Manual HTTP smoke tests were also performed on a separate laptop to check booking totals and representative invalid requests.