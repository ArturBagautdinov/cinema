CREATE TABLE user_role
(
    role_id   BIGSERIAL PRIMARY KEY,
    role_name TEXT NOT NULL UNIQUE
);

CREATE TABLE subscription_status
(
    subscr_status_id BIGSERIAL PRIMARY KEY,
    status_name      TEXT NOT NULL UNIQUE
);

CREATE TABLE rental_status
(
    rental_status_id BIGSERIAL PRIMARY KEY,
    status_name      TEXT NOT NULL UNIQUE
);

CREATE TABLE payment_method
(
    method_id   BIGSERIAL PRIMARY KEY,
    method_name TEXT NOT NULL UNIQUE
);

CREATE TABLE subscription_plan
(
    plan_id   BIGSERIAL PRIMARY KEY,
    plan_type TEXT           NOT NULL UNIQUE,
    price     NUMERIC(10, 2) NOT NULL CHECK (price >= 0)
);

CREATE TABLE age_rating
(
    age_rating_id BIGSERIAL PRIMARY KEY,
    age_rating    TEXT NOT NULL UNIQUE
);

CREATE TABLE country
(
    country_id   BIGSERIAL PRIMARY KEY,
    country_name TEXT NOT NULL UNIQUE
);

CREATE TABLE language
(
    language_id   BIGSERIAL PRIMARY KEY,
    language_name TEXT NOT NULL UNIQUE
);

CREATE TABLE genre
(
    genre_id    BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE users
(
    user_id           BIGSERIAL PRIMARY KEY,
    name              TEXT        NOT NULL,
    role_id           BIGINT      NOT NULL REFERENCES user_role (role_id),
    email             TEXT        NOT NULL UNIQUE,
    registration_date TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE director
(
    director_id BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    birth_date  DATE,
    country_id  BIGINT REFERENCES country (country_id),
    biography   TEXT
);

CREATE TABLE actor
(
    actor_id   BIGSERIAL PRIMARY KEY,
    name       TEXT NOT NULL,
    birth_date DATE,
    country_id BIGINT REFERENCES country (country_id),
    biography  TEXT
);

CREATE TABLE movie
(
    movie_id      BIGSERIAL PRIMARY KEY,
    title         TEXT NOT NULL,
    description   TEXT,
    release_year  INT CHECK (release_year BETWEEN 1888 AND 2100),
    duration      INT  NOT NULL CHECK (duration > 0),
    age_rating_id BIGINT REFERENCES age_rating (age_rating_id),
    language_id   BIGINT REFERENCES language (language_id),
    country_id    BIGINT REFERENCES country (country_id),
    director_id   BIGINT REFERENCES director (director_id)
);

CREATE TABLE movie_genre
(
    movie_id BIGINT NOT NULL REFERENCES movie (movie_id) ON DELETE CASCADE,
    genre_id BIGINT NOT NULL REFERENCES genre (genre_id) ON DELETE RESTRICT,
    PRIMARY KEY (movie_id, genre_id)
);

CREATE TABLE movie_actor
(
    movie_id       BIGINT NOT NULL REFERENCES movie (movie_id) ON DELETE CASCADE,
    actor_id       BIGINT NOT NULL REFERENCES actor (actor_id) ON DELETE RESTRICT,
    character_name TEXT,
    PRIMARY KEY (movie_id, actor_id)
);

CREATE TABLE subscription
(
    subscription_id  BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    plan_id          BIGINT NOT NULL REFERENCES subscription_plan (plan_id),
    start_date       DATE   NOT NULL,
    end_date         DATE,
    subscr_status_id BIGINT NOT NULL REFERENCES subscription_status (subscr_status_id),
    CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE UNIQUE INDEX ux_subscription_active
    ON subscription (user_id) WHERE (end_date IS NULL);

CREATE TABLE rental
(
    rental_id        BIGSERIAL PRIMARY KEY,
    user_id          BIGINT         NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    movie_id         BIGINT         NOT NULL REFERENCES movie (movie_id) ON DELETE RESTRICT,
    rental_date      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    return_date      TIMESTAMPTZ,
    price            NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    rental_status_id BIGINT         NOT NULL REFERENCES rental_status (rental_status_id)
);

CREATE TABLE purchase
(
    purchase_id   BIGSERIAL PRIMARY KEY,
    user_id       BIGINT         NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    movie_id      BIGINT         NOT NULL REFERENCES movie (movie_id) ON DELETE RESTRICT,
    purchase_date TIMESTAMPTZ    NOT NULL DEFAULT now(),
    price         NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    method_id     BIGINT         NOT NULL REFERENCES payment_method (method_id)
);

CREATE TABLE viewing
(
    viewing_id   BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    movie_id     BIGINT      NOT NULL REFERENCES movie (movie_id) ON DELETE RESTRICT,
    viewing_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    progress     INT         NOT NULL CHECK (progress BETWEEN 0 AND 100),
    device       TEXT
);

CREATE TABLE review
(
    review_id   BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    movie_id    BIGINT      NOT NULL REFERENCES movie (movie_id) ON DELETE CASCADE,
    rating      INT         NOT NULL CHECK (rating BETWEEN 1 AND 10),
    comment     TEXT,
    review_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, movie_id)
);

CREATE TABLE family_group
(
    family_group_id BIGSERIAL PRIMARY KEY,
    group_name      TEXT        NOT NULL,
    owner_id        BIGINT      NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    created_date    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE family_member
(
    family_member_id BIGSERIAL PRIMARY KEY,
    family_group_id  BIGINT NOT NULL REFERENCES family_group (family_group_id) ON DELETE CASCADE,
    user_id          BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    role             TEXT   NOT NULL,
    UNIQUE (family_group_id, user_id)
);