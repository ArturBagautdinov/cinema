## задание 1
план до изменений:
<img width="968" height="243" alt="Screenshot 2026-04-01 at 09 11 56" src="https://github.com/user-attachments/assets/34342b04-6c7f-4137-bc5b-a764372503d0" />
Seq Scan по таблице exam_events.
Если бы были индексы idx_exam_events_status и idx_exam_events_amount_hash этому запросу не помогли бы, потому что фильтрация идет по user_id и created_at, а не по status и amount.
Полный просмотр таблицы выбирается потому, что среди имеющихся индексов нет подходящего пути для быстрого отбора нужных строк по обоим условиям.

Лучше всего под такой запрос подходит составной B-tree индекс:
```sql
CREATE INDEX idx_exam_events_user_id_created_at
    ON exam_events (user_id, created_at);
```
<img width="1014" height="220" alt="Screenshot 2026-04-01 at 09 14 30" src="https://github.com/user-attachments/assets/8e78afad-6bcf-4db2-99be-021bb70d215c" />
После создания индекса план обычно меняется с Seq Scan на Index Scan
Это происходит, тк появился индекс, который соответствует и условию равенства по user_id, и диапазону по created_at.
читается не вся таблица, а только небольшой диапазон индексных записей и соответствующие страницы данных.

нужно ли после создания индекса выполнять ANALYZE, и зачем.
После создания индекса ANALYZE желательно выполнить, чтобы планировщик обновил статистику и точнее оценивал селективность условий.
Хотя PostgreSQL может использовать индекс и без этого, свежая статистика повышает шанс выбора оптимального плана.


## Задание 2
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT u.id, u.country, o.amount, o.created_at
FROM exam_users u
JOIN exam_orders o ON o.user_id = u.id
WHERE u.country = 'JP'
  AND o.created_at >= TIMESTAMP '2025-03-01 00:00:00'
  AND o.created_at < TIMESTAMP '2025-03-08 00:00:00';
```
исходный
<img width="922" height="572" alt="Screenshot 2026-04-01 at 09 19 18" src="https://github.com/user-attachments/assets/58b2d2b6-8fc5-4341-939b-4c90381429ea" />
Hash Join - тк это эффективно, когда одна сторона соединения после фильтра сравнительно небольшая, а соединение идет по равенству o.user_id = u.id.
Фильтр u.country = 'JP' оставляет только часть строк из exam_users, и из них удобно построить хеш-таблицу.

Полезен частично:
- idx_exam_orders_created_at — помогает только по фильтру даты, но не по соединению user_id.

Слабо полезен / не полезен:
- idx_exam_users_name — в запросе нет фильтрации по name.

улучшение — составной индекс на таблице заказов:
```sql
CREATE INDEX idx_exam_orders_created_at_user_id
    ON exam_orders (created_at, user_id);
```

<img width="925" height="572" alt="Screenshot 2026-04-01 at 09 22 54" src="https://github.com/user-attachments/assets/8f9b18dd-a08f-4ce5-ab16-b57eedfe11f8" />
Такой индекс помогает сразу и отбирать строки по диапазону даты, и быстрее получать user_id, нужный для соединения.
Это уменьшает объем строк, который надо читать из exam_orders, и делает вход для JOIN дешевле.
Но в данном случае postgres все равно выбрал idx_exam_orders_created_at, он решил это эффективнее
В моем случае план не улучшился 
После создания нового индекса он мог улучшится за счет более дешевого доступа к exam_orders: вместо менее точного чтения по дате или полного прохода читается более узкий набор строк.

shared hit означает, что нужные страницы уже были в буферном кеше PostgreSQL и читались из памяти.
shared read означает, что страницы пришлось читать с диска в буферы.
Преобладание hit обычно говорит о более горячем кеше, а преобладание read — о более дорогом чтении с диска.

## Задание 3.
исходное
```sql
SELECT xmin, xmax, ctid, id, title, qty
FROM exam_mvcc_items
ORDER BY id;
```
<img width="437" height="119" alt="Screenshot 2026-04-01 at 09 30 32" src="https://github.com/user-attachments/assets/230c2bdb-5728-4601-9b11-7e2ba8ff9fcc" />

update
```sql
UPDATE exam_mvcc_items
SET qty = qty + 5
WHERE id = 1;
```

проверка
```sql
SELECT xmin, xmax, ctid, id, title, qty
FROM exam_mvcc_items<img width="437" height="120" alt="Screenshot 2026-04-01 at 09 32 57" src="https://github.com/user-attachments/assets/0d9f4f3d-876c-4d42-9142-0fcb4586ea85" />

ORDER BY id;
```
<img width="438" height="118" alt="Screenshot 2026-04-01 at 09 33 14" src="https://github.com/user-attachments/assets/7ef441a8-ba94-45be-a4fb-c891b3aa1361" />

delete
```sql
DELETE FROM exam_mvcc_items
WHERE id = 2;
```
<img width="440" height="92" alt="Screenshot 2026-04-01 at 09 33 48" src="https://github.com/user-attachments/assets/916f88ad-88c1-44f1-b6c1-08dffa913b1e" />

После UPDATE PostgreSQL не перезаписывает строку “на месте”, а создает новую версию строки.
У старой версии заполняется xmax идентификатором транзакции, которая ее сделала неактуальной, а у новой версии появляется новый xmin.
ctid тоже поменялся, потому что новая версия строки физически записывается как другой кортеж.
После DELETE строка версия помечается как удаленная через xmax.
Обычный SELECT больше ее не показывает, потому что для текущего снимка транзакции эта строка уже считается невидимой.

VACUUM очищает мертвые версии строк и помечает место как доступное для повторного использования, обычно без полной перестройки таблицы.
autovacuum делает то же самое автоматически по внутренним порогам активности, чтобы таблицы не раздувались и не устаревала статистика.
VACUUM FULL переписывает таблицу целиком в новый компактный файл и реально возвращает место операционной системе, но работает намного тяжелее.
Полностью блокировать таблицу может VACUUM FULL.

## Задание 4.

первый

<img width="552" height="353" alt="Screenshot 2026-04-01 at 09 38 05" src="https://github.com/user-attachments/assets/2c88023b-7751-4e3d-85f7-49716c10b925" />
<img width="943" height="569" alt="Screenshot 2026-04-01 at 09 38 33" src="https://github.com/user-attachments/assets/8bed6b6b-397f-46cb-a5b9-9bf624bcad05" />

<img width="487" height="351" alt="Screenshot 2026-04-01 at 09 38 56" src="https://github.com/user-attachments/assets/5480446e-431f-4c62-87e6-e7e5201c095d" />
<img width="558" height="379" alt="Screenshot 2026-04-01 at 09 39 05" src="https://github.com/user-attachments/assets/2c1b1bd1-1d66-4c21-b80f-4827307cacb0" />

второй

<img width="604" height="362" alt="Screenshot 2026-04-01 at 09 39 49" src="https://github.com/user-attachments/assets/43225139-edcf-4592-af23-1122b906d569" />
<img width="871" height="664" alt="Screenshot 2026-04-01 at 09 40 16" src="https://github.com/user-attachments/assets/1eaf1f15-b72a-484b-b5ee-2687ffd0a7c5" />

<img width="512" height="388" alt="Screenshot 2026-04-01 at 09 40 39" src="https://github.com/user-attachments/assets/42605c4c-2f4b-4141-85ab-a1c4a4a1f4ba" />

<img width="501" height="393" alt="Screenshot 2026-04-01 at 09 41 06" src="https://github.com/user-attachments/assets/f1bcd6d9-541f-4e5b-a8aa-7f0d46f0cc25" />

В первом эксперименте (FOR SHARE) запрос UPDATE в сессии B будет ждать освобождения блокировки, потому что обновление требует более сильной блокировки строки и конфликтует с FOR SHARE.
Во втором эксперименте (FOR UPDATE) UPDATE в B тоже будет ждать,блокировка еще сильнее - предназначена для защиты строки перед возможным изменением.
После ROLLBACK в сессии A ожидание завершается, и UPDATE в B может продолжиться.

FOR SHARE — это более слабая блокировка: она позволяет другим транзакциям тоже брать совместимые блокировки чтения, но не дает изменять строку.
FOR UPDATE — более сильная блокировка, которая используется, когда строку собираются изменять или нужно гарантированно запретить конкурентное изменение.

Почему обычный SELECT ведет себя иначе
Обычный SELECT в PostgreSQL не ставит блокировку строки такого уровня, потому что чтение по MVCC обычно не мешает конкурентным изменениям.
Он читает подходящую версию строки из своего снимка транзакции и не блокирует UPDATE так, как это делают FOR SHARE и FOR UPDATE.

FOR UPDATE полезен тогда, когда нужно безопасно прочитать строку и затем изменить ее без гонки данных.
Например, при списании денег, бронировании товара на складе.
То есть он нужен там, где нельзя допустить, чтобы две транзакции одновременно приняли решение на основе одной и той же строки.


## Задание 5

секционированная таюлица
```sql
CREATE TABLE exam_measurements (
    city_id INTEGER NOT NULL,
    log_date DATE NOT NULL,
    peaktemp INTEGER,
    unitsales INTEGER
) PARTITION BY RANGE (log_date);
```
<img width="425" height="372" alt="Screenshot 2026-04-01 at 09 45 15" src="https://github.com/user-attachments/assets/23c0a7ca-fe3a-472e-8643-610c5af4dfa7" />

секции

```sql
CREATE TABLE exam_measurements_2025_01
PARTITION OF exam_measurements
FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE exam_measurements_2025_02
PARTITION OF exam_measurements
FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');

CREATE TABLE exam_measurements_2025_03
PARTITION OF exam_measurements
FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');

CREATE TABLE exam_measurements_default
PARTITION OF exam_measurements
DEFAULT;
```

<img width="576" height="521" alt="Screenshot 2026-04-01 at 09 45 46" src="https://github.com/user-attachments/assets/d5e488f9-f6f3-49d2-9111-9b51298bce11" />
перенос данных:

```sql
INSERT INTO exam_measurements (city_id, log_date, peaktemp, unitsales)
SELECT city_id, log_date, peaktemp, unitsales
FROM exam_measurements_src;
```
<img width="770" height="602" alt="Screenshot 2026-04-01 at 09 46 27" src="https://github.com/user-attachments/assets/548b1129-efd8-467d-9517-611995c2d634" />

```sql
ANALYZE exam_measurements;
```

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT city_id, log_date, unitsales
FROM exam_measurements
WHERE log_date >= DATE '2025-02-01'
  AND log_date < DATE '2025-03-01';
```
<img width="920" height="507" alt="Screenshot 2026-04-01 at 09 47 28" src="https://github.com/user-attachments/assets/45864ab5-fd29-4b32-9d43-59720657f068" />
Здесь partition pruning есть.
Поскольку условие наложено прямо на ключ секционирования log_date, планировщик может заранее определить, что нужна только февральская секция.
В плане участвует 1 секция: exam_measurements_2025_02.

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT city_id, log_date, unitsales
FROM exam_measurements
WHERE city_id = 10;
```
<img width="892" height="595" alt="Screenshot 2026-04-01 at 09 49 27" src="https://github.com/user-attachments/assets/5ea7fe4a-2d8f-4463-aee7-2a85debf8281" />

Здесь partition pruning нет, потому что фильтрация идет не по ключу секционирования, а по city_id.
Планировщик не может заранее отбросить секции по диапазонам дат, если в условии нет ограничения по log_date.
в планн участвуют все секции: январь, февраль, март и DEFAULT - 4 секции.

Pruning работает, когда условие запроса может сопоставить предикат с секциями.
Здесь секции разбиты по log_date, поэтому условие по дате позволяет оставить только нужный диапазон.
Условие по city_id с границами секций не связано, поэтому сервер обязан проверять все секции.

И нет, partition pruning напрямую не зависит от обычного индекса.
Он основан на самой схеме секционирования и на том, можно ли по условию запроса понять, какие секции не подходят.
Индексы тольео могут ускорить чтение внутри уже выбранных секций


Секция DEFAULT нужна для строк, которые не попадут ни в один явно описанный диапазон.
В этом наборе данных попадают строки за апрель 2025, потому что отдельной апрельской секции нет.
Без DEFAULT при вставке была бы ошибка .
