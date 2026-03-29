## Секционирование: RANGE / LIST / HASH

### 1) RANGE
Секционируем viewing по viewing_date

```sql
DROP TABLE IF EXISTS viewing_range CASCADE;

CREATE TABLE viewing_range
(
    viewing_id   BIGINT,
    user_id      BIGINT      NOT NULL,
    movie_id     BIGINT      NOT NULL,
    viewing_date TIMESTAMPTZ NOT NULL,
    progress     INT         NOT NULL,
    device       TEXT,
    session_id   UUID        NOT NULL,
    tags         TEXT[],
    meta         JSONB,
    geo          POINT
) PARTITION BY RANGE (viewing_date);

CREATE TABLE viewing_range_2025_03 PARTITION OF viewing_range
    FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');

CREATE TABLE viewing_range_2025_04 PARTITION OF viewing_range
    FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');

CREATE TABLE viewing_range_2025_05 PARTITION OF viewing_range
    FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');

CREATE TABLE viewing_range_2025_06 PARTITION OF viewing_range
    FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');

CREATE TABLE viewing_range_2025_07 PARTITION OF viewing_range
    FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');

CREATE TABLE viewing_range_2025_08 PARTITION OF viewing_range
    FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');

CREATE TABLE viewing_range_2025_09 PARTITION OF viewing_range
    FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');

CREATE TABLE viewing_range_2025_10 PARTITION OF viewing_range
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');

CREATE TABLE viewing_range_2025_11 PARTITION OF viewing_range
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');

CREATE TABLE viewing_range_2025_12 PARTITION OF viewing_range
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

CREATE TABLE viewing_range_2026_01 PARTITION OF viewing_range
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE viewing_range_2026_02 PARTITION OF viewing_range
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

CREATE TABLE viewing_range_2026_03 PARTITION OF viewing_range
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

CREATE INDEX idx_viewing_range_viewing_date ON viewing_range (viewing_date);

INSERT INTO viewing_range
SELECT
    viewing_id, user_id, movie_id, viewing_date, progress,
    device, session_id, tags, meta, geo
FROM viewing;
```
<img width="640" height="604" alt="Screenshot 2026-03-29 at 22 16 46" src="https://github.com/user-attachments/assets/44400758-e038-47e6-bd30-2233204dae72" /> <br>

Запрос

```sql
EXPLAIN (ANALYZE, VERBOSE, COSTS OFF)
SELECT viewing_id, user_id, movie_id
FROM viewing_range
WHERE viewing_date >= TIMESTAMPTZ '2025-11-10 00:00:00+00'
  AND viewing_date <  TIMESTAMPTZ '2025-11-11 00:00:00+00';
```
<img width="937" height="269" alt="Screenshot 2026-03-29 at 22 33 42" src="https://github.com/user-attachments/assets/c0e31834-7922-45e0-bfde-0f12ea46e14c" />

Partition pruning есть - Bitmap Heap Scan on public.viewing_range_2025_11 viewing_range <br>
Используется только одна партиция - viewing_range_2025_11 <br>
Индекс используется -  Bitmap Index Scan on viewing_range_2025_11_viewing_date_idx

### 2) LIST
Секционируем viewing по device
```sql
DROP TABLE IF EXISTS viewing_list CASCADE;

CREATE TABLE viewing_list
(
    viewing_id   BIGINT,
    user_id      BIGINT      NOT NULL,
    movie_id     BIGINT      NOT NULL,
    viewing_date TIMESTAMPTZ NOT NULL,
    progress     INT         NOT NULL,
    device       TEXT,
    session_id   UUID        NOT NULL,
    tags         TEXT[],
    meta         JSONB,
    geo          POINT
) PARTITION BY LIST (device);

CREATE TABLE viewing_list_tv PARTITION OF viewing_list
    FOR VALUES IN ('TV');

CREATE TABLE viewing_list_mobile PARTITION OF viewing_list
    FOR VALUES IN ('MOBILE');

CREATE TABLE viewing_list_tablet PARTITION OF viewing_list
    FOR VALUES IN ('TABLET');

CREATE TABLE viewing_list_web PARTITION OF viewing_list
    FOR VALUES IN ('WEB');

CREATE TABLE viewing_list_console PARTITION OF viewing_list
    FOR VALUES IN ('CONSOLE');

CREATE TABLE viewing_list_default PARTITION OF viewing_list
    DEFAULT;

CREATE INDEX idx_viewing_list_date ON viewing_list (viewing_date DESC);

INSERT INTO viewing_list
SELECT
    viewing_id, user_id, movie_id, viewing_date, progress,
    device, session_id, tags, meta, geo
FROM viewing;
```
<img width="610" height="537" alt="Screenshot 2026-03-29 at 22 24 38" src="https://github.com/user-attachments/assets/41a2c53b-08f1-4fe1-8bc9-bbc3af93d2f2" /> <br>

Запрос

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT viewing_id, user_id, viewing_date
FROM viewing_list
WHERE device = 'TV'
  AND viewing_date >= now() - interval '1 day'
ORDER BY viewing_date DESC
LIMIT 50;
```
<img width="947" height="293" alt="Screenshot 2026-03-29 at 22 32 29" src="https://github.com/user-attachments/assets/04ea40b7-e377-4ead-a620-95999795148a" />

Partition pruning есть - Index Scan using viewing_list_tv_viewing_date_idx on public.viewing_list_tv viewing_list <br>
Используется только одна партиция - viewing_list_tv <br>
Индекс используется - Index Scan using viewing_list_tv_viewing_date_idx

### 3) HASH
Секционируем purchase по user_id

```sql
DROP TABLE IF EXISTS purchase_hash CASCADE;

CREATE TABLE purchase_hash
(
    purchase_id   BIGINT,
    user_id       BIGINT         NOT NULL,
    movie_id      BIGINT         NOT NULL,
    purchase_date TIMESTAMPTZ    NOT NULL,
    price         NUMERIC(10, 2) NOT NULL,
    method_id     BIGINT         NOT NULL,
    coupon_code   TEXT,
    payment_meta  JSONB,
    amount_range  INT4RANGE
) PARTITION BY HASH (user_id);

CREATE TABLE purchase_hash_p0 PARTITION OF purchase_hash
    FOR VALUES WITH (MODULUS 8, REMAINDER 0);
CREATE TABLE purchase_hash_p1 PARTITION OF purchase_hash
    FOR VALUES WITH (MODULUS 8, REMAINDER 1);
CREATE TABLE purchase_hash_p2 PARTITION OF purchase_hash
    FOR VALUES WITH (MODULUS 8, REMAINDER 2);
CREATE TABLE purchase_hash_p3 PARTITION OF purchase_hash
    FOR VALUES WITH (MODULUS 8, REMAINDER 3);
CREATE TABLE purchase_hash_p4 PARTITION OF purchase_hash
    FOR VALUES WITH (MODULUS 8, REMAINDER 4);
CREATE TABLE purchase_hash_p5 PARTITION OF purchase_hash
    FOR VALUES WITH (MODULUS 8, REMAINDER 5);
CREATE TABLE purchase_hash_p6 PARTITION OF purchase_hash
    FOR VALUES WITH (MODULUS 8, REMAINDER 6);
CREATE TABLE purchase_hash_p7 PARTITION OF purchase_hash
    FOR VALUES WITH (MODULUS 8, REMAINDER 7);

CREATE INDEX idx_purchase_hash_user_date
    ON purchase_hash (user_id, purchase_date DESC);

INSERT INTO purchase_hash
SELECT
    purchase_id, user_id, movie_id, purchase_date,
    price, method_id, coupon_code, payment_meta, amount_range
FROM purchase;
```
<img width="586" height="573" alt="Screenshot 2026-03-29 at 22 29 09" src="https://github.com/user-attachments/assets/82323ef4-147f-4e9e-9a08-c1f9eaa50328" /> <br>

Запрос

```sql
EXPLAIN (ANALYZE, VERBOSE, COSTS OFF)
SELECT purchase_id, purchase_date, price
FROM purchase_hash
WHERE user_id = 42
ORDER BY purchase_date DESC
LIMIT 20;
```
<img width="1103" height="269" alt="Screenshot 2026-03-29 at 22 35 37" src="https://github.com/user-attachments/assets/4705c019-e5cf-44d8-8935-3d272ce48b6a" />

Partition pruning есть - Index Scan using purchase_hash_p2_user_id_purchase_date_idx on purchase_hash_p2 purchase_hash <br>
Используется только одна партиция - purchase_hash_p2 <br>
Индекс используется - Index Scan using purchase_hash_p2_user_id_purchase_date_idx

## Секционирование и физическая репликация

### Проверить что секционирование есть на репликах

Проверить на primary, что viewing_range — partitioned table
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "SELECT relname, relkind FROM pg_class WHERE relname = 'viewing_range';"
```
<img width="1249" height="84" alt="Screenshot 2026-03-29 at 23 00 04" src="https://github.com/user-attachments/assets/e6030177-43cb-4998-a162-9cf34d47e95d" />

Проверить то же на replica1
```bash
docker exec -it PgReplica1 psql -U postgres -d CinemaMigr -c "SELECT relname, relkind FROM pg_class WHERE relname = 'viewing_range';"
```
<img width="1273" height="87" alt="Screenshot 2026-03-29 at 23 01 37" src="https://github.com/user-attachments/assets/7a246513-ccf6-4dd7-be2e-24444c63bf89" />

Проверить то же на replica2
```bash
docker exec -it PgReplica2 psql -U postgres -d CinemaMigr -c "SELECT relname, relkind FROM pg_class WHERE relname = 'viewing_range';"
```
<img width="1265" height="87" alt="Screenshot 2026-03-29 at 23 02 30" src="https://github.com/user-attachments/assets/c397c44a-e134-4497-be0c-4d487d45846a" />

relkind = p означает, что это partitioned table. <br>

Проверить список партиций на primary

```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "SELECT inhrelid::regclass AS partition FROM pg_inherits WHERE inhparent = 'viewing_range'::regclass ORDER BY inhrelid::regclass::text;"
```
<img width="1491" height="267" alt="Screenshot 2026-03-29 at 23 08 19" src="https://github.com/user-attachments/assets/8b8b1f93-abb4-4f09-9980-2f1c2478ecf8" />

Проверить список партиций на replica1/replica2

```bash
docker exec -it PgReplica1 psql -U postgres -d CinemaMigr -c "SELECT inhrelid::regclass AS partition FROM pg_inherits WHERE inhparent = 'viewing_range'::regclass ORDER BY inhrelid::regclass::text;"
```
<img width="1512" height="273" alt="Screenshot 2026-03-29 at 23 09 16" src="https://github.com/user-attachments/assets/8788d50b-b316-4a19-b687-bbc7342b8ed6" />
Результат показывает одинаковые секции на primary и на replica - секционирование на реплике присутствует.

### Почему репликация “не знает” про секции?

Физическая репликация в PostgreSQL работает на уровне WAL (Write-Ahead Log) и физических страниц данных, а не на уровне SQL-логики.
Primary-сервер выполняет все операции с таблицами и сам определяет, в какую партицию должна попасть строка (partition routing). После этого в WAL записываются уже конкретные изменения файлов таблиц и индексов.
Standby-сервер (реплика) просто воспроизводит записи WAL, повторяя изменения страниц данных. Она не анализирует SQL-запросы и не выполняет повторную маршрутизацию по ключу секционирования.
Поэтому говорят, что физическая репликация “не знает” про секции:
она копирует физические изменения базы данных, а логика секционирования выполняется только на primary.

## Логическая репликация и секционирование publish_via_partition_root = on / off

Подготовка таблицы на logical subscriber - здесь делаем такую же секционированную таблицу на PgLogicalSub:
```bash
docker exec -i PgLogicalSub psql -U postgres -d CinemaMigr <<'SQL'
DROP TABLE IF EXISTS viewing_range CASCADE;

CREATE TABLE viewing_range
(
    viewing_id   BIGINT,
    user_id      BIGINT      NOT NULL,
    movie_id     BIGINT      NOT NULL,
    viewing_date TIMESTAMPTZ NOT NULL,
    progress     INT         NOT NULL,
    device       TEXT,
    session_id   UUID        NOT NULL,
    tags         TEXT[],
    meta         JSONB,
    geo          POINT
) PARTITION BY RANGE (viewing_date);

CREATE TABLE viewing_range_2025_03 PARTITION OF viewing_range
FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');
CREATE TABLE viewing_range_2025_04 PARTITION OF viewing_range
FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');
CREATE TABLE viewing_range_2025_05 PARTITION OF viewing_range
FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
CREATE TABLE viewing_range_2025_06 PARTITION OF viewing_range
FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');
CREATE TABLE viewing_range_2025_07 PARTITION OF viewing_range
FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');
CREATE TABLE viewing_range_2025_08 PARTITION OF viewing_range
FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');
CREATE TABLE viewing_range_2025_09 PARTITION OF viewing_range
FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');
CREATE TABLE viewing_range_2025_10 PARTITION OF viewing_range
FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
CREATE TABLE viewing_range_2025_11 PARTITION OF viewing_range
FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
CREATE TABLE viewing_range_2025_12 PARTITION OF viewing_range
FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');
CREATE TABLE viewing_range_2026_01 PARTITION OF viewing_range
FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE viewing_range_2026_02 PARTITION OF viewing_range
FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE viewing_range_2026_03 PARTITION OF viewing_range
FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
SQL
```
### Создать publication с publish_via_partition_root = off
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "CREATE PUBLICATION pub_viewing_off FOR TABLE viewing_range WITH (publish_via_partition_root = false);"
```
Создать subscription
```bash
docker exec -it PgLogicalSub psql -U postgres -d CinemaMigr -c "CREATE SUBSCRIPTION sub_viewing_off CONNECTION 'host=postgres-primary port=5432 dbname=CinemaMigr user=postgres password=postgres' PUBLICATION pub_viewing_off WITH (copy_data = false);"
```

Вставить тестовую строку на publisher
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "INSERT INTO viewing_range (viewing_id, user_id, movie_id, viewing_date, progress, device, session_id) VALUES (700000001, 1, 1, '2025-11-10 12:00:00+00', 80, 'TV', gen_random_uuid());"
```

Проверить на subscriber
```bash
docker exec -it PgLogicalSub psql -U postgres -d CinemaMigr -c "SELECT tableoid::regclass AS actual_table, viewing_id, user_id, movie_id, viewing_date FROM viewing_range WHERE viewing_id = 700000001;"
```
<img width="1512" height="101" alt="Screenshot 2026-03-29 at 23 25 49" src="https://github.com/user-attachments/assets/20195507-e2c6-47e3-9275-cf7daaeb0821" />

При publish_via_partition_root = off изменения секционированной таблицы передаются подписчику от имени конкретных партиций (actual_table = viewing_range_2025_11). Поэтому на подписчике должны существовать соответствующие таблицы/секции. В примере строка с датой 2025-11-10 была применена в секцию viewing_range_2025_11

### publication с publish_via_partition_root = on

На subscriber создаем обычную таблицу viewing_range

``` bash
docker exec -i PgLogicalSub psql -U postgres -d CinemaMigr <<'SQL'
DROP TABLE IF EXISTS viewing_range CASCADE;

CREATE TABLE viewing_range
(
    viewing_id   BIGINT,
    user_id      BIGINT      NOT NULL,
    movie_id     BIGINT      NOT NULL,
    viewing_date TIMESTAMPTZ NOT NULL,
    progress     INT         NOT NULL,
    device       TEXT,
    session_id   UUID        NOT NULL,
    tags         TEXT[],
    meta         JSONB,
    geo          POINT
);
SQL
```

Создать publication с on
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "CREATE PUBLICATION pub_viewing_on FOR TABLE viewing_range WITH (publish_via_partition_root = true);"
```

Создать Subscription
```bash
docker exec -it PgLogicalSub psql -U postgres -d CinemaMigr -c "CREATE SUBSCRIPTION sub_viewing_on CONNECTION 'host=postgres-primary port=5432 dbname=CinemaMigr user=postgres password=postgres' PUBLICATION pub_viewing_on WITH (copy_data = false);"
```

Вставить тестовую строку на Publisher
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "INSERT INTO viewing_range (viewing_id, user_id, movie_id, viewing_date, progress, device, session_id) VALUES (700000002, 2, 2, '2025-11-15 18:30:00+00', 55, 'WEB', gen_random_uuid());"
```
Проверить у Subscriber
```bash
docker exec -it PgLogicalSub psql -U postgres -d CinemaMigr -c "SELECT viewing_id, user_id, movie_id, viewing_date, device FROM viewing_range WHERE viewing_id = 700000002;"
```
<img width="1511" height="96" alt="Screenshot 2026-03-29 at 23 34 49" src="https://github.com/user-attachments/assets/1a851468-24ae-4a48-bcd5-92709689ca65" />

При publish_via_partition_root = on изменения секционированной таблицы передаются подписчику через корневую таблицу, а не через отдельные секции. Поэтому на подписчике структура может отличаться: в примере на publisher использовалась секционированная таблица viewing_range, а на subscriber данные успешно применялись в обычную таблицу viewing_range без секционирования.

