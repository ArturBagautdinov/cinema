## Смоделировать обновление данных и посмотреть на параметры xmin, xmax, ctid, t_infomask; Понять что хранится в t_infomask


Тестовые данные

```sql

INSERT INTO users(name, role_id, email)
SELECT 'Test User', role_id, 'test_user@example.com'
FROM user_role
WHERE role_name = 'USER'
ON CONFLICT (email) DO NOTHING;

INSERT INTO review(user_id, movie_id, rating, comment)
SELECT u.user_id, m.movie_id, 7, 'initial review'
FROM users u
JOIN movie m ON m.title = 'Test Movie'
WHERE u.email = 'test_user@example.com'
ON CONFLICT (user_id, movie_id) DO NOTHING;

INSERT INTO director(name, birth_date, country_id, biography)
SELECT 'Test Director', DATE '1980-01-01', country_id, 'bio'
FROM country
WHERE country_name = 'USA'
AND NOT EXISTS (
    SELECT 1 FROM director WHERE name = 'Test Director'
);

INSERT INTO movie(title, description, release_year, duration, age_rating_id, language_id, country_id, director_id)
SELECT
    'Test Movie',
    'Movie for xmin/xmax demo',
    2024,
    120,
    ar.age_rating_id,
    l.language_id,
    c.country_id,
    d.director_id
FROM age_rating ar
JOIN language l ON l.language_name = 'English'
JOIN country c ON c.country_name = 'USA'
JOIN director d ON d.name = 'Test Director'
WHERE ar.age_rating = 'PG-13'
AND NOT EXISTS (
    SELECT 1 FROM movie WHERE title = 'Test Movie'
);

```
### xmin, xmax, ctid
Просмотр строки до обновления
```sql
SELECT
    review_id,
    rating,
    comment,
    xmin,
    xmax,
    ctid
FROM review
WHERE comment = 'initial review';
```
<img width="474" height="70" alt="Screenshot 2026-03-10 at 19 24 02" src="https://github.com/user-attachments/assets/3fd8b989-839c-4e99-943e-32591614e642" />

xmin = 841 - ID транзакции, которая создала текущую версию строки. <br>
xmax = 0 - значит строка ещё не удалена и не обновлена.<br>
0 означает, что никакая транзакция не пометила её как устаревшую.
ctid = (4059,21)<br>
Физическое расположение строки в таблице: <br>
страница heap = 4059 <br>
слот строки = 21 <br>

<br>

Обновление
```sql
UPDATE review
SET comment = 'review updated #1'
WHERE comment = 'initial review';
```

После обновления
```sql
SELECT
    review_id,
    rating,
    comment,
    xmin,
    xmax,
    ctid
FROM review
WHERE comment = 'review updated #1';
```
<img width="508" height="69" alt="Screenshot 2026-03-10 at 19 34 37" src="https://github.com/user-attachments/assets/c57de974-d500-4b23-bc91-f0841c699485" /> <br>
Что изменилось
1. xmin <br>
Было: xmin = 841 <br>
Стало: xmin = 843 <br>
Это означает, что новая версия строки была создана транзакцией 843. <br>
2. xmax <br>
xmax = 0 - Новая версия строки ещё не обновлялась и не удалялась. <br>
3. ctid <br>
Было: (4059,21) <br>
Стало: (4060,24) <br>
Это означает, что новая версия строки физически хранится в другом месте таблицы. <br>

UPDATE создаёт новую tuple-версию строки, а не изменяет существующую. <br>

если посмотреть на старую версию строки: <br>
<img width="328" height="571" alt="Screenshot 2026-03-10 at 19 53 42" src="https://github.com/user-attachments/assets/0af5d3f7-d73f-419e-acf4-e62df2656053" /> <br>
Autovacuum уже удалил старую версию (Ip=21) <br>

### t_infomask 
t_infomask — это служебное битовое поле в заголовке tuple. <br>
Там хранятся признаки состояния версии строки. <br>
содержатся флаги вида: <br>
<ul>
<li>строка удалена / обновлена
<li>xmax валиден или невалиден
<li>tuple заблокирован
<li>это HOT update 
<li>есть ли NULL-поля
<li>есть ли varlena-поля 
<li>и другие внутренние признаки
</ul>
То есть t_infomask — не данные пользователя, а внутренние флаги состояния tuple для MVCC и хранения строки.

```sql
SELECT
    lp,
    t_xmin,
    t_xmax,
    t_ctid,
    t_infomask,
    t_infomask2
FROM heap_page_items(get_raw_page('review', 4060));
```
<img width="519" height="45" alt="Screenshot 2026-03-10 at 20 10 05" src="https://github.com/user-attachments/assets/d0691011-5f12-4d78-8d70-fcae09263b34" />
<img width="522" height="27" alt="Screenshot 2026-03-10 at 20 10 39" src="https://github.com/user-attachments/assets/b6dcbf0a-f8d2-4c48-ab4a-9d82b586a373" />

Для расшифровки флагов:
```sql
SELECT
    h.lp,
    h.t_xmin,
    h.t_xmax,
    h.t_ctid,
    h.t_infomask,
    h.t_infomask2,
    f.raw_flags,
    f.combined_flags
FROM heap_page_items(get_raw_page('review', 0)) h
CROSS JOIN LATERAL heap_tuple_infomask_flags(h.t_infomask, h.t_infomask2) f;
```
<img width="1140" height="40" alt="Screenshot 2026-03-10 at 20 11 49" src="https://github.com/user-attachments/assets/8d896954-a258-4f91-9884-49f243eceb2e" />
<img width="1239" height="26" alt="Screenshot 2026-03-10 at 20 13 33" src="https://github.com/user-attachments/assets/f0da5404-6133-459c-bdc8-36ad665c89cd" />

raw_flags — это расшифровка битов поля t_infomask <br>
В примере: <br>
HEAP_HASNULL - в строке есть хотя бы одно поле со значением NULL <br>
HEAP_HASVARWIDTH - строка содержит поля переменной длины <br>
HEAP_XMIN_COMMITTED - транзакция, которая создала строку (xmin) уже зафиксирована (xmin = 843) <br>
HEAP_XMAX_INVALID - значение xmax невалидно, строка не была удалена или обновлена <br>
HEAP_UPDATED - эта строка появилась в результате UPDATE предыдущей версии строки <br>

## Посмотреть на параметры в разных транзакциях
### Сценарий 1. Незакоммиченный UPDATE <br>
Сессия 1:
```sql
BEGIN;

SELECT review_id, rating, comment, xmin, xmax, ctid
FROM review
WHERE comment = 'review updated #1';
```
<img width="508" height="67" alt="Screenshot 2026-03-10 at 20 41 27" src="https://github.com/user-attachments/assets/94619a69-7c00-4bf8-b153-8c464a126527" />

Теперь обновим: <br>
```sql
UPDATE review
SET comment = 'review updated #2'
WHERE comment = 'review updated #1';
```
Проверим в этой же транзакции:
```sql
SELECT review_id, rating, comment, xmin, xmax, ctid
FROM review
WHERE comment = 'review updated #2';
```
<img width="506" height="69" alt="Screenshot 2026-03-10 at 20 43 13" src="https://github.com/user-attachments/assets/eb247711-4c8f-4dea-bc0d-f56849103e34" />

Сессия 1 видит новую версию строки, созданную её же транзакцией. <br>

Сессия 2:
Пока Сессия 1 не сделала COMMIT, запускаем:
```sql
SELECT review_id, rating, comment, xmin, xmax, ctid
FROM review
WHERE review_id = 1;
```
<img width="508" height="67" alt="Screenshot 2026-03-10 at 20 45 58" src="https://github.com/user-attachments/assets/db661d93-22db-441d-b597-c9d1cbf1c361" />

Сессия 2 видит старую зафиксированную версию, то есть: <br>
ещё старый comment ("review updated #1") <br>
старый xmin ("843") <br>
есть xmax ("844") <br>
старый ctid ("(4060,24)") <br>
Это и есть работа MVCC: разные транзакции видят разные версии строки. <br>

Теперь в Сессии 1:
```sql
COMMIT;
```
После этого в Сессии 2 повторяем:

```sql
SELECT review_id, rating, comment, xmin, xmax, ctid
FROM review
WHERE review_id = 1;
```
<img width="507" height="67" alt="Screenshot 2026-03-10 at 20 49 28" src="https://github.com/user-attachments/assets/9a1e90c5-daa6-40f4-a2d9-5c6ebcb65b3a" /> <br>
Теперь Сессия 2 уже увидит новую версию строки.

### Сценарий 2. UPDATE и ROLLBACK

Сессия 1: <br>
Начинаем новую транзакцию:
```sql
BEGIN;

SELECT review_id, rating, comment, xmin, xmax, ctid
FROM review
WHERE review_id = 250202;
```
Сейчас строка имеет значение: comment = 'review updated #2', xmin = 844
<img width="506" height="69" alt="Screenshot 2026-03-10 at 20 57 11" src="https://github.com/user-attachments/assets/36de3bc4-8772-4270-8b45-7897c84dbca2" />
Теперь выполняем обновление:
```sql
UPDATE review
SET comment = 'review rollback test'
WHERE review_id = 250202;
```
Проверяем в этой же транзакции:
```sql
SELECT review_id, rating, comment, xmin, xmax, ctid
FROM review
WHERE review_id = 250202;
```
<img width="510" height="71" alt="Screenshot 2026-03-10 at 20 59 09" src="https://github.com/user-attachments/assets/6f7daaca-0cd8-4ac5-8b83-00aee22d4fdf" />

Результат в Сессии 1: <br>
comment = "review rollback test" <br>
появился новый xmin <br>
изменился ctid <br>
Это означает, что была создана новая версия строки внутри транзакции. <br>

Сессия 2: <br>
Пока Сессия 1 не сделала COMMIT, выполняем:
```sql
SELECT review_id, rating, comment, xmin, xmax, ctid
FROM review
WHERE review_id = 250202;
```
<img width="507" height="69" alt="Screenshot 2026-03-10 at 21 01 04" src="https://github.com/user-attachments/assets/28e59246-5b75-4b6a-948d-2b2e445dbd8e" />

Результат: <br>
comment = "review updated #2" <br>
старый xmin <br>
старый ctid <br>
То есть Сессия 2 не видит незакоммиченные изменения. <br>

Теперь в Сессии 1 <br>
Отменяем транзакцию:<br>
```sql
ROLLBACK;
```

Сессия 2<br>
Теперь снова выполняем:<br>
```sql
SELECT review_id, rating, comment, xmin, xmax, ctid
FROM review
WHERE review_id = 250202;
```
<img width="507" height="67" alt="Screenshot 2026-03-10 at 21 03 11" src="https://github.com/user-attachments/assets/4a801910-7d4d-48b5-a576-9d21708b3a69" />

Результат:<br>
comment остаётся "review updated #2"<br>
xmin и ctid не изменились<br>
Это означает, что обновление из Сессии 1 было полностью отменено.<br>

Вывод: Внутри транзакции была создана новая версия строки, но после выполнения ROLLBACK эта версия была удалена системой, так как транзакция не была зафиксирована. <br>
Другие транзакции продолжали видеть старую зафиксированную версию строки.


## Смоделировать дедлок, описать результаты
Сессия 1:
```sql
BEGIN;

UPDATE users
SET name = 'Deadlock User 1 - tx1'
WHERE user_id = 100002;
```
Сессия 2:
```sql
BEGIN;

UPDATE users
SET name = 'Deadlock User 2 - tx2'
WHERE user_id = 100003;
```
Теперь Сессия 2 держит блокировку строки user_id = 100003 <br>
Дальше выполняем:
```sql
UPDATE users
SET name = 'Deadlock User 1 - tx2'
WHERE user_id = 100002;
```
Этот запрос зависнет, потому что строку user_id = 10 уже держит Сессия 1.<br>

Теперь запускаем второй запрос из Сессии 1:
```sql
UPDATE users
SET name = 'Deadlock User 2 - tx1'
WHERE user_id = 100003;
```
Теперь получится цикл: <br>
Сессия 1 ждёт строку user_id = 11, которую держит Сессия 2 <br>
Сессия 2 ждёт строку user_id = 10, которую держит Сессия 1 <br>
PostgreSQL обнаружил deadlock между двумя транзакциями и принудительно прервал одну из них. <br>

<img width="647" height="208" alt="Screenshot 2026-03-10 at 21 22 55" src="https://github.com/user-attachments/assets/f64c76e0-1211-4784-9619-086d5747a84c" />

## Режимы блокировки на уровне строк – запросы и конфликты в разных транзакциях 

#### 1. Блокировка FOR UPDATE
Это самая сильная блокировка строки. <br>
Сессия 1:
```sql
BEGIN;

SELECT *
FROM users
WHERE user_id = 1
FOR UPDATE;
```
Теперь строка заблокирована для изменения. <br>
Сессия 2 <br>
Пробуем изменить строку:
```sql
UPDATE users
SET name = 'Blocked update'
WHERE user_id = 1;
```
<img width="998" height="246" alt="Screenshot 2026-03-10 at 21 43 34" src="https://github.com/user-attachments/assets/8062d3ac-1f1d-4e56-8ac8-abe75ac57604" />
Запрос завис и будет ждать завершения транзакции в Сессии 1.<br>
После commit в сессии 1, сессия 2:
<img width="958" height="132" alt="Screenshot 2026-03-10 at 21 44 51" src="https://github.com/user-attachments/assets/cb7cace1-dbef-4d6d-af74-b53997817782" />

#### 2. Блокировка FOR NO KEY UPDATE <br>
Эта блокировка чуть слабее, чем FOR UPDATE.<br>
Она не блокирует SELECT FOR KEY SHARE.<br>

Сессия 1:
```sql
BEGIN;

SELECT *
FROM users
WHERE user_id = 1
FOR NO KEY UPDATE;
```
Сессия 2:
```sql
SELECT *
FROM users
WHERE user_id = 1
FOR KEY SHARE;
```
<img width="583" height="66" alt="Screenshot 2026-03-10 at 21 47 13" src="https://github.com/user-attachments/assets/e8a7734d-ab16-4147-9d16-ac56893e71a9" /> <br>
Запрос выполнится, потому что FOR NO KEY UPDATE НЕ блокирует FOR KEY SHARE<br2>
Но если выполнить:
```sql
UPDATE users
SET name = 'test'
WHERE user_id = 1;
```
<img width="1270" height="321" alt="Screenshot 2026-03-10 at 21 50 12" src="https://github.com/user-attachments/assets/f65a64a2-2640-4009-9759-4fd3f0734ed1" />
Тогда запрос будет ждать.

#### 3. Блокировка FOR SHARE
Это разделяемая блокировка строки.<br>
Она позволяет другим транзакциям читать строку с FOR SHARE, но запрещает её изменять.<br>
Сессия 1:
```sql
BEGIN;

SELECT *
FROM users
WHERE user_id = 1
FOR SHARE;
```

Сессия 2<br2>
Попробуем тоже взять разделяемую блокировку:<br>
```sql
SELECT *
FROM users
WHERE user_id = 1
FOR SHARE;
```
Запрос выполнится. Разделяемые блокировки совместимы. <br>
<img width="534" height="64" alt="Screenshot 2026-03-10 at 21 53 07" src="https://github.com/user-attachments/assets/74f25ac7-e748-438c-bec8-e03db45c1e5d" /> <br>

Но если выполнить:
```sql
UPDATE users
SET name = 'test'
WHERE user_id = 1;
```
<img width="1269" height="254" alt="Screenshot 2026-03-10 at 21 53 56" src="https://github.com/user-attachments/assets/d5b745d0-6af9-41d1-8d83-9ba10146dc03" />
Запрос будет ждать.

#### 4. Блокировка FOR KEY SHARE

Это самая слабая блокировка строки. <br>
Она защищает ключ строки, но позволяет обычные UPDATE.<br>

Сессия 1:
```sql
BEGIN;

SELECT *
FROM users
WHERE user_id = 1
FOR KEY SHARE;
```
Сессия 2:
```sql
UPDATE users
SET name = 'Allowed update'
WHERE user_id = 1;
```
<img width="375" height="83" alt="Screenshot 2026-03-10 at 21 57 16" src="https://github.com/user-attachments/assets/64108ab9-07c6-4ff9-ae33-fa026db17d9a" /> <br>

Запрос выполнится, потому что FOR KEY SHARE разрешает обычные UPDATE<br>

Но если попытаться удалить строку
```sql
DELETE FROM users
WHERE user_id = 1;
```
<img width="1268" height="280" alt="Screenshot 2026-03-10 at 21 58 28" src="https://github.com/user-attachments/assets/0ba88ce7-fdbf-4a5a-bce5-2a41a29a997f" />
Запрос будет ждать.

