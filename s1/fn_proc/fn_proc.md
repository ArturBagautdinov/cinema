Процедуры 3 шт + запрос просмотра всех процедур

добавить пользователя
```sql
CREATE OR REPLACE PROCEDURE Cinema.add_user(
    p_id VARCHAR,
    p_name VARCHAR,
    p_role VARCHAR,
    p_email VARCHAR
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO Cinema.User(id, name, role, email, registrationDate)
    VALUES (p_id, p_name, p_role, p_email, CURRENT_DATE);
    RAISE NOTICE 'Пользователь добавлен: %', p_name;
END;
$$;


CALL Cinema.add_user('u010', 'John Doe', 'user', 'john@mail.com');
```

Обновить бюджет фильма
```sql
CREATE OR REPLACE PROCEDURE Cinema.update_movie_budget(
    p_movie_id INT,
    p_new_budget NUMERIC
)
    LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE Cinema.Movie
    SET budget = p_new_budget
    WHERE UniqueID = p_movie_id;

    RAISE NOTICE 'Бюджет фильма обновлен';
END;
$$;

CALL Cinema.update_movie_budget(1, 200000000);
```

Удалить семью (вместе с участниками)
```sql
CREATE OR REPLACE PROCEDURE Cinema.delete_family_group(
    p_group_id INT
)
    LANGUAGE plpgsql
AS $$
BEGIN
    DELETE FROM Cinema.FamilyMember WHERE family_group_id = p_group_id;
    DELETE FROM Cinema.FamilyGroup WHERE UniqueID = p_group_id;

    RAISE NOTICE 'Группа % удалена', p_group_id;
END;
$$;

CALL Cinema.delete_family_group(1);
```

Запрос просмотра всех процедур
```sql
SELECT routine_name
FROM information_schema.routines
WHERE routine_type='PROCEDURE' AND specific_schema='cinema';
```

Функции 3 шт

Получить количество фильмов
```sql
CREATE OR REPLACE FUNCTION Cinema.get_movie_count()
    RETURNS INT
    LANGUAGE plpgsql
AS $$
DECLARE cnt INT;
BEGIN
    SELECT COUNT(*) INTO cnt FROM Cinema.Movie;
    RETURN cnt;
END;
$$;

SELECT Cinema.get_movie_count();
```

Получить количество пользователей
```sql
CREATE OR REPLACE FUNCTION Cinema.get_user_count()
RETURNS INT
LANGUAGE plpgsql
AS $$
DECLARE r INT;
BEGIN
    SELECT COUNT(*) INTO r FROM Cinema.User;
    RETURN r;
END;
$$;

SELECT Cinema.get_user_count();
```

Вернуть максимальную цену подписки
```sql
CREATE OR REPLACE FUNCTION Cinema.get_max_subscription_price()
RETURNS NUMERIC
LANGUAGE plpgsql
AS $$
DECLARE m NUMERIC;
BEGIN
    SELECT MAX(price) INTO m FROM Cinema.Subscription;
    RETURN m;
END;
$$;

SELECT Cinema.get_max_subscription_price();
```

функции с переменными 3 шт

Получить фильмы по режиссёру
```sql
CREATE OR REPLACE FUNCTION Cinema.get_movies_by_director(p_dir_id INT)
RETURNS TABLE(movie_title VARCHAR)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT title FROM Cinema.Movie WHERE director_id = p_dir_id;
END;
$$;

SELECT * FROM Cinema.get_movies_by_director(2);
```

Получить средний рейтинг фильма
```sql
CREATE OR REPLACE FUNCTION Cinema.get_movie_avg_rating(p_movie_id INT)
RETURNS NUMERIC
LANGUAGE plpgsql
AS $$
DECLARE avg_rating NUMERIC;
BEGIN
    SELECT AVG(rating) INTO avg_rating FROM Cinema.Review
    WHERE movie_id = p_movie_id;

    RETURN avg_rating;
END;
$$;

SELECT Cinema.get_movie_avg_rating(1);
```

Проверить активна ли подписка пользователя
```sql
CREATE OR REPLACE FUNCTION Cinema.is_subscription_active(p_user_id INT)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE st VARCHAR;
BEGIN
    SELECT status INTO st FROM Cinema.Subscription
    WHERE user_id = p_user_id
    ORDER BY end_date DESC LIMIT 1;

    RETURN st = 'active';
END;
$$;

SELECT Cinema.is_subscription_active(1);
```

запрос просмотра всех функций
```sql
SELECT routine_name 
FROM information_schema.routines
WHERE routine_type='FUNCTION' AND specific_schema='cinema';
```

Блок DO 3 шт

Показать количество фильмов
```sql
DO $$
DECLARE c INT;
BEGIN
    SELECT COUNT(*) INTO c FROM Cinema.Movie;
    RAISE NOTICE 'Фильмов: %', c;
END $$;
```

Обновить цену всех прокатов
```sql
DO $$
BEGIN
    UPDATE Cinema.Rental SET price = price + 1;
END $$;
```

Добавить жанр
```sql
DO $$
BEGIN
    INSERT INTO Cinema.Genre(name, description)
    VALUES ('RandomGenre', 'Just random genre');
END $$;
```

IF 1 шт

Если пользователей больше 5, выводит сообщение:
"Пользователей больше 5"
Если 5 или меньше, выводит:
"Пользователей 5 или меньше"

```sql
DO $$
DECLARE cnt INT;
BEGIN
    SELECT COUNT(*) INTO cnt FROM Cinema.User;
    
    IF cnt > 5 THEN
        RAISE NOTICE 'Пользователей больше 5';
    ELSE
        RAISE NOTICE 'Пользователей 5 или меньше';
    END IF;

END $$;
```

CASE 1 шт

"case" анализирует средний рейтинг фильма из таблицы Review
```sql
DO $$
    DECLARE
        avg_rating NUMERIC;
        v_movie_id INT := 1;
    BEGIN
        SELECT AVG(rating) INTO avg_rating
        FROM Cinema.Review r
        WHERE r.movie_id = v_movie_id;

        RAISE NOTICE '%',
            CASE
                WHEN avg_rating >= 9 THEN 'Отличный фильм'
                WHEN avg_rating >= 7 THEN 'Хороший фильм'
                ELSE 'Средний фильм'
                END;
    END $$;
```

WHILE 2 шт

счет общей выручки из таблицы Rental
```sql
DO $$
DECLARE 
    v_idx INT := 0;
    v_total_rows INT;
    v_price NUMERIC(10,2);
    v_total_sum NUMERIC(10,2) := 0;
BEGIN
    SELECT COUNT(*) INTO v_total_rows
    FROM Cinema.Rental;

    WHILE v_idx < v_total_rows LOOP
        SELECT price
        INTO v_price
        FROM Cinema.Rental r
        ORDER BY r.UniqueID
        LIMIT 1 OFFSET v_idx;

        v_total_sum := v_total_sum + COALESCE(v_price, 0);

        v_idx := v_idx + 1;
    END LOOP;

    RAISE NOTICE 'Общая выручка от проката: %', v_total_sum;
END $$;
```

вывод всех фильмов с их режиссёрами

```sql
DO $$
DECLARE 
    v_idx INT := 0;
    v_total_rows INT;
    v_title VARCHAR(200);
    v_director_name VARCHAR(100);
BEGIN
    SELECT COUNT(*) INTO v_total_rows
    FROM Cinema.Movie;

    WHILE v_idx < v_total_rows LOOP
        SELECT m.title, d.name
        INTO v_title, v_director_name
        FROM Cinema.Movie m
        LEFT JOIN Cinema.Director d ON m.director_id = d.UniqueID
        ORDER BY m.UniqueID
        LIMIT 1 OFFSET v_idx;

        RAISE NOTICE 'Фильм: %, режиссёр: %', v_title, v_director_name;

        v_idx := v_idx + 1;
    END LOOP;
END $$;
```

EXCEPTION 2 шт

Ошибка при вставке дубликата email
```sql
DO $$
BEGIN
    INSERT INTO Cinema.User(id, name, email)
    VALUES ('u999', 'Test', 'alice@mail.com');
EXCEPTION
    WHEN unique_violation THEN
        RAISE NOTICE 'Email уже существует!';
END $$;
```

Ошибка при неправильном ID фильма
```sql
DO $$
    DECLARE t VARCHAR;
    BEGIN
        SELECT title INTO STRICT t
        FROM Cinema.Movie
        WHERE UniqueID = -1;

        RAISE NOTICE 'Название фильма: %', t;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE NOTICE 'Фильм с таким ID не найден';
    END $$;
```

RAISE 2 шт

```sql
DO $$
BEGIN
    RAISE NOTICE 'Сообщение от RAISE NOTICE';
END $$;
```

```sql
DO $$
BEGIN
    RAISE EXCEPTION 'Это тестовая ошибка!';
END $$;
```

