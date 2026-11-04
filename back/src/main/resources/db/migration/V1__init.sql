CREATE TABLE users
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    login_id       VARCHAR(255) NOT NULL,
    login_password VARCHAR(255) NULL,
    role           VARCHAR(10)  NOT NULL,
    is_guest       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_login_id UNIQUE (login_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE places
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    overview_svg    VARCHAR(255) NULL,
    overview_height INT          NOT NULL,
    overview_width  INT          NOT NULL,
    -- 구역 순서(sectionIndex -> section id). 좌석 비트맵/예약 좌석의 sectionIndex 기준이 된다.
    sections        JSON         NOT NULL,
    overview_points TEXT         NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE sections
(
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    name     VARCHAR(255) NOT NULL,
    col_len  INT          NOT NULL,
    -- 좌석 존재 여부 배열 (true: 좌석 있음, false: 통로 등 빈 칸)
    seats    JSON         NOT NULL,
    place_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sections_place FOREIGN KEY (place_id) REFERENCES places (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE programs
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(255) NOT NULL,
    profile_url  VARCHAR(255) NOT NULL,
    running_time INT          NOT NULL,
    genre        VARCHAR(255) NOT NULL,
    actors       VARCHAR(255) NOT NULL,
    price        INT          NOT NULL,
    place_id     BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_programs_place FOREIGN KEY (place_id) REFERENCES places (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE events
(
    id                     BIGINT   NOT NULL AUTO_INCREMENT,
    running_date           DATETIME NOT NULL,
    reservation_open_date  DATETIME NOT NULL,
    reservation_close_date DATETIME NOT NULL,
    program_id             BIGINT   NOT NULL,
    place_id               BIGINT   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_events_program FOREIGN KEY (program_id) REFERENCES programs (id),
    CONSTRAINT fk_events_place FOREIGN KEY (place_id) REFERENCES places (id),
    INDEX idx_events_reservation_close_date (reservation_close_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE reservations
(
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    created_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    amount     INT      NOT NULL,
    program_id BIGINT   NOT NULL,
    event_id   BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reservations_program FOREIGN KEY (program_id) REFERENCES programs (id),
    CONSTRAINT fk_reservations_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE reserved_seats
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    deleted_at     DATETIME     NULL,
    section_name   VARCHAR(255) NOT NULL,
    section_index  INT          NOT NULL,
    col_len        INT          NOT NULL,
    `row`          INT          NOT NULL,
    col            INT          NOT NULL,
    reservation_id BIGINT       NOT NULL,
    event_id       BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reserved_seats_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT fk_reserved_seats_event FOREIGN KEY (event_id) REFERENCES events (id),
    INDEX idx_reserved_seats_event_seat (event_id, section_index, `row`, col)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
