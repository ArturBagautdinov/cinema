## GIN индексы
Создание
```sql

CREATE INDEX IF NOT EXISTS gin_movie_fts_idx
ON movie
USING GIN (
    to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, ''))
);

CREATE INDEX IF NOT EXISTS gin_movie_title_trgm_idx
ON movie
USING GIN (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS gin_actor_name_trgm_idx
ON actor
USING GIN (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS gin_viewing_tags_idx
ON viewing
USING GIN (tags);

CREATE INDEX IF NOT EXISTS gin_purchase_payment_meta_idx
ON purchase
USING GIN (payment_meta);
```
Запросы
```sql
-- 1. GIN индекс: gin_movie_fts_idx
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    movie_id,
    title
FROM movie
WHERE to_tsvector(
        'english',
        coalesce(title, '') || ' ' || coalesce(description, '')
      )
@@ to_tsquery('english', 'great | plot | action')
LIMIT 20;
```
<img width="885" height="367" alt="Screenshot 2026-03-09 at 22 41 56" src="https://github.com/user-attachments/assets/2133ef17-b49f-4ad8-8509-1e345417d943" />

```sql
-- 2. GIN индекс: gin_movie_title_trgm_idx

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    movie_id,
    title
FROM movie
WHERE title ILIKE '%Movie 100%';
```
<img width="777" height="339" alt="Screenshot 2026-03-09 at 22 43 10" src="https://github.com/user-attachments/assets/564a4ed5-22ee-493a-a94f-a0349e424cff" />


```sql
-- 3. GIN индекс: gin_actor_name_trgm_idx

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    actor_id,
    name,
    similarity(name, 'Actor 500') AS sim
FROM actor
WHERE name % 'Actor 500'
ORDER BY similarity(name, 'Actor 500') DESC
LIMIT 20;
```
<img width="831" height="465" alt="Screenshot 2026-03-09 at 22 47 49" src="https://github.com/user-attachments/assets/0f23305b-84d4-4873-b882-0edd4fe7387a" />



```sql
-- 4. GIN индекс: gin_viewing_tags_idx

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    viewing_id,
    user_id,
    movie_id,
    tags
FROM viewing
WHERE tags @> ARRAY['action'];
```
<img width="805" height="314" alt="Screenshot 2026-03-09 at 22 48 52" src="https://github.com/user-attachments/assets/d7923a9c-5328-42ca-bfcc-457219986687" />

```sql
-- 5. GIN индекс: gin_purchase_payment_meta_idx

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    purchase_id,
    user_id,
    movie_id,
    payment_meta
FROM purchase
WHERE payment_meta @> '{"provider":"paypal"}';
```
<img width="871" height="315" alt="Screenshot 2026-03-09 at 22 50 00" src="https://github.com/user-attachments/assets/d2456c95-c00c-43c4-8556-07338a20b543" />

## GIST индексы
создание 
```sql
CREATE INDEX IF NOT EXISTS gist_rental_period_idx
ON rental
USING GIST (rental_period);

CREATE INDEX IF NOT EXISTS gist_purchase_amount_range_idx
ON purchase
USING GIST (amount_range);

CREATE INDEX IF NOT EXISTS gist_movie_title_trgm_idx
ON movie
USING GIST (title gist_trgm_ops);

CREATE INDEX IF NOT EXISTS gist_actor_name_trgm_idx
ON actor
USING GIST (name gist_trgm_ops);

CREATE INDEX IF NOT EXISTS gist_movie_fts_idx
ON movie
USING GIST (
    to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, ''))
);
```
Запросы

```sql
-- 1. GiST индекс: gist_rental_period_idx

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    rental_id,
    user_id,
    movie_id,
    rental_period
FROM rental
WHERE rental_period && tstzrange(
        now() - interval '30 days',
        now()
);
```
<img width="818" height="315" alt="Screenshot 2026-03-09 at 22 54 23" src="https://github.com/user-attachments/assets/d4c022ec-5817-45f8-bd61-f7e6fb80af1e" />

```sql
-- 2. GiST индекс: gist_purchase_amount_range_idx

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    purchase_id,
    user_id,
    movie_id,
    amount_range
FROM purchase
WHERE amount_range @> 1000;
```
<img width="880" height="317" alt="Screenshot 2026-03-09 at 22 55 23" src="https://github.com/user-attachments/assets/2eefaac3-cccf-4ff2-9a54-9ff5889b8e18" />

```sql
-- 3. GiST индекс: gist_movie_title_trgm_idx

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    movie_id,
    title,
    similarity(title, 'Movie 100')
FROM movie
WHERE title % 'Movie 100'
ORDER BY similarity(title, 'Movie 100') DESC
LIMIT 20;
```
<img width="825" height="615" alt="Screenshot 2026-03-09 at 22 56 28" src="https://github.com/user-attachments/assets/e19645ce-0545-4244-9a4a-0c260163c123" />

```sql
-- 4. GiST индекс: gist_actor_name_trgm_idx

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    actor_id,
    name,
    similarity(name, 'Actor 500')
FROM actor
WHERE name % 'Actor 500'
ORDER BY similarity(name, 'Actor 500') DESC
LIMIT 20;
```
<img width="830" height="466" alt="Screenshot 2026-03-09 at 22 57 28" src="https://github.com/user-attachments/assets/19e348df-0570-4911-826f-f95f79f2a26b" />

```sql
-- 5. GiST индекс: gist_movie_fts_idx

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    movie_id,
    title
FROM movie
WHERE to_tsvector(
        'english',
        coalesce(title,'') || ' ' || coalesce(description,'')
      )
@@ plainto_tsquery('english','great plot action');
```
<img width="892" height="215" alt="Screenshot 2026-03-09 at 22 58 42" src="https://github.com/user-attachments/assets/10ba353d-90bd-41cc-a516-e6f8a46a32c0" />

## JOIN

```sql
-- 1. Фильмы + режиссеры + страна + язык
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    m.movie_id,
    m.title,
    d.name AS director_name,
    c.country_name,
    l.language_name
FROM movie m
JOIN director d ON m.director_id = d.director_id
LEFT JOIN country c ON m.country_id = c.country_id
LEFT JOIN language l ON m.language_id = l.language_id
ORDER BY m.movie_id
LIMIT 20;
````
<img width="756" height="691" alt="Screenshot 2026-03-09 at 23 06 44" src="https://github.com/user-attachments/assets/78ee393b-357f-4f37-bbbd-9a2701f7f32d" />

<img width="758" height="302" alt="Screenshot 2026-03-09 at 23 08 01" src="https://github.com/user-attachments/assets/f1aeab69-eab7-4bf4-97c4-1d3195cc5e6b" />
<br> Результат: Используется Nested Loop, так как количество строк небольшое благодаря LIMIT, и поиск по первичным ключам выполняется быстро.

```sql
-- 2. Отзывы + пользователи + фильмы
SELECT
    r.review_id,
    u.name,
    m.title,
    r.rating
FROM review r
JOIN users u ON r.user_id = u.user_id
JOIN movie m ON r.movie_id = m.movie_id
LIMIT 20;
```
<img width="730" height="740" alt="Screenshot 2026-03-09 at 23 10 34" src="https://github.com/user-attachments/assets/8e655b92-bcfd-4930-b478-3ba13468239e" />

<br> Результат: Используется Nested Loop, так как соединение происходит по первичным ключам и возвращается небольшое количество строк.

```sql
-- 3. Аренды + пользователи + фильмы + статус
SELECT
    r.rental_id,
    u.name,
    m.title,
    rs.status_name
FROM rental r
JOIN users u ON r.user_id = u.user_id
JOIN movie m ON r.movie_id = m.movie_id
JOIN rental_status rs ON r.rental_status_id = rs.rental_status_id
LIMIT 20;
```
<img width="810" height="691" alt="Screenshot 2026-03-09 at 23 12 19" src="https://github.com/user-attachments/assets/7b3e53e1-f664-4331-9cb9-37061a1a59a1" />
<img width="812" height="300" alt="Screenshot 2026-03-09 at 23 12 47" src="https://github.com/user-attachments/assets/fa26afb3-71cd-4e8c-b9ce-cb5e0abb6e43" />

<br> Результат: Используется Nested Loop, так как выполняется соединение небольшого числа строк по индексированным ключам.

```sql
-- 4. review + users
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    r.review_id,
    r.user_id,
    u.name,
    r.rating,
    r.review_date
FROM review r
JOIN users u ON r.user_id = u.user_id;
```
<img width="717" height="390" alt="Screenshot 2026-03-09 at 23 18 53" src="https://github.com/user-attachments/assets/99a77220-8623-4898-b5d5-44c79f218e85" />
<br> Результат: Hash Join, так как соединяются большие таблицы без LIMIT, и PostgreSQL выгоднее построить хеш по users, чем выполнять много отдельных индексных поисков.

```sql
-- 5. Пользователи + подписки + тариф + статус
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    u.user_id,
    u.name,
    sp.plan_type,
    ss.status_name
FROM users u
LEFT JOIN subscription s ON u.user_id = s.user_id
LEFT JOIN subscription_plan sp ON s.plan_id = sp.plan_id
LEFT JOIN subscription_status ss ON s.subscr_status_id = ss.subscr_status_id
LIMIT 20;
```
<img width="872" height="590" alt="Screenshot 2026-03-09 at 23 25 42" src="https://github.com/user-attachments/assets/0fdffc88-4a9e-4e53-a3ea-18f151fc3ac8" />
<br> Результат: Используется Nested Loop Left Join, так как применяется LEFT JOIN и возвращается небольшое количество строк.
