package com.paytm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class SeatBurstTest {

    private static final String BASE_URL =
            "http://localhost:8080";

    /*
     * IMPORTANT:
     * Use a FRESH show for every hot-seat test.
     */
    private static final String SHOW_ID =
            "PUT_FRESH_SHOW_ID_HERE";

    private static final String SEAT =
            "A1";

    private static final String USER1_TOKEN =
            "PUT_FRESH_USER1_TOKEN_HERE";

    private static final String USER2_TOKEN =
            "PUT_FRESH_USER2_TOKEN_HERE";

    private static final int TOTAL_REQUESTS = 2000;

    private static final int CONCURRENCY = 200;

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    public static void main(String[] args) throws Exception {

        System.out.println();
        System.out.println("==================================================");
        System.out.println("        SEAT RESERVATION HOT-SEAT BURST");
        System.out.println("==================================================");

        System.out.println("Base URL       : " + BASE_URL);
        System.out.println("Show ID        : " + SHOW_ID);
        System.out.println("Seat           : " + SEAT);
        System.out.println("Requests       : " + TOTAL_REQUESTS);
        System.out.println("Concurrency    : " + CONCURRENCY);
        System.out.println();

        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger unauthorized = new AtomicInteger();
        AtomicInteger forbidden = new AtomicInteger();
        AtomicInteger badRequest = new AtomicInteger();
        AtomicInteger serverError = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        AtomicInteger networkErrors = new AtomicInteger();

        ExecutorService executor =
                Executors.newFixedThreadPool(CONCURRENCY);

        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {

            final int requestNumber = i;

            tasks.add(() -> {

                try {

                    String token =
                            requestNumber % 2 == 0
                                    ? USER1_TOKEN
                                    : USER2_TOKEN;

                    HttpResponse<String> response =
                            reserve(requestNumber, token);

                    int status = response.statusCode();

                    if (status == 201) {
                        created.incrementAndGet();

                    } else if (status == 409) {
                        conflict.incrementAndGet();

                    } else if (status == 401) {
                        unauthorized.incrementAndGet();

                    } else if (status == 403) {
                        forbidden.incrementAndGet();

                    } else if (status == 400) {
                        badRequest.incrementAndGet();

                    } else if (status >= 500) {
                        serverError.incrementAndGet();

                    } else {
                        other.incrementAndGet();
                    }

                } catch (Exception ex) {

                    networkErrors.incrementAndGet();

                }

                return null;
            });
        }

        long start = System.currentTimeMillis();

        List<Future<Void>> futures =
                executor.invokeAll(tasks);

        long end = System.currentTimeMillis();

        executor.shutdown();

        int completed = futures.size();

        double seconds =
                (end - start) / 1000.0;

        double throughput =
                completed / seconds;

        System.out.println();
        System.out.println("RESULT");
        System.out.println("--------------------------------------------------");

        System.out.println(
                "201 Created          : "
                        + created.get()
        );

        System.out.println(
                "409 Conflict         : "
                        + conflict.get()
        );

        System.out.println(
                "400 Bad Request      : "
                        + badRequest.get()
        );

        System.out.println(
                "401 Unauthorized     : "
                        + unauthorized.get()
        );

        System.out.println(
                "403 Forbidden        : "
                        + forbidden.get()
        );

        System.out.println(
                "5xx Server Error     : "
                        + serverError.get()
        );

        System.out.println(
                "Other                : "
                        + other.get()
        );

        System.out.println(
                "Network Errors       : "
                        + networkErrors.get()
        );

        System.out.println();
        System.out.println(
                "Total Requests       : "
                        + completed
        );

        System.out.printf(
                "Time                 : %.2f sec%n",
                seconds
        );

        System.out.printf(
                "Throughput           : %.2f requests/sec%n",
                throughput
        );

        System.out.println();

        validateResults(
                created.get(),
                serverError.get(),
                networkErrors.get(),
                unauthorized.get(),
                forbidden.get(),
                completed
        );

        showFinalState();

    }

    private static HttpResponse<String> reserve(
            int requestNumber,
            String token
    ) throws Exception {

        String requestBody =
                """
                {
                  "seats": ["%s"]
                }
                """.formatted(SEAT);

        String idempotencyKey =
                "burst-"
                        + requestNumber
                        + "-"
                        + UUID.randomUUID();

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        BASE_URL
                                                + "/shows/"
                                                + SHOW_ID
                                                + "/reserve"
                                )
                        )
                        .timeout(Duration.ofSeconds(30))
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .header(
                                "Idempotency-Key",
                                idempotencyKey
                        )
                        .header(
                                "X-Correlation-Id",
                                idempotencyKey
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(requestBody)
                        )
                        .build();

        return HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static void validateResults(
            int created,
            int serverError,
            int networkErrors,
            int unauthorized,
            int forbidden,
            int total
    ) {

        System.out.println("VALIDATION");
        System.out.println("--------------------------------------------------");

        boolean passed = true;

        if (created != 1) {

            System.out.println(
                    "FAIL: Expected exactly 1 successful reservation"
            );

            passed = false;
        } else {

            System.out.println(
                    "PASS: Exactly one request reserved the seat"
            );
        }

        if (serverError > 0) {

            System.out.println(
                    "FAIL: 5xx errors = "
                            + serverError
            );

            passed = false;

        } else {

            System.out.println(
                    "PASS: No 5xx errors"
            );
        }

        if (networkErrors > 0) {

            System.out.println(
                    "FAIL: Network errors = "
                            + networkErrors
            );

            passed = false;

        } else {

            System.out.println(
                    "PASS: No network errors"
            );
        }

        if (unauthorized > 0) {

            System.out.println(
                    "WARNING: 401 responses = "
                            + unauthorized
                            + " - check JWT expiry"
            );

            passed = false;
        }

        if (forbidden > 0) {

            System.out.println(
                    "WARNING: 403 responses = "
                            + forbidden
            );

            passed = false;
        }

        if (total != TOTAL_REQUESTS) {

            System.out.println(
                    "FAIL: Completed request count mismatch"
            );

            passed = false;
        }

        System.out.println();

        if (passed) {

            System.out.println(
                    "=============================================="
            );

            System.out.println(
                    "             TEST PASSED"
            );

            System.out.println(
                    "=============================================="
            );

        } else {

            System.out.println(
                    "=============================================="
            );

            System.out.println(
                    "             TEST FAILED"
            );

            System.out.println(
                    "=============================================="
            );
        }
    }

    private static void showFinalState()
            throws Exception {

        System.out.println();
        System.out.println("FINAL RECONCILIATION");
        System.out.println("--------------------------------------------------");

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        BASE_URL
                                                + "/shows/"
                                                + SHOW_ID
                                )
                        )
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

        HttpResponse<String> response =
                HTTP_CLIENT.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        System.out.println(
                "GET /shows/" + SHOW_ID
        );

        System.out.println(
                "HTTP Status: "
                        + response.statusCode()
        );

        System.out.println();

        System.out.println(response.body());

        System.out.println();
        System.out.println(
                "Check that:"
        );

        System.out.println(
                "available + held + confirmed = totalSeats"
        );
    }
}