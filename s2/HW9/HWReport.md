## 1. Выбрать 2-3 аналитических вопроса по своему проекту.

### Вопрос 1. Какая динамика активности пользователей по дням?

Бизнес-вопрос:

Сколько просмотров, покупок, аренд и отзывов происходит каждый день?

### Вопрос 2. Какие фильмы самые популярные?

Бизнес-вопрос:

Какие фильмы чаще всего смотрят, покупают, арендуют и оценивают?

### Вопрос 3. Сколько действий совершают пользователи?

Бизнес-вопрос:

Какие пользователи самые активные и сколько действий они совершили за период?

## 2) Главный факт

### fact_user_actions

У меня в проекте не один универсальный “заказ”, как в интернет-магазине, есть разные пользовательские действия:

просмотр фильма;

покупка фильма;

аренда фильма;

отзыв.

Поэтому главный аналитический факт можно сделать как событийную таблицу активности пользователей.

fact_user_actions будет объединять данные из OLTP-таблиц:

viewing

purchase

rental

review

## 3) Зерно факта

### 1 строка = одно действие пользователя с фильмом

Одна строка в olap.fact_user_actions — это одно событие из OLTP:

один просмотр

или одна покупка

или одна аренда

или один отзыв

## 4) Создать 2-4 измерения.

OLAP-модель:

olap.dim_date (Измерение даты)

olap.dim_user (Измерение пользователя)

olap.dim_movie (Измерение фильма)

olap.dim_action_type (Измерение типа действия)

olap.fact_user_actions (Главный факт)


OLAP-схема:

```sql
DROP SCHEMA IF EXISTS olap CASCADE;
CREATE SCHEMA olap;

-- 1. Измерение даты
CREATE TABLE olap.dim_date
(
    date_id    INT PRIMARY KEY, -- формат YYYYMMDD
    date_value DATE NOT NULL UNIQUE,
    year       INT NOT NULL,
    quarter    INT NOT NULL,
    month      INT NOT NULL,
    month_name TEXT NOT NULL,
    day        INT NOT NULL,
    day_of_week INT NOT NULL,
    is_weekend BOOLEAN NOT NULL
);

-- 2. Измерение пользователя
CREATE TABLE olap.dim_user
(
    user_key          BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL UNIQUE,
    user_name         TEXT NOT NULL,
    email             TEXT NOT NULL,
    role_name         TEXT NOT NULL,
    registration_date DATE NOT NULL
);

-- 3. Измерение фильма
CREATE TABLE olap.dim_movie
(
    movie_key      BIGSERIAL PRIMARY KEY,
    movie_id       BIGINT NOT NULL UNIQUE,
    title          TEXT NOT NULL,
    release_year   INT,
    duration       INT NOT NULL,
    age_rating     TEXT,
    language_name  TEXT,
    country_name   TEXT,
    director_name  TEXT,
    genres         TEXT
);

-- 4. Измерение типа действия
CREATE TABLE olap.dim_action_type
(
    action_type_key BIGSERIAL PRIMARY KEY,
    action_type     TEXT NOT NULL UNIQUE,
    description     TEXT
);

-- 5. Главный факт
CREATE TABLE olap.fact_user_actions
(
    action_key       BIGSERIAL PRIMARY KEY,

    date_id          INT NOT NULL REFERENCES olap.dim_date(date_id),
    user_key         BIGINT NOT NULL REFERENCES olap.dim_user(user_key),
    movie_key        BIGINT NOT NULL REFERENCES olap.dim_movie(movie_key),
    action_type_key  BIGINT NOT NULL REFERENCES olap.dim_action_type(action_type_key),

    source_table     TEXT NOT NULL,
    source_id        BIGINT NOT NULL,

    action_timestamp TIMESTAMPTZ NOT NULL,

    action_count     INT NOT NULL DEFAULT 1,

    revenue          NUMERIC(10, 2),
    progress         INT,
    rating           INT,
    rental_days      NUMERIC(10, 2),

    device           TEXT,
    payment_method   TEXT,
    rental_status    TEXT,

    UNIQUE (source_table, source_id)
);
```

## 5. Заполнить OLAP-таблицы из своих OLTP-таблиц

Заполнение dim_action_type:

```sql
INSERT INTO olap.dim_action_type(action_type, description)
VALUES
    ('VIEWING', 'Просмотр фильма пользователем'),
    ('PURCHASE', 'Покупка фильма пользователем'),
    ('RENTAL', 'Аренда фильма пользователем'),
    ('REVIEW', 'Отзыв пользователя на фильм');
```


Заполнение dim_date

Даты берем из всех таблиц событий:

```sql
INSERT INTO olap.dim_date
(
    date_id,
    date_value,
    year,
    quarter,
    month,
    month_name,
    day,
    day_of_week,
    is_weekend
)
SELECT DISTINCT
    TO_CHAR(action_date::DATE, 'YYYYMMDD')::INT AS date_id,
    action_date::DATE AS date_value,
    EXTRACT(YEAR FROM action_date)::INT AS year,
    EXTRACT(QUARTER FROM action_date)::INT AS quarter,
    EXTRACT(MONTH FROM action_date)::INT AS month,
    TO_CHAR(action_date, 'Month') AS month_name,
    EXTRACT(DAY FROM action_date)::INT AS day,
    EXTRACT(ISODOW FROM action_date)::INT AS day_of_week,
    CASE 
        WHEN EXTRACT(ISODOW FROM action_date)::INT IN (6, 7) THEN TRUE
        ELSE FALSE
    END AS is_weekend
FROM
(
    SELECT viewing_date AS action_date FROM viewing
    UNION
    SELECT purchase_date AS action_date FROM purchase
    UNION
    SELECT rental_date AS action_date FROM rental
    UNION
    SELECT review_date AS action_date FROM review
) s;
```

Заполнение dim_user:

```sql
INSERT INTO olap.dim_user
(
    user_id,
    user_name,
    email,
    role_name,
    registration_date
)
SELECT
    u.user_id,
    u.name AS user_name,
    u.email,
    ur.role_name,
    u.registration_date::DATE
FROM users u
JOIN user_role ur 
    ON u.role_id = ur.role_id;
```

Заполнение dim_movie 

Так как у фильма может быть несколько жанров, в OLAP проще собрать их в одну строку через string_agg.

```sql
INSERT INTO olap.dim_movie
(
    movie_id,
    title,
    release_year,
    duration,
    age_rating,
    language_name,
    country_name,
    director_name,
    genres
)
SELECT
    m.movie_id,
    m.title,
    m.release_year,
    m.duration,
    ar.age_rating,
    l.language_name,
    c.country_name,
    d.name AS director_name,
    STRING_AGG(DISTINCT g.name, ', ' ORDER BY g.name) AS genres
FROM movie m
LEFT JOIN age_rating ar 
    ON m.age_rating_id = ar.age_rating_id
LEFT JOIN language l 
    ON m.language_id = l.language_id
LEFT JOIN country c 
    ON m.country_id = c.country_id
LEFT JOIN director d 
    ON m.director_id = d.director_id
LEFT JOIN movie_genre mg 
    ON m.movie_id = mg.movie_id
LEFT JOIN genre g 
    ON mg.genre_id = g.genre_id
GROUP BY
    m.movie_id,
    m.title,
    m.release_year,
    m.duration,
    ar.age_rating,
    l.language_name,
    c.country_name,
    d.name;
```

Заполнение fact_user_actions

Факт заполняется через UNION ALL из четырех OLTP-таблиц.

```sql
INSERT INTO olap.fact_user_actions
(
    date_id,
    user_key,
    movie_key,
    action_type_key,

    source_table,
    source_id,

    action_timestamp,
    action_count,

    revenue,
    progress,
    rating,
    rental_days,

    device,
    payment_method,
    rental_status
)

-- 1. Просмотры
SELECT
    TO_CHAR(v.viewing_date::DATE, 'YYYYMMDD')::INT AS date_id,
    du.user_key,
    dm.movie_key,
    dat.action_type_key,

    'viewing' AS source_table,
    v.viewing_id AS source_id,

    v.viewing_date AS action_timestamp,
    1 AS action_count,

    NULL::NUMERIC(10, 2) AS revenue,
    v.progress AS progress,
    NULL::INT AS rating,
    NULL::NUMERIC(10, 2) AS rental_days,

    v.device,
    NULL::TEXT AS payment_method,
    NULL::TEXT AS rental_status
FROM viewing v
JOIN olap.dim_user du 
    ON v.user_id = du.user_id
JOIN olap.dim_movie dm 
    ON v.movie_id = dm.movie_id
JOIN olap.dim_action_type dat 
    ON dat.action_type = 'VIEWING'

UNION ALL

-- 2. Покупки
SELECT
    TO_CHAR(p.purchase_date::DATE, 'YYYYMMDD')::INT AS date_id,
    du.user_key,
    dm.movie_key,
    dat.action_type_key,

    'purchase' AS source_table,
    p.purchase_id AS source_id,

    p.purchase_date AS action_timestamp,
    1 AS action_count,

    p.price AS revenue,
    NULL::INT AS progress,
    NULL::INT AS rating,
    NULL::NUMERIC(10, 2) AS rental_days,

    NULL::TEXT AS device,
    pm.method_name AS payment_method,
    NULL::TEXT AS rental_status
FROM purchase p
JOIN olap.dim_user du 
    ON p.user_id = du.user_id
JOIN olap.dim_movie dm 
    ON p.movie_id = dm.movie_id
JOIN payment_method pm 
    ON p.method_id = pm.method_id
JOIN olap.dim_action_type dat 
    ON dat.action_type = 'PURCHASE'

UNION ALL

-- 3. Аренды
SELECT
    TO_CHAR(r.rental_date::DATE, 'YYYYMMDD')::INT AS date_id,
    du.user_key,
    dm.movie_key,
    dat.action_type_key,

    'rental' AS source_table,
    r.rental_id AS source_id,

    r.rental_date AS action_timestamp,
    1 AS action_count,

    r.price AS revenue,
    NULL::INT AS progress,
    NULL::INT AS rating,
    CASE
        WHEN r.return_date IS NOT NULL
            THEN ROUND(EXTRACT(EPOCH FROM (r.return_date - r.rental_date)) / 86400, 2)
        ELSE NULL
    END AS rental_days,

    NULL::TEXT AS device,
    NULL::TEXT AS payment_method,
    rs.status_name AS rental_status
FROM rental r
JOIN olap.dim_user du 
    ON r.user_id = du.user_id
JOIN olap.dim_movie dm 
    ON r.movie_id = dm.movie_id
JOIN rental_status rs 
    ON r.rental_status_id = rs.rental_status_id
JOIN olap.dim_action_type dat 
    ON dat.action_type = 'RENTAL'

UNION ALL

-- 4. Отзывы
SELECT
    TO_CHAR(rv.review_date::DATE, 'YYYYMMDD')::INT AS date_id,
    du.user_key,
    dm.movie_key,
    dat.action_type_key,

    'review' AS source_table,
    rv.review_id AS source_id,

    rv.review_date AS action_timestamp,
    1 AS action_count,

    NULL::NUMERIC(10, 2) AS revenue,
    NULL::INT AS progress,
    rv.rating AS rating,
    NULL::NUMERIC(10, 2) AS rental_days,

    NULL::TEXT AS device,
    NULL::TEXT AS payment_method,
    NULL::TEXT AS rental_status
FROM review rv
JOIN olap.dim_user du 
    ON rv.user_id = du.user_id
JOIN olap.dim_movie dm 
    ON rv.movie_id = dm.movie_id
JOIN olap.dim_action_type dat 
    ON dat.action_type = 'REVIEW';
```

Индексы для производительности:

```sql
CREATE INDEX ix_fact_user_actions_date 
    ON olap.fact_user_actions(date_id);

CREATE INDEX ix_fact_user_actions_user 
    ON olap.fact_user_actions(user_key);

CREATE INDEX ix_fact_user_actions_movie 
    ON olap.fact_user_actions(movie_key);

CREATE INDEX ix_fact_user_actions_action_type 
    ON olap.fact_user_actions(action_type_key);

CREATE INDEX ix_fact_user_actions_timestamp 
    ON olap.fact_user_actions(action_timestamp);
```

## 6. Написать минимум 3 аналитических запроса





