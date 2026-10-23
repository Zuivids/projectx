-- Course catalog: courses, their sections and lessons.

CREATE TABLE course (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(200) NOT NULL,
    slug         VARCHAR(120) NOT NULL,
    description  TEXT         NOT NULL,
    -- Money as integer cents, never floating point (PAY-06)
    price_cents  BIGINT       NOT NULL,
    currency     VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    level        VARCHAR(20)  NULL,
    status       VARCHAR(20)  NOT NULL,
    published_at DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_slug UNIQUE (slug),
    CONSTRAINT ck_course_price CHECK (price_cents >= 0),
    INDEX ix_course_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE course_section (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    course_id BIGINT       NOT NULL,
    title     VARCHAR(200) NOT NULL,
    position  INT          NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_course_section_course (course_id, position),
    CONSTRAINT fk_course_section_course FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE lesson (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    section_id       BIGINT       NOT NULL,
    title            VARCHAR(200) NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    -- Text lessons: the lesson text. Audio lessons: optional notes or transcript.
    content_text     MEDIUMTEXT   NULL,
    -- Reference to the audio file at the media host (backlog D-06); empty until then
    audio_ref        VARCHAR(500) NULL,
    duration_seconds INT          NULL,
    preview          BOOLEAN      NOT NULL DEFAULT FALSE,
    position         INT          NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_lesson_section (section_id, position),
    CONSTRAINT fk_lesson_section FOREIGN KEY (section_id) REFERENCES course_section (id) ON DELETE CASCADE,
    CONSTRAINT ck_lesson_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
