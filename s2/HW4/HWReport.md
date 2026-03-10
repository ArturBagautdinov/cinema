## Смоделировать обновление данных и посмотреть на параметры xmin, xmax, ctid, t_infomask; Понять что хранится в t_infomask


Тестовые данные

```sql

INSERT INTO review(user_id, movie_id, rating, comment)
SELECT u.user_id, m.movie_id, 7, 'initial review'
FROM users u
JOIN movie m ON m.title = 'Test Movie'
WHERE u.email = 'test_user@example.com'
ON CONFLICT (user_id, movie_id) DO NOTHING;

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


