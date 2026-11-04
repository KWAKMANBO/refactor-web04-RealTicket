package com.realticket.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.realticket.TestcontainersConfiguration;
import com.realticket.domain.booking.repository.SeatRepository;
import com.realticket.domain.booking.service.Seat;
import com.realticket.domain.event.entity.Event;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.auth.UserStatus;
import com.realticket.support.BookingTestHelper;
import com.realticket.support.TestFixtures;

/**
 * 실제 HTTP 서버로 좌석 SSE 를 연결했다가 끊어, 서버가 연결 종료를 감지하고 선점 좌석을 풀어 주는지 확인한다.
 * heartbeat 주기를 짧게 줄여 끊긴 연결을 빨리 감지하게 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.booking.sse-heartbeat-interval=200ms")
@Import({TestcontainersConfiguration.class, TestFixtures.class, BookingTestHelper.class})
class SeatsSseConnectionTest {

    @LocalServerPort
    int port;

    @Autowired
    BookingTestHelper booking;

    @Autowired
    SeatRepository seatRepository;

    @Test
    void 좌석_SSE_연결을_끊으면_선점_좌석이_풀리고_LOGIN으로_돌아간다() throws Exception {
        Event event = booking.openedEvent(10);
        SessionUser user = booking.requestAdmission(event.getId());
        booking.setBookingAmount(user, 1);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/booking/seat/" + event.getId()))
                .header("Cookie", "SID=" + user.sid())
                .header("Accept", "text/event-stream")
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/event-stream"));

        try (InputStream body = response.body()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
            String firstData = readFirstDataLine(reader);
            assertThat(firstData).contains("\"seatStatus\":[[true,true,true,false],[true,true,true,false]]");

            // 연결된 상태에서 좌석 선점
            assertThat(booking.refresh(user).status()).isEqualTo(UserStatus.SELECTING_SEAT);
            booking.reserve(booking.refresh(user), event.getId(), new Seat(0, 0));
            assertThat(seatRepository.findAll(event.getId())[0][0]).isFalse();
        }
        // 브라우저 탭을 닫은 것처럼 연결을 끊는다
        client.close();

        await(() -> seatRepository.findAll(event.getId())[0][0]);
        await(() -> booking.refresh(user).status() == UserStatus.LOGIN);
    }

    private static String readFirstDataLine(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data:")) {
                return line;
            }
        }
        throw new AssertionError("SSE 데이터를 받지 못했습니다.");
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("10초 안에 조건이 충족되지 않았습니다.");
    }
}
