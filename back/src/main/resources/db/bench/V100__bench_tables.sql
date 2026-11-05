-- 성능 비교용 테이블 (bench 프로필에서만 적용된다)

-- [비교 1] 좌석 1개 = 1행 구조 (반정규화 이전 구조 재현)
-- 운영 구조는 sections.seats JSON 배열에 구역의 좌석을 한 번에 담는다.
-- 공정한 비교를 위해 조회 조건(section_id)에 인덱스를 둔다.
CREATE TABLE bench_seats
(
    id         BIGINT  NOT NULL AUTO_INCREMENT,
    section_id BIGINT  NOT NULL,
    seat_index INT     NOT NULL,
    row_no     INT     NOT NULL,
    col_no     INT     NOT NULL,
    is_seat    BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bench_seats_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT uk_bench_seats_section_seat UNIQUE (section_id, seat_index)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- [비교 2] 예매 중 좌석 상태를 DB 에 두는 구조
-- 운영 구조는 Redis 비트맵(event:{e}:section:{i}:seats)에 둔다.
CREATE TABLE bench_event_seat_status
(
    event_id      BIGINT      NOT NULL,
    section_index INT         NOT NULL,
    seat_index    INT         NOT NULL,
    reserved      BOOLEAN     NOT NULL DEFAULT FALSE,
    reserved_by   VARCHAR(64) NULL,
    PRIMARY KEY (event_id, section_index, seat_index)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
