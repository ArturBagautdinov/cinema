## 1) '>' Query 
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM users
WHERE registration_date > now() - interval '30 days';
```

No indexes <br>
<img width="652" height="244" alt="Screenshot 2026-03-03 at 20 23 48" src="https://github.com/user-attachments/assets/fdff7df3-77fe-4e8a-a1d1-19b0d2ca69f5" />

B-tree index <br>
<img width="791" height="315" alt="Screenshot 2026-03-03 at 20 27 08" src="https://github.com/user-attachments/assets/cfa33ec6-a84f-4061-9884-3c388049643d" />

Hash index - not efficient

## 2) '<' Query 

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT rental_id, user_id, price
FROM rental
WHERE price < 3.00;
```

No indexes <br>
<img width="809" height="314" alt="Screenshot 2026-03-03 at 20 31 34" src="https://github.com/user-attachments/assets/17dde10d-77ed-40d8-97a6-32a710c1a8cf" />

B-tree index <br>
<img width="815" height="267" alt="Screenshot 2026-03-03 at 20 32 39" src="https://github.com/user-attachments/assets/1fcb9fb5-b3b8-4ee3-a80c-6e96750e76f0" />

Hash index - not efficient

## 3) '=' Query 

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM subscription
WHERE user_id = 12345;
```

No indexes <br>
<img width="637" height="193" alt="Screenshot 2026-03-03 at 20 36 22" src="https://github.com/user-attachments/assets/381caab6-27b0-43fb-8035-474aa342eb39" />

B-tree index <br>
<img width="785" height="291" alt="Screenshot 2026-03-03 at 20 37 17" src="https://github.com/user-attachments/assets/2fb644a1-7f09-480a-8fc6-29632141b0b9" />

Hash index <br>
<img width="786" height="292" alt="Screenshot 2026-03-03 at 20 38 15" src="https://github.com/user-attachments/assets/a503d020-054d-4ae3-8d4d-bac843b4e2d0" />

## 4) LIKE 'prefix%' Query 

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT movie_id, title
FROM movie
WHERE title LIKE 'Star%';
```

No indexes <br>
<img width="632" height="236" alt="Screenshot 2026-03-03 at 20 39 24" src="https://github.com/user-attachments/assets/7bd0eb8c-11c3-40e8-8ed0-269accd29a40" />

B-tree index <br>
<img width="801" height="240" alt="Screenshot 2026-03-03 at 20 40 08" src="https://github.com/user-attachments/assets/784d55fb-c32b-46c8-b44f-069e5cebb366" />

Hash index - not efficient

## 5) LIKE '%prefix' Query 

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT movie_id, title
FROM movie
WHERE title LIKE 'Star%';
```

No indexes (using index scan) <br>
<img width="801" height="192" alt="Screenshot 2026-03-03 at 20 41 46" src="https://github.com/user-attachments/assets/26d9a492-be8e-488b-a3b4-91498a237673" />

B-tree, Hash indexes - not efficient

## 6) Composite index

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT rental_id, user_id, rental_date, price
FROM rental
WHERE user_id = 12345
  AND rental_date >= now() - interval '30 days'
ORDER BY rental_date DESC;
```

No indexes <br>
<img width="697" height="416" alt="Screenshot 2026-03-03 at 20 44 19" src="https://github.com/user-attachments/assets/6822411d-5d91-4134-bb41-7cb7fe91da90" />

Using composite index:

```sql
CREATE INDEX idx_rental_userid_rdate
    ON rental (user_id, rental_date DESC);
```

<img width="766" height="214" alt="Screenshot 2026-03-03 at 20 45 21" src="https://github.com/user-attachments/assets/621a80ec-aadf-4774-9e33-dcfe0cf7fd07" />






