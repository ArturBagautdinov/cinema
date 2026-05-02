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









