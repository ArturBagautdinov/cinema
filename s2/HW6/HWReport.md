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







