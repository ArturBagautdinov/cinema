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
