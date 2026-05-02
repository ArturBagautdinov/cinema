# Домашнее задание ClickHouse

## Задание 1

```
	CREATE TABLE web_logs (
    log_time DateTime,
    ip String,
    url String,
    status_code UInt16,
    response_size UInt64
	) ENGINE = MergeTree()
	ORDER BY (log_time, status_code);
```

```
	INSERT INTO web_logs
	SELECT
    toDateTime('2024-03-01 00:00:00') + INTERVAL number SECOND,
    concat('192.168.0.', toString(number % 50)),
    arrayElement(['/home', '/api/users', '/api/orders', '/admin', '/products'], number % 5 + 1),
    arrayElement([200, 200, 200, 404, 500, 301, 200], number % 7 + 1),
    rand() % 1000000
	FROM numbers(500000);
```

### 1. Найдите топ-10 IP-адресов по количеству запросов.

```sql
SELECT
    ip,
    count() AS requests_count
FROM web_logs
GROUP BY ip
ORDER BY requests_count DESC
LIMIT 10;
```

<img width="1420" height="518" alt="Screenshot 2026-05-02 at 19 55 40" src="https://github.com/user-attachments/assets/f859e1b1-37d1-43b8-a248-eb4c68ce0e24" />

### 2. Посчитайте процент успешных запросов (2xx) и ошибочных (4xx, 5xx).

```sql
SELECT
    round(countIf(status_code BETWEEN 200 AND 299) / count() * 100, 2) AS success_2xx_percent,
    round(countIf(status_code BETWEEN 400 AND 599) / count() * 100, 2) AS error_4xx_5xx_percent,
    round(countIf(status_code BETWEEN 400 AND 499) / count() * 100, 2) AS error_4xx_percent,
    round(countIf(status_code BETWEEN 500 AND 599) / count() * 100, 2) AS error_5xx_percent
FROM web_logs;
```

<img width="854" height="261" alt="Screenshot 2026-05-02 at 19 56 59" src="https://github.com/user-attachments/assets/25b5f33f-dc86-4541-8918-c550afb10265" />

### 3. Найдите самый популярный URL и средний размер ответа для него.

```sql
SELECT
    url,
    count() AS requests_count,
    round(avg(response_size), 2) AS avg_response_size
FROM web_logs
GROUP BY url
ORDER BY requests_count DESC
LIMIT 1;
```

<img width="789" height="293" alt="Screenshot 2026-05-02 at 19 57 44" src="https://github.com/user-attachments/assets/a0e7984e-dd60-428a-b953-e3f7c91a16af" />

### 4. Определите час с наибольшим количеством ошибок 500.

```sql
SELECT
    toStartOfHour(log_time) AS hour,
    count() AS errors_500_count
FROM web_logs
WHERE status_code = 500
GROUP BY hour
ORDER BY errors_500_count DESC
LIMIT 1;
```

<img width="820" height="289" alt="Screenshot 2026-05-02 at 19 59 24" src="https://github.com/user-attachments/assets/dcb40276-f394-4c2b-9a5c-4017a2bfaa3b" />


## Задание 2

### Сравнение с PostgreSQL

```
	CREATE TABLE sales_ch (
    sale_date DateTime,
    product_id UInt64,
    category String,
    quantity UInt32,
    price Float64,
    customer_id UInt64
	) ENGINE = MergeTree()
	ORDER BY (sale_date);

	INSERT INTO sales_ch
	SELECT
    toDateTime('2024-01-01 00:00:00') + INTERVAL number MINUTE,
    number % 1000,
    arrayElement(['Electronics', 'Clothing', 'Food', 'Books'], number % 4 + 1),
    rand() % 10 + 1,
    round(rand() % 10000 / 100, 2),
    number % 50000
	FROM numbers(1000000);
```

```
	CREATE TABLE sales_pg (
    sale_date timestamp,
    product_id bigint,
    category text,
    quantity integer,
    price float8,
    customer_id bigint
	);

	CREATE INDEX idx_sales_pg_date ON sales_pg(sale_date);
	CREATE INDEX idx_sales_pg_product ON sales_pg(product_id);

	INSERT INTO sales_pg
	SELECT
    '2024-01-01 00:00:00'::timestamp + (n || ' minutes')::interval,
    n % 1000,
    CASE (n % 4)
        WHEN 0 THEN 'Electronics'
        WHEN 1 THEN 'Clothing'
        WHEN 2 THEN 'Food'
        ELSE 'Books'
    END,
    (random() * 9 + 1)::integer,
    round((random() * 100)::numeric, 2),
    n % 50000
	FROM generate_series(1, 1000000) AS n;
```

### Замер вставки 1 млн строк в ClickHouse

```bash
time docker exec -i clickhouse-lab clickhouse-client --password password --query "
INSERT INTO sales_ch
SELECT
    toDateTime('2024-01-01 00:00:00') + INTERVAL number MINUTE,
    number % 1000,
    arrayElement(['Electronics', 'Clothing', 'Food', 'Books'], number % 4 + 1),
    rand() % 10 + 1,
    round(rand() % 10000 / 100, 2),
    number % 50000
FROM numbers(1000000);
"
```

<img width="981" height="204" alt="Screenshot 2026-05-02 at 20 06 06" src="https://github.com/user-attachments/assets/fca1644b-3020-4f94-bcc0-8242bc224cbe" />

### Замер вставки 1 млн строк в PostgreSQL

```bash
time docker exec -i postgres-lab psql -U postgres -d postgres -c "
INSERT INTO sales_pg
SELECT
    '2024-01-01 00:00:00'::timestamp + (n || ' minutes')::interval,
    n % 1000,
    CASE (n % 4)
        WHEN 0 THEN 'Electronics'
        WHEN 1 THEN 'Clothing'
        WHEN 2 THEN 'Food'
        ELSE 'Books'
    END,
    (random() * 9 + 1)::integer,
    round((random() * 100)::numeric, 2),
    n % 50000
FROM generate_series(1, 1000000) AS n;
"
```

<img width="867" height="286" alt="Screenshot 2026-05-02 at 20 07 55" src="https://github.com/user-attachments/assets/420b0e64-43fb-460e-9b6b-5010415628b1" />

### Запросы

1. Продажи за последний месяц

ClickHouse

```sql
docker exec -it clickhouse-lab clickhouse-client --password password --query "
SELECT
    count() AS sales_count,
    round(sum(quantity * price), 2) AS total_revenue
FROM sales_ch
WHERE sale_date >= addMonths((SELECT max(sale_date) FROM sales_ch), -1);
"
```

<img width="939" height="139" alt="Screenshot 2026-05-02 at 20 48 42" src="https://github.com/user-attachments/assets/db32b619-242f-4923-a45f-9fe1d2ca8b8f" />

Postgres

```sql
docker exec -it postgres-lab psql -U postgres -d postgres -c "
SELECT
    count(*) AS sales_count,
    round(sum(quantity * price)::numeric, 2) AS total_revenue
FROM sales_pg
WHERE sale_date >= (SELECT max(sale_date) FROM sales_pg) - interval '1 month';
"
```

<img width="836" height="197" alt="Screenshot 2026-05-02 at 20 49 55" src="https://github.com/user-attachments/assets/9ff08b20-ccf5-47e9-8ac4-1f0fbe84137b" />

Замер скорости запроса “продажи за последний месяц”

ClickHouse

```bash
time docker exec -i clickhouse-lab clickhouse-client --password password --query "
SELECT
    category,
    count() AS sales_count,
    round(sum(quantity * price), 2) AS total_revenue
FROM sales_ch
WHERE sale_date >= addMonths((SELECT max(sale_date) FROM sales_ch), -1)
GROUP BY category
ORDER BY total_revenue DESC;
"
```

<img width="958" height="243" alt="Screenshot 2026-05-02 at 20 51 32" src="https://github.com/user-attachments/assets/4a68fe5e-34f2-4c72-a826-8d6de8e32dcb" />

Postgres

<img width="872" height="302" alt="Screenshot 2026-05-02 at 20 52 11" src="https://github.com/user-attachments/assets/719707c8-3b0f-4586-9403-9429f22c18d7" />

Размер данных

ClickHouse

```bash
docker exec -it clickhouse-lab clickhouse-client --password password --query "
SELECT
    table,
    formatReadableSize(sum(bytes_on_disk)) AS size_on_disk,
    formatReadableSize(sum(data_compressed_bytes)) AS compressed_size,
    formatReadableSize(sum(data_uncompressed_bytes)) AS uncompressed_size,
    round(sum(data_uncompressed_bytes) / sum(data_compressed_bytes), 2) AS compression_ratio
FROM system.parts
WHERE database = 'default'
  AND table = 'sales_ch'
  AND active
GROUP BY table;
"
```

<img width="955" height="225" alt="Screenshot 2026-05-02 at 20 53 35" src="https://github.com/user-attachments/assets/e2e26307-9de5-4c58-b595-e75262e1d81e" />

Postgres

```bash
docker exec -it postgres-lab psql -U postgres -d postgres -c "
SELECT
    pg_size_pretty(pg_total_relation_size('sales_pg')) AS total_size,
    pg_size_pretty(pg_relation_size('sales_pg')) AS table_size,
    pg_size_pretty(pg_indexes_size('sales_pg')) AS indexes_size;
"
```

<img width="846" height="183" alt="Screenshot 2026-05-02 at 20 54 30" src="https://github.com/user-attachments/assets/a668ebc2-2e99-431c-8419-a387f3d8cbff" />

## Ответьте на вопросы:

### 1. Какая СУБД быстрее вставила 1 млн строк?

Быстрее вставку 1 млн строк выполняет ClickHouse (ClickHouse - 0.168, Postgres - 1.867). Он оптимизирован под массовую загрузку и аналитическую обработку больших объёмов данных. PostgreSQL вставляет данные медленнее, особенно потому что для таблицы созданы индексы idx_sales_pg_date и idx_sales_pg_product, которые также нужно обновлять при вставке. 

### 2. Во сколько раз ClickHouse сжал данные эффективнее?

ClickHouse - 14,88 MB

Postgres - 102 MB

ClickHouse сжал данные в 6,85 раз эффективнее

### 3. Какой вывод можно сделать о выборе СУБД для аналитики?

Для аналитики лучше подходит ClickHouse, потому что он:

- быстрее обрабатывает большие объёмы данных;
- эффективнее выполняет агрегации;
- лучше сжимает данные;
- хорошо подходит для логов, событий, метрик и продаж;
- оптимизирован под OLAP-нагрузку.

PostgreSQL лучше использовать для приложений, где важны транзакции, обновления, удаления, связи между таблицами и целостность данных.

### 4. Разница ClickHouse и PostgreSQL

**ClickHouse** — колоночная СУБД для аналитики. Она быстро обрабатывает большие объёмы данных, хорошо сжимает данные и эффективно выполняет запросы с `COUNT`, `SUM`, `AVG`, `GROUP BY`. Подходит для логов, метрик, статистики и отчётов.

**PostgreSQL** — реляционная строчная СУБД для транзакционных приложений. Она хорошо подходит для сайтов, CRM, интернет-магазинов и систем, где часто используются `INSERT`, `UPDATE`, `DELETE`, связи между таблицами и транзакции.

ClickHouse лучше использовать для аналитики и больших данных, а PostgreSQL — для обычных приложений и транзакционной обработки.

## Задние 3

Потыкаться в http://localhost:8123, посмотреть dashboard

<img width="1506" height="774" alt="Screenshot 2026-05-02 at 21 09 12" src="https://github.com/user-attachments/assets/8a78659e-b4f8-4faf-8331-b267c5e8f356" />
