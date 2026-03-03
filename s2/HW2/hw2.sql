-- >

Explain
FROM users
WHERE registration_date > now() - interval '30 days';

EXPLAIN ANALYZE
FROM users
WHERE registration_date > now() - interval '30 days';

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM users
WHERE registration_date > now() - interval '30 days';

-- B-tree
CREATE INDEX idx_users_regdate_btree ON users USING btree (registration_date);

-- Hash
CREATE INDEX idx_users_regdate_hash ON users USING hash (registration_date);

DROP INDEX IF EXISTS idx_users_regdate_btree;
DROP INDEX IF EXISTS idx_users_regdate_hash;

-- <

Explain
SELECT rental_id, user_id, price
FROM rental
WHERE price < 3.00;

EXPLAIN ANALYZE
SELECT rental_id, user_id, price
FROM rental
WHERE price < 3.00;

EXPLAIN (ANALYZE, BUFFERS)
SELECT rental_id, user_id, price
FROM rental
WHERE price < 3.00;

CREATE INDEX idx_rental_price_btree ON rental USING btree (price);
CREATE INDEX idx_rental_price_hash  ON rental USING hash (price);

drop INDEX idx_rental_price_btree;
drop INDEX idx_rental_price_hash;

-- =

Explain
SELECT *
FROM subscription
WHERE user_id = 12345;

EXPLAIN ANALYZE
SELECT *
FROM subscription
WHERE user_id = 12345;

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM subscription
WHERE user_id = 12345;

CREATE INDEX idx_subscription_userid_btree ON subscription USING btree (user_id);
CREATE INDEX idx_subscription_userid_hash  ON subscription USING hash (user_id);

drop INDEX idx_subscription_userid_btree;
drop INDEX idx_subscription_userid_hash;

-- LIKE 'prefix%'

Explain
SELECT movie_id, title
FROM movie
WHERE title LIKE 'Star%';

EXPLAIN ANALYZE
SELECT movie_id, title
FROM movie
WHERE title LIKE 'Star%';

EXPLAIN (ANALYZE, BUFFERS)
SELECT movie_id, title
FROM movie
WHERE title LIKE 'Star%';

CREATE INDEX idx_movie_title_btree_pattern ON movie USING btree (title text_pattern_ops);
CREATE INDEX idx_movie_title_hash ON movie USING hash (title);

drop INDEX idx_movie_title_btree_pattern;
drop INDEX idx_movie_title_hash;

-- LIKE '%prefix'

Explain
SELECT movie_id, title
FROM movie
WHERE title LIKE '%war%';

EXPLAIN ANALYZE
SELECT movie_id, title
FROM movie
WHERE title LIKE '%war%';

EXPLAIN (ANALYZE, BUFFERS)
SELECT movie_id, title
FROM movie
WHERE title LIKE 'Star%';

CREATE INDEX idx_movie_title_btree ON movie USING btree (title);
CREATE INDEX idx_movie_title_hash  ON movie USING hash (title);

drop INDEX idx_movie_title_btree;
drop INDEX idx_movie_title_hash;

-- composite index

Explain
SELECT rental_id, user_id, rental_date, price
FROM rental
WHERE user_id = 12345
  AND rental_date >= now() - interval '30 days'
ORDER BY rental_date DESC;

EXPLAIN ANALYZE
SELECT rental_id, user_id, rental_date, price
FROM rental
WHERE user_id = 12345
  AND rental_date >= now() - interval '30 days'
ORDER BY rental_date DESC;

EXPLAIN (ANALYZE, BUFFERS)
SELECT rental_id, user_id, rental_date, price
FROM rental
WHERE user_id = 12345
  AND rental_date >= now() - interval '30 days'
ORDER BY rental_date DESC;

CREATE INDEX idx_rental_userid_rdate
    ON rental (user_id, rental_date DESC);

drop INDEX idx_rental_userid_rdate;