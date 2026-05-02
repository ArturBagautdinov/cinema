# Домашнее задание Cassandra

### Задание 1: Инициализация БД с репликацией

Создайте Keyspace `university` с фактором репликации **2** (чтобы данные дублировались на обе ноды)

```sql
DROP KEYSPACE IF EXISTS university;

CREATE KEYSPACE university
WITH replication = {
    'class': 'SimpleStrategy',
    'replication_factor': 2
};
```

<img width="524" height="141" alt="Screenshot 2026-05-02 at 21 32 18" src="https://github.com/user-attachments/assets/b6b1c9fe-f9e0-40fc-91e0-a16905f447d3" />

### Задание 2: Создание таблицы и данных

Создайте таблицу `student_grades`: `student_id(uuid)`, `created_at`, `subject`, `grade`.

Настройте ключи: **Partition Key** — `student_id`, **Clustering Key** — `created_at`.

```sql
CREATE TABLE student_grades (
    student_id uuid,
    created_at timestamp,
    subject text,
    grade int,
    PRIMARY KEY (student_id, created_at)
) WITH CLUSTERING ORDER BY (created_at DESC);
```

<img width="497" height="144" alt="Screenshot 2026-05-02 at 21 36 46" src="https://github.com/user-attachments/assets/0a38140c-f16c-439e-8e91-5727947cbd88" />

Выполните по 2 вставки  для двух разных студентов. Для генерации ID используйте функцию `uuid()`.

```sql
SELECT uuid() FROM system.local;
```

```sql
INSERT INTO student_grades (student_id, created_at, subject, grade)
VALUES (9edf5855-05fb-4b97-8e2d-0c6c4e289afb, '2026-05-01 10:00:00', 'Math', 5);

INSERT INTO student_grades (student_id, created_at, subject, grade)
VALUES (9edf5855-05fb-4b97-8e2d-0c6c4e289afb, '2026-05-02 11:00:00', 'Physics', 4);

INSERT INTO student_grades (student_id, created_at, subject, grade)
VALUES (e5834b34-d3b1-48e0-ba6d-58b8c766ba6a, '2026-05-01 12:00:00', 'Math', 3);

INSERT INTO student_grades (student_id, created_at, subject, grade)
VALUES (e5834b34-d3b1-48e0-ba6d-58b8c766ba6a, '2026-05-02 13:00:00', 'History', 5);
```

<img width="758" height="188" alt="Screenshot 2026-05-02 at 21 39 49" src="https://github.com/user-attachments/assets/86b64502-aeb9-4e11-a560-932475a3d897" />

### Задание 3: Проверка распределения данных (Partitioning)

Найдите UUID ваших студентов: `SELECT student_id FROM student_grades;`.

В терминале выполните команду для получения ip нод с данными каждого UUID: `nodetool getendpoints keyspace table_name <UUID>`, посмотрите результат

```sql
SELECT student_id FROM student_grades;
```

<img width="425" height="173" alt="Screenshot 2026-05-02 at 21 41 55" src="https://github.com/user-attachments/assets/310f5836-adc0-48c9-aa68-066053ecfae5" />

Для первого студента:

```bash
docker exec -it cassandra-node1 nodetool getendpoints university student_grades 9edf5855-05fb-4b97-8e2d-0c6c4e289afb
```

<img width="1204" height="76" alt="Screenshot 2026-05-02 at 21 44 39" src="https://github.com/user-attachments/assets/87c2ca65-9de1-4e7c-9e50-29122674d733" />

Для второго студента:

```bash
docker exec -it cassandra-node1 nodetool getendpoints university student_grades e5834b34-d3b1-48e0-ba6d-58b8c766ba6a
```

<img width="1201" height="75" alt="Screenshot 2026-05-02 at 21 45 41" src="https://github.com/user-attachments/assets/3731fac3-d676-45ab-9ad9-9169cf7c4401" />

Вывод:

Так как фактор репликации равен 2, данные каждой партиции student_id хранятся на двух нодах Cassandra (172.22.0.2 и 172.22.0.3)

### Задание 4: Работа с фильтрацией

Попробуйте выполнить поиск по предмету (не ключевое поле), зафиксируйте ошибку 

Выполните этот же запрос, добавив `ALLOW FILTERING`.  Посмотрите результаты.

```sql
SELECT * FROM student_grades WHERE subject = 'Math';
```

<img width="1512" height="73" alt="Screenshot 2026-05-02 at 21 49 29" src="https://github.com/user-attachments/assets/e0dd49ba-88a8-4a42-92ca-fc4701746d63" />

Поле subject не является ни Partition Key, ни Clustering Key. Поэтому Cassandra не разрешает обычный поиск по этому полю.

Теперь тот же запрос с ALLOW FILTERING

```sql
SELECT * FROM student_grades WHERE subject = 'Math' ALLOW FILTERING;
```

<img width="681" height="147" alt="Screenshot 2026-05-02 at 21 51 12" src="https://github.com/user-attachments/assets/067aaa1c-47d7-4fcc-8ef5-b5dae073c364" />

Вывод:

Запрос по неключевому полю subject без ALLOW FILTERING не выполняется, потому что Cassandra не знает, в какой партиции искать данные.
ALLOW FILTERING разрешает такой поиск, но он может быть медленным, так как Cassandra вынуждена просматривать больше данных.


