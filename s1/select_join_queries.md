-- 1) Простая выборка фильмов с базовыми полями
SELECT 
    m.UniqueID as movie_id, 
    m.title, 
    m.release_year, 
    m.duration
FROM Cinema.Movie m
ORDER BY m.release_year DESC, m.title;

-- 2) Пользователи, зарегистрированные за последние 3 года
SELECT 
    u.UniqueID as user_id, 
    u.name, 
    u.email, 
    u.registrationDate
FROM Cinema.User u
WHERE u.registrationDate >= (CURRENT_DATE - INTERVAL '3 year')
ORDER BY u.registrationDate DESC;

-- CASE запрос 1: Категоризация фильмов по возрастному рейтингу
SELECT
    m.UniqueID as movie_id,
    m.title,
    m.age_rating,
    CASE
        WHEN m.age_rating = 'PG' THEN 'Для детей с родителями'
        WHEN m.age_rating = 'PG-13' THEN 'Для детей от 13 лет'
        WHEN m.age_rating = 'R' THEN 'Для взрослых'
        ELSE 'Возрастной рейтинг не указан'
    END AS age_rating_description
FROM Cinema.Movie m
ORDER BY m.title;

-- CASE запрос 2: Статус подписки пользователя
SELECT
    u.UniqueID as user_id,
    u.name,
    s.plan_type,
    s.status,
    CASE
        WHEN s.status = 'active' THEN 'Активна'
        WHEN s.status = 'expired' THEN 'Истекла'
        ELSE 'Неактивна'
    END AS status_description,
    CASE
        WHEN s.plan_type = 'Premium' THEN 'Премиум'
        WHEN s.plan_type = 'Standard' THEN 'Стандарт'
        WHEN s.plan_type = 'Basic' THEN 'Базовый'
        ELSE 'Неизвестно'
    END AS plan_translation
FROM Cinema.User u
LEFT JOIN Cinema.Subscription s ON s.user_id = u.UniqueID
ORDER BY u.name;

-- INNER JOIN запрос 1: Фильмы с их режиссерами
SELECT 
    m.UniqueID as movie_id, 
    m.title, 
    d.name AS director_name,
    d.country AS director_country
FROM Cinema.Movie m
INNER JOIN Cinema.Director d ON m.director_id = d.UniqueID
ORDER BY d.name, m.title;

-- INNER JOIN запрос 2: Отзывы пользователей с деталями фильмов
SELECT 
    r.UniqueID as review_id,
    u.name AS user_name, 
    m.title AS movie_title, 
    r.rating,
    r.comment,
    r.review_date
FROM Cinema.Review r
INNER JOIN Cinema.User u ON r.user_id = u.UniqueID
INNER JOIN Cinema.Movie m ON r.movie_id = m.UniqueID
ORDER BY r.rating DESC, r.review_date DESC;

-- LEFT JOIN запрос 1: Все фильмы и их жанры (если есть)
SELECT 
    m.UniqueID as movie_id, 
    m.title,
    g.name AS genre_name,
    g.description AS genre_description
FROM Cinema.Movie m
LEFT JOIN Cinema.MovieGenre mg ON m.UniqueID = mg.movie_id
LEFT JOIN Cinema.Genre g ON mg.genre_id = g.UniqueID
ORDER BY m.title, g.name;

-- LEFT JOIN запрос 2: Все пользователи и их просмотры (если есть)
SELECT 
    u.UniqueID as user_id,
    u.name,
    u.email,
    m.title AS movie_title,
    v.viewing_date,
    v.progress,
    v.device
FROM Cinema.User u
LEFT JOIN Cinema.Viewing v ON u.UniqueID = v.user_id
LEFT JOIN Cinema.Movie m ON v.movie_id = m.UniqueID
ORDER BY u.name, v.viewing_date DESC;

-- RIGHT JOIN запрос 1: Все режиссеры и их фильмы (если есть)
SELECT 
    d.UniqueID as director_id,
    d.name AS director_name,
    m.title AS movie_title,
    m.release_year
FROM Cinema.Movie m
RIGHT JOIN Cinema.Director d ON m.director_id = d.UniqueID
ORDER BY d.name, m.release_year;

-- RIGHT JOIN запрос 2: Все жанры и связанные фильмы (если есть)
SELECT 
    g.UniqueID as genre_id,
    g.name AS genre_name,
    m.title AS movie_title
FROM Cinema.Movie m
RIGHT JOIN Cinema.MovieGenre mg ON m.UniqueID = mg.movie_id
RIGHT JOIN Cinema.Genre g ON mg.genre_id = g.UniqueID
ORDER BY g.name, m.title;

-- FULL OUTER JOIN запрос 1: Полный список фильмов и отзывов
SELECT 
    m.UniqueID as movie_id, 
    m.title, 
    r.UniqueID as review_id, 
    r.rating, 
    r.comment,
    CASE 
        WHEN r.UniqueID IS NULL THEN 'Нет отзывов'
        WHEN m.UniqueID IS NULL THEN 'Отзыв без фильма'
        ELSE 'Есть отзыв'
    END AS status
FROM Cinema.Movie m
FULL OUTER JOIN Cinema.Review r ON r.movie_id = m.UniqueID
ORDER BY m.title NULLS LAST, r.rating DESC;

-- FULL OUTER JOIN запрос 2: Полный список пользователей и подписок
SELECT 
    u.UniqueID as user_id, 
    u.name,
    s.UniqueID as subscription_id, 
    s.plan_type, 
    s.status,
    CASE 
        WHEN s.UniqueID IS NULL THEN 'Нет подписки'
        WHEN u.UniqueID IS NULL THEN 'Подписка без пользователя'
        ELSE 'Активная подписка'
    END AS subscription_status
FROM Cinema.User u
FULL OUTER JOIN Cinema.Subscription s ON s.user_id = u.UniqueID
ORDER BY u.name NULLS LAST, s.start_date DESC;

-- CROSS JOIN запрос 1: Все комбинации планов подписок и статусов
SELECT 
    plan.plan_type,
    status.status_type,
    CASE 
        WHEN plan.plan_type = 'Premium' AND status.status_type = 'active' 
            THEN 'Премиум активен'
        WHEN plan.plan_type = 'Standard' AND status.status_type = 'active' 
            THEN 'Стандарт активен'
        ELSE 'Другая комбинация'
    END AS combination_description
FROM (VALUES ('Premium'), ('Standard'), ('Basic')) AS plan(plan_type)
CROSS JOIN (VALUES ('active'), ('expired'), ('cancelled')) AS status(status_type)
ORDER BY plan.plan_type, status.status_type;

-- CROSS JOIN запрос 2: Все комбинации возрастных рейтингов и стран
SELECT 
    country.country_name,
    rating.age_rating,
    CASE 
        WHEN rating.age_rating = 'R' THEN 'Ограниченный показ'
        WHEN rating.age_rating IN ('PG', 'PG-13') THEN 'Семейный просмотр'
        ELSE 'Свободный доступ'
    END AS access_level
FROM (VALUES ('USA'), ('UK'), ('Russia')) AS country(country_name)
CROSS JOIN (VALUES ('PG'), ('PG-13'), ('R')) AS rating(age_rating)
ORDER BY country.country_name, rating.age_rating;