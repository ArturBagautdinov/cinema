## Архитектура

<img width="491" height="459" alt="Screenshot 2026-03-23 at 15 50 45" src="https://github.com/user-attachments/assets/a860d8d2-1ee2-475e-bd05-e062e9b65a61" />

## Проверка репликации данных

### Проверка, что реплики вообще подключены
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "SELECT pid, usename, application_name, state, sync_state FROM pg_stat_replication;"
```
<img width="1332" height="96" alt="Screenshot 2026-03-23 at 16 00 10" src="https://github.com/user-attachments/assets/23966e4d-16bd-47ed-852e-63b313e456cb" />

### Проверка, что это именно реплики

```bash
docker exec -it PgReplica1 psql -U postgres -d CinemaMigr -c "SELECT pg_is_in_recovery();"
docker exec -it PgReplica2 psql -U postgres -d CinemaMigr -c "SELECT pg_is_in_recovery();"
```

<img width="967" height="165" alt="Screenshot 2026-03-23 at 16 01 37" src="https://github.com/user-attachments/assets/3becf84f-80a5-4461-b531-25e0f74ce173" />

### Вставить данные на master, проверить наличие строки на репликах
вставим запись в viewing:

```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "INSERT INTO viewing (user_id, movie_id, progress, device, tags, meta) VALUES (1, 1, 42, 'replication_demo', ARRAY['demo','primary'], '{\"source\":\"primary\",\"check\":\"replication\"}');"
```
<img width="1283" height="45" alt="Screenshot 2026-03-23 at 16 11 21" src="https://github.com/user-attachments/assets/27de34a3-3985-4ce4-b56b-94033bb9c216" />

Проверим наличие строки на репликах <br>
на первой: <br>
```bash
docker exec -it PgReplica1 psql -U postgres -d CinemaMigr -c "SELECT viewing_id, user_id, movie_id, progress, device, tags, meta FROM viewing WHERE device = 'replication_demo';"
```
<img width="1500" height="148" alt="Screenshot 2026-03-23 at 16 15 09" src="https://github.com/user-attachments/assets/79994484-c685-4750-8001-0d125976d6f1" />

на второй:
```bash
docker exec -it PgReplica2 psql -U postgres -d CinemaMigr -c "SELECT viewing_id, user_id, movie_id, progress, device, tags, meta FROM viewing WHERE device = 'replication_demo';"
```
<img width="1503" height="121" alt="Screenshot 2026-03-23 at 16 15 56" src="https://github.com/user-attachments/assets/2c7a3677-5e66-496d-ac4e-40bb3ba735f8" />

### Что произойдет если попробовать вставить данные на реплике
Попытка записи на replica1:

```bash
docker exec -it PgReplica1 psql -U postgres -d CinemaMigr -c "INSERT INTO viewing (user_id, movie_id, progress, device) VALUES (1, 1, 50, 'write_on_replica');"
```
<img width="1506" height="62" alt="Screenshot 2026-03-23 at 16 17 50" src="https://github.com/user-attachments/assets/8204e88c-4e38-4f28-92d8-298125cccd7b" />
Попытка записи на replica2:

```bash
docker exec -it PgReplica2 psql -U postgres -d CinemaMigr -c "INSERT INTO viewing (user_id, movie_id, progress, device) VALUES (1, 1, 60, 'write_on_replica');"
```
<img width="1507" height="60" alt="Screenshot 2026-03-23 at 16 18 57" src="https://github.com/user-attachments/assets/9c27275b-4f1b-4dcc-8f28-45cd8b2af2bb" />
Физическая реплика работает только на чтение.

## Анализ replication lag
Нагрузка INSERT:
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "INSERT INTO viewing (user_id, movie_id, progress, device, tags, meta) SELECT 3, 7, (random()*100)::int, 'load_test_heavy', ARRAY['load','replication'], jsonb_build_object('n', g, 'source', 'primary', 'ts', now()) FROM generate_series(1, 200000) g;"
```
Для наблюдения за replication lag повторяю команды несколько раз:
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "SELECT application_name, state, sync_state, write_lag, flush_lag, replay_lag, pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn) AS byte_lag FROM pg_stat_replication;"
docker exec -it PgReplica1 psql -U postgres -d CinemaMigr -c "SELECT now() - pg_last_xact_replay_timestamp() AS replication_delay;"
docker exec -it PgReplica2 psql -U postgres -d CinemaMigr -c "SELECT now() - pg_last_xact_replay_timestamp() AS replication_delay;"
```
до нагрузки:
<img width="1509" height="142" alt="Screenshot 2026-03-23 at 16 53 09" src="https://github.com/user-attachments/assets/599d21b1-2520-4849-a83e-d36b0c8a7119" />

после:
<img width="1508" height="563" alt="Screenshot 2026-03-23 at 16 47 44" src="https://github.com/user-attachments/assets/3ccce22c-f663-4307-af19-330734030407" />

### При первом измерении (сразу после выполнения массовых INSERT): <br>
обе реплики находились в состоянии streaming, режим репликации — async <br>
значения задержек: <br>
write_lag ≈ 0.004 сек <br>
flush_lag ≈ 0.008–0.009 сек <br>
replay_lag ≈ 0.22–0.23 сек <br>
byte_lag ≈ 24 байта <br>
на репликах: <br>
replication_delay ≈ 27 секунд (реплика ещё не догнала primary,
данные устаревшие) <br>
В момент сразу после нагрузки: <br>
primary активно генерировал WAL <br>
реплики не успевали мгновенно применить изменения <br>
наблюдается заметный replication lag <br>
данные на репликах временно могут быть устаревшими <br>

### При повторном измерении (через небольшой промежуток времени):
значения lag резко уменьшились: <br>
write_lag ≈ 0.0001 сек <br>
flush_lag ≈ 0.0004–0.0005 сек <br>
replay_lag ≈ 0.0007 сек <br>
byte_lag = 0 <br>
на репликах: <br>
replication_delay ≈ 1–2 секунды <br>
после завершения нагрузки реплики начали догонять primary <br>
WAL был полностью применен <br>
задержка практически исчезла <br>
система вернулась к согласованному состоянию <br>

## Настроить Logical replication
### Подготовка publisher на primary
Создание тестовых таблиц без PK:
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "
CREATE TABLE IF NOT EXISTS lr_test (
    id BIGINT PRIMARY KEY,
    value TEXT
);
CREATE TABLE IF NOT EXISTS lr_no_pk (
    id BIGINT,
    value TEXT
);
"
```
Добавление тестовых данных:
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "
INSERT INTO lr_test (id, value) VALUES (1, 'first row')
ON CONFLICT (id) DO NOTHING;

INSERT INTO lr_no_pk (id, value) VALUES (1, 'row without pk');
"
```
Создание publication:
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "
DROP PUBLICATION IF EXISTS cinema_pub;
CREATE PUBLICATION cinema_pub FOR TABLE lr_test, lr_no_pk;
"
```
### Подготовка subscriber

Создание таблиц на subscriber:
```bash
docker exec -it PgLogicalSub psql -U postgres -d CinemaMigr -c "
CREATE TABLE IF NOT EXISTS lr_test (
    id BIGINT PRIMARY KEY,
    value TEXT
);
CREATE TABLE IF NOT EXISTS lr_no_pk (
    id BIGINT,
    value TEXT
);
"
```
Создание Subscription:
```bash
docker exec -it PgLogicalSub psql -U postgres -d CinemaMigr -c "
DROP SUBSCRIPTION IF EXISTS cinema_sub;
CREATE SUBSCRIPTION cinema_sub
CONNECTION 'host=postgres-primary port=5444 dbname=CinemaMigr user=replicator password=pass'
PUBLICATION cinema_pub;
"
```
### Данные реплицируются

Primary - вставка строки:
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "
INSERT INTO lr_test (id, value) VALUES (2, 'replicated row');
"
```
<img width="570" height="71" alt="Screenshot 2026-03-23 at 17 47 01" src="https://github.com/user-attachments/assets/d460ff5f-07ef-4efb-99d8-eb05193859da" />

Проверка у subscriber:
```bash
docker exec -it PgLogicalSub psql -U postgres -d CinemaMigr -c "
SELECT * FROM lr_test ORDER BY id;
"
```
<img width="775" height="118" alt="Screenshot 2026-03-23 at 17 57 11" src="https://github.com/user-attachments/assets/64671548-b5f7-4445-9e58-6ba27526d12c" />

Строка появилась у subscriber, значит logical replication по PUBLICATION/SUBSCRIPTION работает.

### DDL не реплицируется
измение схемы у primary:
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "
ALTER TABLE lr_test ADD COLUMN note TEXT;
"
```
<img width="767" height="65" alt="Screenshot 2026-03-23 at 18 00 43" src="https://github.com/user-attachments/assets/dd4d2baf-b733-4ad2-b77f-114a97bcad6b" /> <br>

Проверка схемы у subscriber:
```bash
docker exec -it PgLogicalSub psql -U postgres -d CinemaMigr -c "
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'lr_test';
"
```
<img width="780" height="155" alt="Screenshot 2026-03-23 at 18 06 42" src="https://github.com/user-attachments/assets/ca16b3a4-d915-463f-9fb2-60c840a803c5" /> <br>
У subscriber столбца note нет. <br>
logical replication реплицирует данные, но не DDL.

### Проверку REPLICA IDENTITY
Попробовать UPDATE на primary для таблицы без PK
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "
UPDATE lr_no_pk
SET value = 'updated without pk'
WHERE id = 1;
"
```
<img width="757" height="103" alt="Screenshot 2026-03-23 at 18 13 45" src="https://github.com/user-attachments/assets/322582a7-d09f-421d-b071-958776c4a69b" /> <br>
subscriber не понимает, какую именно строку обновлять, если у таблицы нет PK и не задана replica identity. <br>

Чтобы исправь это: <br>
На primary задать replica identity full
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "
ALTER TABLE lr_no_pk REPLICA IDENTITY FULL;
"
```
Снова UPDATE:
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "
UPDATE lr_no_pk
SET value = 'updated after replica identity'
WHERE id = 1;
"
```
<img width="756" height="87" alt="Screenshot 2026-03-23 at 18 15 50" src="https://github.com/user-attachments/assets/a21c5618-94f2-4dd7-a747-ee8bc132859c" /> <br>
Проверка у subscriber:

```bash
docker exec -it PgLogicalSub psql -U postgres -d CinemaMigr -c "
SELECT * FROM lr_no_pk;
"
```
<img width="781" height="108" alt="Screenshot 2026-03-23 at 18 16 44" src="https://github.com/user-attachments/assets/bd2f1864-7978-44c2-91f5-bffb2819101d" /> <br>
UPDATE произошел

### Проверку replication status
У publisher:
```bash
docker exec -it PgPrimary psql -U postgres -d CinemaMigr -c "
SELECT pid, usename, application_name, client_addr, state
FROM pg_stat_replication;
"
```
<img width="767" height="152" alt="Screenshot 2026-03-23 at 18 19 01" src="https://github.com/user-attachments/assets/98bbaf80-6057-46ca-8f3e-e4336a57b14c" />

У subscriber:
```bash
docker exec -it PgLogicalSub psql -U postgres -d CinemaMigr -c "
SELECT subname, pid, received_lsn, latest_end_lsn, latest_end_time
FROM pg_stat_subscription;
"
```
<img width="789" height="125" alt="Screenshot 2026-03-23 at 18 20 46" src="https://github.com/user-attachments/assets/5845ed4e-21fd-42d6-bdff-29298f6268e1" />

Подписка активна, есть pid

### Как могут пригодится pg_dump/pg_restore для Logical replication
Для logical replication pg_dump и pg_restore полезны прежде всего для переноса схемы и первоначальной подготовки подписчика, так как publication/subscription реплицируют изменения данных, но не изменения структуры. Поэтому dump/restore можно использовать для начального разворачивания таблиц, индексов и других объектов схемы, а logical replication — для дальнейшей синхронизации строк.





