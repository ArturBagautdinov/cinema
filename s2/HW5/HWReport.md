## Посмотреть на изменение LSN и WAL после изменения данных

### a. Сравнение LSN до и после INSERT

до:
```sql
SELECT pg_current_wal_lsn() AS lsn_before;
```
<img width="135" height="68" alt="Screenshot 2026-03-16 at 22 16 22" src="https://github.com/user-attachments/assets/895ff9f1-80fa-4c93-b720-bd31af0ed1b4" /> <br>
вставляем данные:<br>
```sql
INSERT INTO purchase (user_id, movie_id, price, method_id)
VALUES (1, 1, 9.99, 1);
```
LSN после: <br>
<img width="137" height="70" alt="Screenshot 2026-03-16 at 22 17 48" src="https://github.com/user-attachments/assets/64f22e4d-ebcc-440d-a63c-dfb8378a6ffa" /><br>
После добавления новой строки значение pg_current_wal_lsn() увеличилось. <br>
Это означает, что PostgreSQL записал информацию об операции вставки в журнал WAL (Write-Ahead Log). <br>
Разница между LSN до и после операции, вычисленная с помощью pg_wal_lsn_diff(), показывает количество байт WAL, созданных этой операцией.<br>

### b. Сравнение WAL до и после commit



```sql
DROP TABLE IF EXISTS wal_test_lsn;

CREATE TEMP TABLE wal_test_lsn
(
    stage text,
    lsn   pg_lsn
);

BEGIN;

INSERT INTO wal_test_lsn(stage, lsn)
VALUES ('before_insert', pg_current_wal_lsn());

INSERT INTO rental (user_id, movie_id, price, rental_status_id)
VALUES (7, 1, 7.00, 1);

INSERT INTO wal_test_lsn(stage, lsn)
VALUES ('after_insert', pg_current_wal_lsn());

COMMIT;

INSERT INTO wal_test_lsn(stage, lsn)
VALUES ('after_commit', pg_current_wal_lsn());

SELECT
    b.lsn AS before_insert_lsn,
    a.lsn AS after_insert_lsn,
    c.lsn AS after_commit_lsn,
    pg_wal_lsn_diff(a.lsn, b.lsn) AS insert_wal_bytes,
    pg_wal_lsn_diff(c.lsn, a.lsn) AS commit_wal_bytes,
    pg_wal_lsn_diff(c.lsn, b.lsn) AS total_wal_bytes
FROM
    (SELECT lsn FROM wal_test_lsn WHERE stage = 'before_insert') b,
    (SELECT lsn FROM wal_test_lsn WHERE stage = 'after_insert') a,
    (SELECT lsn FROM wal_test_lsn WHERE stage = 'after_commit') c;
```
<img width="784" height="70" alt="Screenshot 2026-03-16 at 22 44 14" src="https://github.com/user-attachments/assets/b4085a82-99b3-4a9e-b809-05a7d94f3233" /> <br>
Даже после выполнения INSERT и записи данных в WAL операция COMMIT также создаёт дополнительную запись в журнале.<br>
Это связано с тем, что PostgreSQL фиксирует факт успешного завершения транзакции.<br>
Разница LSN между моментом после INSERT и после COMMIT показывает объём WAL, который был создан именно для подтверждения транзакции.

### c. Анализ WAL размера после массовой операции

До:
```sql
SELECT pg_current_wal_lsn() AS lsn_before_bulk;
```
<img width="162" height="67" alt="Screenshot 2026-03-16 at 22 52 12" src="https://github.com/user-attachments/assets/fdfd8a70-5b5a-4277-993e-f5f574d41748" /> <br>

Массовая операция вставки:

```sql
INSERT INTO viewing (user_id, movie_id, progress, device, tags, meta)
SELECT
    1,
    1,
    (random()*100)::int,
    'device-' || gs,
    ARRAY['bulk','test'],
    jsonb_build_object('n', gs)
FROM generate_series(1, 5000) gs;
```
После:
```sql
SELECT pg_current_wal_lsn() AS lsn_after_bulk;
```
<img width="154" height="67" alt="Screenshot 2026-03-16 at 22 55 29" src="https://github.com/user-attachments/assets/91dd63df-16fe-498a-b7ae-22b60ebdaf63" /> <br>
До массовой операции 0/3CFDA080 и после операции 0/3D8106D0. <br>
Разница между ними составляет 8 611 408 байт WAL.<br>
Это означает, что при выполнении массовой вставки PostgreSQL сгенерировал около 8.6 МБ записей WAL.<br>
Результаты показали, что при массовой вставке объём созданного WAL значительно увеличивается по сравнению с одиночными операциями, для каждой вставляемой строки PostgreSQL записывает информацию о модификации страниц таблицы в журнал WAL.

## Сделать дамп БД и накатить его на новую чистую БД

### полный dump БД
<img width="705" height="548" alt="Screenshot 2026-03-16 at 23 05 20" src="https://github.com/user-attachments/assets/d83f8802-c133-4780-a8a0-a627301afc20" />
<img width="1512" height="982" alt="Screenshot 2026-03-16 at 23 29 51" src="https://github.com/user-attachments/assets/eda1e7fb-e54c-4ded-8842-7889e444ff4e" />
<img width="1512" height="982" alt="Screenshot 2026-03-16 at 23 44 12" src="https://github.com/user-attachments/assets/70cbe894-c20b-4b56-88e7-c41e6991c627" />

### Dump только структуры базы
<img width="701" height="551" alt="Screenshot 2026-03-16 at 23 47 47" src="https://github.com/user-attachments/assets/29963980-09b7-4b0c-8e9c-969e739d41f6" />
<img width="1512" height="982" alt="Screenshot 2026-03-16 at 23 49 42" src="https://github.com/user-attachments/assets/f9d7c1e5-f44d-4fb1-a548-b4335815a6f5" />
<img width="1512" height="982" alt="Screenshot 2026-03-16 at 23 50 17" src="https://github.com/user-attachments/assets/ef9b6acc-30fa-462b-a2cf-9afe7afb68e8" />

### Dump одной таблицы
<img width="1512" height="982" alt="Screenshot 2026-03-17 at 00 01 06" src="https://github.com/user-attachments/assets/9600c11d-e7ea-4d03-b452-d7d4e40db3ba" />
<img width="1512" height="982" alt="Screenshot 2026-03-17 at 00 01 40" src="https://github.com/user-attachments/assets/ca48c1b2-c048-43e7-8885-c4028c1ceef5" />
<img width="1512" height="982" alt="Screenshot 2026-03-17 at 00 02 01" src="https://github.com/user-attachments/assets/a0ffe910-bc27-4c86-86dd-9c535936a3ae" />


## Создание нескольких seed и проверка идемпотентности seed (ON CONFLICT)

### Seed базовых справочников системы

```sql
-- USER ROLES
INSERT INTO user_role (role_name)
VALUES 
    ('ADMIN'),
    ('USER'),
    ('KID')
ON CONFLICT (role_name) DO NOTHING;

-- SUBSCRIPTION STATUS
INSERT INTO subscription_status (status_name)
VALUES 
    ('ACTIVE'),
    ('PAUSED'),
    ('CANCELLED')
ON CONFLICT (status_name) DO NOTHING;

-- PAYMENT METHODS
INSERT INTO payment_method (method_name)
VALUES 
    ('CARD'),
    ('PAYPAL'),
    ('APPLEPAY')
ON CONFLICT (method_name) DO NOTHING;

-- COUNTRIES
INSERT INTO country (country_name)
VALUES 
    ('USA'),
    ('UK'),
    ('DE')
ON CONFLICT (country_name) DO NOTHING;

-- GENRES
INSERT INTO genre (name, description)
VALUES 
    ('Drama', ''),
    ('Comedy', ''),
    ('Action', '')
ON CONFLICT (name) DO NOTHING;
```
<img width="1512" height="982" alt="Screenshot 2026-03-17 at 11 04 11" src="https://github.com/user-attachments/assets/639188f8-6afe-496e-ae50-47e9187b48e2" />
Команды INSERT используют конструкции ON CONFLICT DO NOTHING, что обеспечивает идемпотентность - при повторном запуске дубликаты данных не создаются, ошибки уникальности не возникают, состояние базы остаётся корректным.

### Seed тестовых бизнес-данных
```sql
-- DIRECTORS
INSERT INTO director (name, birth_date, country_id)
SELECT 
    'Christopher Nolan',
    '1970-07-30',
    c.country_id
FROM country c
WHERE c.country_name = 'UK'
ON CONFLICT DO NOTHING;

-- ACTORS
INSERT INTO actor (name, birth_date, country_id)
SELECT 
    'Leonardo DiCaprio',
    '1974-11-11',
    c.country_id
FROM country c
WHERE c.country_name = 'USA'
ON CONFLICT DO NOTHING;

INSERT INTO actor (name, birth_date, country_id)
SELECT 
    'Matthew McConaughey',
    '1969-11-04',
    c.country_id
FROM country c
WHERE c.country_name = 'USA'
ON CONFLICT DO NOTHING;

-- MOVIES
INSERT INTO movie (title, description, release_year, duration, director_id, country_id, language_id)
SELECT 
    'Inception',
    'Dream inside a dream',
    2010,
    148,
    d.director_id,
    c.country_id,
    l.language_id
FROM director d, country c, language l
WHERE d.name = 'Christopher Nolan'
  AND c.country_name = 'USA'
  AND l.language_name = 'English'
ON CONFLICT DO NOTHING;

INSERT INTO movie (title, description, release_year, duration, director_id, country_id, language_id)
SELECT 
    'Interstellar',
    'Space exploration',
    2014,
    169,
    d.director_id,
    c.country_id,
    l.language_id
FROM director d, country c, language l
WHERE d.name = 'Christopher Nolan'
  AND c.country_name = 'USA'
  AND l.language_name = 'English'
ON CONFLICT DO NOTHING;

-- MOVIE_GENRE
INSERT INTO movie_genre (movie_id, genre_id)
SELECT m.movie_id, g.genre_id
FROM movie m, genre g
WHERE m.title = 'Inception'
  AND g.name = 'Sci-Fi'
ON CONFLICT DO NOTHING;

INSERT INTO movie_genre (movie_id, genre_id)
SELECT m.movie_id, g.genre_id
FROM movie m, genre g
WHERE m.title = 'Interstellar'
  AND g.name = 'Sci-Fi'
ON CONFLICT DO NOTHING;

-- MOVIE_ACTOR
INSERT INTO movie_actor (movie_id, actor_id, character_name)
SELECT m.movie_id, a.actor_id, 'Cobb'
FROM movie m, actor a
WHERE m.title = 'Inception'
  AND a.name = 'Leonardo DiCaprio'
ON CONFLICT DO NOTHING;

INSERT INTO movie_actor (movie_id, actor_id, character_name)
SELECT m.movie_id, a.actor_id, 'Cooper'
FROM movie m, actor a
WHERE m.title = 'Interstellar'
  AND a.name = 'Matthew McConaughey'
ON CONFLICT DO NOTHING;

-- USERS
INSERT INTO users (name, role_id, email)
SELECT 
    'Movie Fan',
    r.role_id,
    'fan@example.com'
FROM user_role r
WHERE r.role_name = 'USER'
ON CONFLICT (email) DO NOTHING;

-- REVIEW
INSERT INTO review (user_id, movie_id, rating, comment)
SELECT 
    u.user_id,
    m.movie_id,
    9,
    'Amazing movie!'
FROM users u, movie m
WHERE u.email = 'fan@example.com'
  AND m.title = 'Inception'
ON CONFLICT (user_id, movie_id) DO NOTHING;
```
<img width="1512" height="982" alt="Screenshot 2026-03-17 at 11 09 49" src="https://github.com/user-attachments/assets/e6fa90ce-4b49-4e82-a145-9516404e42d9" />
Аналогично, использование ON CONFLICT, скрипт корректно выполняется при повторном запуске без создания дубликатов.
