## ЧАСТЬ 1. Базовые операции с транзакциями

### 1.1. Добавление нового пользователя и подписки
```sql
BEGIN;

-- Добавляем нового пользователя
INSERT INTO Cinema.User (
    id, 
    name, 
    role, 
    email, 
    registrationDate, 
    phone
) VALUES (
    'u006', 
    'Frank Miller', 
    'user', 
    'frank@mail.com', 
    '2023-11-15', 
    '5559876543'
);

-- Добавляем подписку для нового пользователя
INSERT INTO Cinema.Subscription (
    user_id, 
    plan_type, 
    price, 
    start_date, 
    end_date, 
    status
) VALUES (
    6, 
    'Premium', 
    15.99, 
    '2023-11-15', 
    '2024-11-14', 
    'active'
);

COMMIT;
```
**Результат**: новый пользователь добавлен, подписка создана.

<img width="1018" height="133" alt="image" src="https://github.com/user-attachments/assets/bf294e14-54a6-4379-ac89-0a6a2ab317cc" />


---

### 1.2. Добавление нового актёра и связи с фильмом
```sql
BEGIN;

-- Добавляем нового актёра
INSERT INTO Cinema.Actor (
    name, 
    birth_date, 
    country, 
    bio
) VALUES (
    'Tom Hardy', 
    '1977-09-15', 
    'UK', 
    'Versatile actor known for intense roles'
);

-- Добавляем связь актёра с фильмом Inception
INSERT INTO Cinema.MovieActor (
    movie_id, 
    actor_id, 
    role_name, 
    is_lead_role
) VALUES (
    1, 
    4, 
    'Eames', 
    FALSE
);

COMMIT;
```
**Результат**: актёр добавлен, связь с фильмом создана.

<img width="752" height="136" alt="image" src="https://github.com/user-attachments/assets/3a394eb5-1884-45aa-b595-c87fcdc34107" />


---

### 2.1. Добавление пользователя и подписки с ROLLBACK
```sql
BEGIN;

-- Добавляем нового пользователя
INSERT INTO Cinema.User (
    id, 
    name, 
    role, 
    email, 
    registrationDate, 
    phone
) VALUES (
    'u007', 
    'Grace Lee', 
    'user', 
    'grace@mail.com', 
    '2023-11-20', 
    '5551112222'
);

-- Добавляем подписку для нового пользователя
INSERT INTO Cinema.Subscription (
    user_id, 
    plan_type, 
    price, 
    start_date, 
    end_date, 
    status
) VALUES (
    7, 
    'Standard', 
    9.99, 
    '2023-11-20', 
    '2024-11-19', 
    'active'
);

ROLLBACK;
```
**Результат**: изменений в базе нет — транзакция откатилась.

<img width="625" height="125" alt="image" src="https://github.com/user-attachments/assets/802ae4d2-d5f0-4080-bf1b-929a06285fbe" />


---

### 2.2. Добавление актёра и связи с ROLLBACK
```sql
BEGIN;

-- Добавляем нового актёра
INSERT INTO Cinema.Actor (
    name, 
    birth_date, 
    country, 
    bio
) VALUES (
    'Emma Stone', 
    '1988-11-06', 
    'USA', 
    'Academy Award winning actress'
);

-- Добавляем связь актёра с фильмом
INSERT INTO Cinema.MovieActor (
    movie_id, 
    actor_id, 
    role_name, 
    is_lead_role
) VALUES (
    2, 
    5, 
    'Sarah Harding', 
    FALSE
);

ROLLBACK;
```
**Результат**: изменений в базе нет — транзакция откатилась.

<img width="595" height="119" alt="image" src="https://github.com/user-attachments/assets/df0f2429-c7dd-4a99-aa6b-07f39c7dd4fb" />


---

### 3.1. Ошибка внутри транзакции (деление на 0)
```sql
BEGIN;

-- Добавляем новый отзыв
INSERT INTO Cinema.Review (
    user_id, 
    movie_id, 
    rating, 
    comment, 
    review_date
) VALUES (
    4, 
    1, 
    8, 
    'Запрос с ошибкой — не должен сохраниться', 
    '2023-11-25'
);

-- Добавляем ошибку
SELECT 1 / 0;

-- Добавляем просмотр
INSERT INTO Cinema.Viewing (
    user_id, 
    movie_id, 
    viewing_date, 
    progress, 
    device
) VALUES (
    4, 
    1, 
    '2023-11-25', 
    25.5, 
    'Phone'
);

COMMIT;
```
**Результат**: все изменения откатились из-за ошибки деления на ноль.

<img width="605" height="128" alt="image" src="https://github.com/user-attachments/assets/6c5b5c59-05fa-496f-ac97-468426a907d2" />


---

### 3.2. Ошибка вставки связи с несуществующей записью
```sql
BEGIN;

-- Добавляем новый отзыв
INSERT INTO Cinema.Review (
    user_id, 
    movie_id, 
    rating, 
    comment, 
    review_date
) VALUES (
    5, 
    2, 
    7, 
    'Хороший фильм', 
    '2023-11-26'
);

-- Добавляем ошибку в виде связи с несуществующим пользователем
INSERT INTO Cinema.Viewing (
    user_id, 
    movie_id, 
    viewing_date, 
    progress, 
    device
) VALUES (
    9999,  -- user_id = 9999 не существует
    2, 
    '2023-11-26', 
    50.0, 
    'Tablet'
);

COMMIT;
```
**Результат**: все изменения откатились из-за ошибки внешнего ключа.

<img width="611" height="131" alt="image" src="https://github.com/user-attachments/assets/e055ccbe-ef14-40c6-a6a9-7cc5d86487a9" />


---

## ЧАСТЬ 2. Уровни изоляции

### 1. READ UNCOMMITTED / READ COMMITTED

**Вывод**: PostgreSQL не поддерживает грязное чтение — T2 видит только коммитнутые данные.

#### 1.1. READ UNCOMMITTED: попытка увидеть незакоммиченные изменения

**T1:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;

UPDATE Cinema.Movie
SET description = 'Новое описание для READ UNCOMMITTED'
WHERE UniqueID = 1;
```

**T2:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;

SELECT UniqueID, title, description
FROM Cinema.Movie
WHERE UniqueID = 1;
```

**Результат**: T2 видит старые данные, грязное чтение не произошло.

<img width="541" height="160" alt="image" src="https://github.com/user-attachments/assets/cc831402-e345-4ab8-955d-545bcebc4209" />


---

#### 1.2. READ COMMITTED: попытка увидеть незакоммиченные изменения

**T1:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;

UPDATE Cinema.Movie
SET description = 'Новое описание для READ COMMITTED'
WHERE UniqueID = 1;
```

**T2:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT UniqueID, title, description
FROM Cinema.Movie
WHERE UniqueID = 1;
```

**Результат**: T2 видит старые данные до коммита T1.

<img width="470" height="143" alt="image" src="https://github.com/user-attachments/assets/a5021e27-cd3c-4cfe-8db1-5bc7e612dead" />


---

### 2. READ COMMITTED: неповторяющееся чтение

**Вывод**: при READ COMMITTED второй SELECT видит обновленные данные после коммита T2.

#### 2.1. Обновление описания фильма с COMMIT

**T1:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT UniqueID, title, description
FROM Cinema.Movie
WHERE UniqueID = 1;
```

**T2:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;

UPDATE Cinema.Movie
SET description = 'Описание обновлено транзакцией T2'
WHERE UniqueID = 1;

COMMIT;
```

**T1:**
```sql
SELECT UniqueID, title, description
FROM Cinema.Movie
WHERE UniqueID = 1;
```

**Результат**: второй SELECT в T1 видит новые данные (неповторяющееся чтение).

<img width="574" height="148" alt="image" src="https://github.com/user-attachments/assets/c859af6a-309b-4066-a85c-35b830567b51" />

<img width="533" height="137" alt="image" src="https://github.com/user-attachments/assets/ce0013a4-1a21-4631-a43c-c05067a7d68d" />


---

#### 2.2. Обновление длительности фильма с COMMIT

**T1:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT UniqueID, title, duration
FROM Cinema.Movie
WHERE UniqueID = 2;
```

**T2:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;

UPDATE Cinema.Movie
SET duration = '130 minutes'
WHERE UniqueID = 2;

COMMIT;
```

**T1:**
```sql
SELECT UniqueID, title, duration
FROM Cinema.Movie
WHERE UniqueID = 2;
```

**Результат**: второй SELECT в T1 видит новые данные (неповторяющееся чтение).

<img width="445" height="137" alt="image" src="https://github.com/user-attachments/assets/2792ceeb-9eb9-4ff2-b53e-32cf886fd3d3" />

<img width="452" height="131" alt="image" src="https://github.com/user-attachments/assets/f695d6a0-957d-4b18-b263-09afddf8f187" />


---

### 3. REPEATABLE READ

**Вывод**: T1 не видит изменения от T2 до завершения собственной транзакции.

#### 3.1. Обновление описания фильма при REPEATABLE READ

**T1:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;

SELECT UniqueID, title, description
FROM Cinema.Movie
WHERE UniqueID = 3;
```

**T2:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;

UPDATE Cinema.Movie
SET description = 'Описание, изменённое в T2'
WHERE UniqueID = 3;

COMMIT;
```

**T1:**
```sql
SELECT UniqueID, title, description
FROM Cinema.Movie
WHERE UniqueID = 3;
```

**Результат**: второй SELECT в T1 не видит новые данные от T2.

<img width="467" height="146" alt="image" src="https://github.com/user-attachments/assets/d93c697a-a95f-4bc3-8697-fc165f0bace9" />

<img width="458" height="137" alt="image" src="https://github.com/user-attachments/assets/e025907b-b259-4afe-9032-c05986e43256" />


---

#### 3.2. Фантомное чтение: INSERT в T2

**T1:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;

SELECT UniqueID, title, release_year
FROM Cinema.Movie
WHERE release_year = 2010;
```

**T2:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;

INSERT INTO Cinema.Movie (
    title, 
    description, 
    release_year, 
    duration, 
    age_rating, 
    language, 
    country, 
    director_id, 
    budget
) VALUES (
    'Shutter Island', 
    'Психологический триллер', 
    2010, 
    '138 minutes', 
    'R', 
    'English', 
    'USA', 
    1, 
    80000000
);

COMMIT;
```

**T1:**
```sql
SELECT UniqueID, title, release_year
FROM Cinema.Movie
WHERE release_year = 2010
ORDER BY UniqueID;
```

**Результат**: второй SELECT в T1 не видит новую запись (фантомное чтение предотвращено).

<img width="401" height="132" alt="image" src="https://github.com/user-attachments/assets/456b790c-e4ff-4bb7-9075-c22a0d51ae3b" />

<img width="415" height="139" alt="image" src="https://github.com/user-attachments/assets/230df262-327f-40ea-b3aa-549d7a4b27a7" />


---

### 4. SERIALIZABLE

**Вывод**: возможен конфликт при одновременной вставке данных, требуется повтор транзакции.

#### 4.1. Попытка одновременной вставки

**T1:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

SELECT * FROM Cinema.Movie WHERE title = 'The Dark Knight';
```

**T2:**
```sql
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

SELECT * FROM Cinema.Movie WHERE title = 'The Dark Knight';

INSERT INTO Cinema.Movie (
    title, 
    description, 
    release_year, 
    duration, 
    age_rating, 
    language, 
    country, 
    director_id
) VALUES (
    'The Dark Knight', 
    'Описание от T2', 
    2008, 
    '152 minutes', 
    'PG-13', 
    'English', 
    'USA', 
    2
);

COMMIT;
```

**T1:**
```sql
INSERT INTO Cinema.Movie (
    title, 
    description, 
    release_year, 
    duration, 
    age_rating, 
    language, 
    country, 
    director_id
) VALUES (
    'The Dark Knight', 
    'Описание от T1', 
    2008, 
    '152 minutes', 
    'PG-13', 
    'English', 
    'USA', 
    2
);

COMMIT;
```

**Результат**: T1 получает ошибку сериализации из-за конфликта с T2.

<img width="758" height="179" alt="image" src="https://github.com/user-attachments/assets/4a0d4bf4-edc0-4ae2-8914-fa69e26e8efe" />

---

#### 4.2. Откат и повтор транзакции

**T1:**
```sql
ROLLBACK;

BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

SELECT * FROM Cinema.Movie WHERE title = 'The Dark Knight';

INSERT INTO Cinema.Movie (
    title, 
    description, 
    release_year, 
    duration, 
    age_rating, 
    language, 
    country, 
    director_id
) VALUES (
    'The Dark Knight', 
    'Описание от T1 после отката', 
    2008, 
    '152 minutes', 
    'PG-13', 
    'English', 
    'USA', 
    2
);

COMMIT;
```

**Результат**: после отката и повтора транзакция выполнилась успешно (но будет ошибка unique constraint из-за предыдущей вставки).

<img width="582" height="190" alt="image" src="https://github.com/user-attachments/assets/f8a04827-2943-44c2-aec1-5e162cdb5af8" />

---

## ЧАСТЬ 3. SAVEPOINT

### 1.1. Транзакция с одной точкой сохранения

**Вывод**: сохраняются только те изменения, что произошли до точки сохранения, если был откат до этой точки.

```sql
BEGIN;

INSERT INTO Cinema.Genre (
    name, 
    description
) VALUES (
    'Horror', 
    'Фильмы ужасов'
);

SAVEPOINT first_savepoint;

INSERT INTO Cinema.Genre (
    name, 
    description
) VALUES (
    'Comedy', 
    'Комедийные фильмы'
);

ROLLBACK TO SAVEPOINT first_savepoint;

COMMIT;

SELECT UniqueID, name, description 
FROM Cinema.Genre;
```

**Результат**: сохранился только первый жанр (Horror), второй (Comedy) откатился.

<img width="466" height="213" alt="image" src="https://github.com/user-attachments/assets/83632a36-8a08-420c-9e3b-c05efe166448" />

---

### 2.1. Транзакция с несколькими точками сохранения

**Вывод**: сохраняются только те изменения, что произошли до точки сохранения, до которой был выполнен откат.

**T1 (откат до первой точки):**
```sql
BEGIN;

INSERT INTO Cinema.Genre (
    name, 
    description
) VALUES (
    'Documentary', 
    'Документальные фильмы'
);

SAVEPOINT first_savepoint;

INSERT INTO Cinema.Genre (
    name, 
    description
) VALUES (
    'Musical', 
    'Музыкальные фильмы'
);

SAVEPOINT second_savepoint;

INSERT INTO Cinema.Genre (
    name, 
    description
) VALUES (
    'Western', 
    'Вестерны'
);

ROLLBACK TO SAVEPOINT first_savepoint;

COMMIT;

SELECT UniqueID, name, description 
FROM Cinema.Genre;
```

**T2 (откат до второй точки):**
```sql
BEGIN;

INSERT INTO Cinema.Genre (
    name, 
    description
) VALUES (
    'Thriller', 
    'Триллеры'
);

SAVEPOINT first_savepoint;

INSERT INTO Cinema.Genre (
    name, 
    description
) VALUES (
    'Romance', 
    'Романтические фильмы'
);

SAVEPOINT second_savepoint;

INSERT INTO Cinema.Genre (
    name, 
    description
) VALUES (
    'Fantasy', 
    'Фэнтези'
);

ROLLBACK TO SAVEPOINT second_savepoint;

COMMIT;

SELECT UniqueID, name, description 
FROM Cinema.Genre;
```

**Результат T1**: сохранился только Documentary.  
**Результат T2**: сохранились Thriller и Romance.

<img width="508" height="244" alt="image" src="https://github.com/user-attachments/assets/863e92f4-a29e-41a9-8b02-8bfe46f7f5db" />

<img width="470" height="290" alt="image" src="https://github.com/user-attachments/assets/5f5a9351-3148-4f4c-a3c2-7e043485254a" />



