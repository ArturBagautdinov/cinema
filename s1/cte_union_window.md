// CTE

1. Найти пользователей, которые смотрели фильмы и оставили отзывы 

WITH views_reviews AS (
    Select distinct v.user_id from cinema.viewing v inner join cinema.review r on 
        v.user_id = r.user_id and v.movie_id = r.movie_id
)
select u.name from cinema.users u  inner join views_reviews vr on u.user_id = vr.user_id

2. Найти режиссёров, чьи фильмы имеют рейтинг выше 8

WITH high_rated_movies AS (
    SELECT DISTINCT movie_id
    FROM cinema.review
    WHERE rating > 8
)
SELECT DISTINCT d.name AS director_name
FROM cinema.director d
INNER JOIN cinema.movie m ON d.director_id = m.director_id
INNER JOIN high_rated_movies hrm ON m.movie_id = hrm.movie_id;


3. Найти пользователей, у которых есть подписка и они арендовали фильм

WITH subscribed_renters AS (
    SELECT DISTINCT s.user_id
    FROM cinema.subscription s
    INNER JOIN cinema.rental r ON s.user_id = r.user_id
)
SELECT u.name, u.email
FROM cinema.users u
INNER JOIN subscribed_renters sr ON u.user_id = sr.user_id;


4. Найти фильмы, которые были куплены и просмотрены

WITH purchase_viewing AS (
    SELECT DISTINCT p.movie_id 
    FROM cinema.purchase p 
    JOIN cinema.viewing v ON p.movie_id = v.movie_id
)
SELECT m.title 
FROM cinema.movie m 
JOIN purchase_viewing pv ON m.movie_id = pv.movie_id;


5. Найти фильмы, у которых есть актёры и жанры

WITH movies_with_actors_genres AS (
    SELECT DISTINCT m.movie_id, m.title
    FROM cinema.movie m
    INNER JOIN cinema.movie_actor ma ON m.movie_id = ma.movie_id
    INNER JOIN cinema.movie_genre mg ON m.movie_id = mg.movie_id
)
SELECT DISTINCT title FROM movies_with_actors_genres;


// UNION, INTERSECT, EXCEPT

UNION

1. Объединить пользователей, которые покупали или арендовали фильмы

SELECT user_id, 'purchase' AS action_type FROM cinema.purchase
UNION
SELECT user_id, 'rental' AS action_type FROM cinema.rental;

2. Найти фильмы, которые были куплены или просмотрены

SELECT movie_id FROM cinema.purchase
UNION
SELECT movie_id FROM cinema.viewing;

3. Найти пользователей, которые смотрели ИЛИ оставляли отзывы на фильмы

SELECT user_id FROM cinema.viewing
UNION
SELECT user_id FROM cinema.review;

INTERSECT

1. Найти пользователей, которые и покупали, и арендовали фильмы

SELECT user_id FROM cinema.purchase
INTERSECT
SELECT user_id FROM cinema.rental;

2. Найти фильмы, которые были и куплены, и просмотрены

SELECT movie_id FROM cinema.purchase
INTERSECT
SELECT movie_id FROM cinema.viewing;

3. Найти пользователей, которые и арендовали, и оставляли отзывы

SELECT u.name FROM cinema.users u JOIN cinema.rental r ON u.user_id = r.user_id
INTERSECT
SELECT u.name FROM cinema.users u JOIN cinema.viewing v ON u.user_id = v.user_id

EXCEPT

1. Найти фильмы, в которых снимались актёры, но не было жанра "Драма"

SELECT DISTINCT m.movie_id
FROM cinema.movie m
JOIN cinema.movie_actor ma ON m.movie_id = ma.movie_id
EXCEPT
SELECT DISTINCT mg.movie_id
FROM cinema.movie_genre mg
JOIN cinema.genre g ON mg.genre_id = g.genre_id
WHERE g.name = 'Драма';

2. Найти пользователей, которые оставили отзывы, но не являются детьми

SELECT DISTINCT user_id
FROM cinema.review
EXCEPT
SELECT DISTINCT user_id
FROM cinema.users where role = 'child';

3. Найти пользователей, которые покупали фильмы, но никогда не арендовали

SELECT DISTINCT user_id
FROM cinema.purchase
EXCEPT
SELECT DISTINCT user_id
FROM cinema.rental;


// PARTITION BY

1. Найти количество отзывов для каждого пользователя

Select u.name, count(*) over (partition by r.user_id)
from cinema.review r join cinema.users u on u.user_id = r.user_id

2. Найти средний рейтинг для каждого фильма 

SELECT 
    movie_id,
    rating,
    AVG(rating) OVER (PARTITION BY movie_id) AS avg_rating
FROM cinema.review;

// PARTITION BY + ORDER BY

1. Найти порядковый номер отзыва по дате внутри каждого фильма

SELECT 
    movie_id,
    user_id,
    rating,
    review_date,
    ROW_NUMBER() OVER (PARTITION BY movie_id ORDER BY review_date) AS review_order
FROM cinema.review;

2. Найти накопленный рейтинг для каждого пользователя по мере поступления отзывов

SELECT 
    user_id,
    rating,
    review_date,
    SUM(rating) OVER (PARTITION BY user_id ORDER BY review_date) AS cumulative_rating
FROM cinema.review;

// ROWS и RANGE

1. Найти среднее рейтинга за последние 1 отзыва по каждому фильму

SELECT 
    movie_id,
    rating,
    review_date,
    AVG(rating) OVER (
        PARTITION BY movie_id 
        ORDER BY review_date 
        ROWS BETWEEN 1 PRECEDING AND CURRENT ROW
    ) AS rolling_avg_rating
FROM cinema.review;

2. Найти количество отзывов за последние 1 записи по каждому пользователю

SELECT 
    user_id,
    rating,
    review_date,
    COUNT(*) OVER (
        PARTITION BY user_id 
        ORDER BY review_date 
        ROWS BETWEEN 1 PRECEDING AND CURRENT ROW
    ) AS recent_reviews_count
FROM cinema.review;


1. Найти количество отзывов за последний день по дате отзыва для каждого фильма

SELECT 
    movie_id,
    rating,
    review_date,
    COUNT(*) OVER (
        PARTITION BY movie_id 
        ORDER BY review_date 
        RANGE BETWEEN INTERVAL '1 day' PRECEDING AND CURRENT ROW
    ) AS reviews_last_day
FROM cinema.review;

2. Найти сумму рейтингов за последние 30 дней по дате отзыва для каждого пользователя

SELECT 
    user_id,
    rating,
    review_date,
    SUM(rating) OVER (
        PARTITION BY user_id 
        ORDER BY review_date 
        RANGE BETWEEN INTERVAL '30 days' PRECEDING AND CURRENT ROW
    ) AS rating_sum_30_days
FROM cinema.review;

// Ранжирующие все виды

Фильмы и цены покупок

SELECT 
    movie_id,
    price,
    ROW_NUMBER() OVER (PARTITION BY movie_id ORDER BY price) AS row_num,
    RANK() OVER (PARTITION BY movie_id ORDER BY price) AS rank_by_price,
    DENSE_RANK() OVER (PARTITION BY movie_id ORDER BY price) AS dense_rank_by_price
FROM cinema.purchase
ORDER BY movie_id, price;
 

// Функции смещения все виды

Айди пользователей, рейтинг, дата рейтинга

SELECT 
    user_id,
    rating,
    review_date,
    LAG(rating, 1) OVER (ORDER BY review_date) AS prev_rating,
    LEAD(rating, 1) OVER (ORDER BY review_date) AS next_rating,
    FIRST_VALUE(rating) OVER (ORDER BY review_date) AS first_rating,
    LAST_VALUE(rating) OVER (ORDER BY review_date 
                             ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS last_rating
FROM cinema.review
ORDER BY review_date;
