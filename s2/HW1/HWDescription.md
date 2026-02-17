## Заполнение основных таблиц

Были заполнены 4 таблицы:

- **viewing** (250k)  
- **rental** (250k)  
- **purchase** (250k)  
- **review** (250k)  

Чтобы обеспечить обязательные условия (JSONB/array/range/geometry/fulltext и т.п.), добавил колонки через миграции.

---

## Реализация распределений

- **Равномерное (uniform):** `movie_id` для событий (равномерно по фильмам).  
- **Сильно неравномерное (skewed, Zipf-подобное):** `user_id` для событий (70% событий у топ-10% пользователей).  
- **Низкая селективность (3–5 уникальных):** `viewing.device` (например 5 устройств), `review.sentiment` (3 значения).  
- **Высокая селективность (~90–100% уникальных):** `purchase.coupon_code` (почти уникален), `viewing.session_id` (UUID).  

---

## Кардинальность данных

- **Высокая кардинальность:** `users.email`, `purchase.coupon_code`, `viewing.session_id`.  
- **Низкая кардинальность:** `device`, `sentiment`, `status` (справочники).  

---

## Типы данных

- **Диапазонные значения:** `rental.rental_period` (tstzrange), `purchase.amount_range` (int4range).  
- **Full-text:** `review.comment` + `review.comment_tsv` (tsvector, generated).  
- **Массивы или JSONB:** `viewing.tags` (text[]), `viewing.meta` (jsonb), `purchase.payment_meta` (jsonb).  
- **Геометрические или range-типы:** `viewing.geo` (point), `rental.rental_period` (tstzrange).  

---

## Дополнительные условия

- **5–7+ полей в каждой таблице:** в таблицах уже 6–7, плюс добавлены новые.  
- **NULL-значения 5–20%:** `device`, `return_date`, `comment`, `coupon_code`, `geo` частично NULL.  
- **Реалистичный перекос данных:** 70% событий → 10% пользователей.

---

## Роли в проекте

- `cinema_migrator` — роль для миграций/DDL (расширенные права)
- `cinema_app` — роль приложения (чтение + запись)
- `cinema_ro` — роль только для чтения (SELECT)

### 1) Проверка роли `cinema_ro` (только чтение)

Чтение (работает):

```bash
docker exec -it PgLab psql -U cinema_ro -d CinemaMigr -c "select count(*) from viewing;"
```
<img width="954" height="78" alt="cinema_ro_reading" src="https://github.com/user-attachments/assets/fce03738-4f6b-4bbe-9c1d-b8983879f9aa" />

Запись (permission denied):
```bash
docker exec -it PgLab psql -U cinema_ro -d CinemaMigr -c "insert into viewing(user_id,movie_id,progress) values (1,1,10);"
```
<img width="1179" height="49" alt="Screenshot 2026-02-17 at 12 05 34" src="https://github.com/user-attachments/assets/e699a1fe-6640-41e1-a3b5-fa07246421a2" />

### 2) Проверка роли cinema_app (чтение + запись)

Чтение (работает):
```bash
docker exec -it PgLab psql -U cinema_app -d CinemaMigr -c "select count(*) from viewing;"
```
<img width="947" height="76" alt="Screenshot 2026-02-17 at 12 07 48" src="https://github.com/user-attachments/assets/01dff17e-a8f1-405e-ae14-85e5ace9a1d9" />

Запись (работает):
```bash
docker exec -it PgLab psql -U cinema_app -d CinemaMigr -c "insert into viewing(user_id,movie_id,progress) values (1,1,10);"
```
<img width="1191" height="36" alt="Screenshot 2026-02-17 at 12 09 08" src="https://github.com/user-attachments/assets/3978c24e-2cd2-40bd-b8ce-807f4e1ed4c1" />

### 3) Проверка роли cinema_migrator (DDL/миграции)

Проверка, что можно создавать объекты (DDL):
```bash
docker exec -it PgLab psql -U cinema_migrator -d CinemaMigr -c "create table if not exists __perm_test(id int);"
```
<img width="1112" height="36" alt="Screenshot 2026-02-17 at 12 10 54" src="https://github.com/user-attachments/assets/baa6df06-aee0-4fb3-a473-00703e5b29e9" />

Проверка, что можно удалять тестовый объект:

```bash
docker exec -it PgLab psql -U cinema_migrator -d CinemaMigr -c "drop table if exists __perm_test;"
```
<img width="1015" height="38" alt="Screenshot 2026-02-17 at 12 12 02" src="https://github.com/user-attachments/assets/a4b628f5-9158-405c-8358-44dcfd697b41" />

---

## Миграции Flyway

### V1 — Базовая схема  
**Создание:**
- `users`
- `movie`
- `rental`
- `purchase`
- `viewing`
- `review`
- справочников

---

### V2 — Расширение типов  
**Добавлены:**
- **JSONB**
- **arrays**
- **range-types**
- **geometry**
- **fulltext (tsvector)**

---

### V3 — Индексы  
**Добавлены:**
- **GIN** (JSONB, arrays, fulltext)
- **GiST** (range)
- **Partial indexes**
- индексы на **FK**

