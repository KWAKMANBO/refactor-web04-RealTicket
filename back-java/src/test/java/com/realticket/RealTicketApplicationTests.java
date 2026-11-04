package com.realticket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Flyway 마이그레이션 + JPA 엔티티 스키마 검증(ddl-auto=validate)이 통과하는지 확인한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RealTicketApplicationTests {

    @Test
    void contextLoads() {
    }
}
