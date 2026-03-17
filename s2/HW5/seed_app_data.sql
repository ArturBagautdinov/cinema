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