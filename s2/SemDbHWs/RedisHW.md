## Задание 1. Hash — данные о студентах

Создайте 3 студентов, используя Hash. Каждый ключ — `student:<id>`, поля: `name`, `group`, `gpa`.
Проверьте, что данные записались

Создание:

```bash
HSET student:1 name "Ivan Petrov" group "IKBO-01" gpa 4.7
HSET student:2 name "Anna Smirnova" group "IKBO-01" gpa 4.2
HSET student:3 name "Pavel Sidorov" group "IKBO-02" gpa 3.9
```

<img width="550" height="90" alt="Screenshot 2026-05-02 at 10 30 18" src="https://github.com/user-attachments/assets/6620aee3-dac2-4409-97ef-3f33be2ab00b" />

Проверка:

```bash
HGETALL student:1
HGETALL student:2
HGETALL student:3
```

<img width="293" height="301" alt="Screenshot 2026-05-02 at 10 30 59" src="https://github.com/user-attachments/assets/5baf7690-fa29-4318-9af4-93e0cd78387a" />

## Задание 2. Sorted Set — лидерборд по GPA

Создайте рейтинг студентов по среднему баллу. В Sorted Set score = GPA, member = имя.
Выведите топ-3 по убыванию GPA

Создание рейтинга, где score = GPA, member = имя:

```bash
ZADD students:gpa 4.7 "Ivan Petrov"
ZADD students:gpa 4.2 "Anna Smirnova"
ZADD students:gpa 3.9 "Pavel Sidorov"
```

<img width="398" height="89" alt="Screenshot 2026-05-02 at 10 34 45" src="https://github.com/user-attachments/assets/6b80e25e-bcb0-47f7-85be-d8cc096c07d9" />

Вывод топ-3 по убыванию GPA:

```bash
ZREVRANGE students:gpa 0 2 WITHSCORES
```

<img width="391" height="135" alt="Screenshot 2026-05-02 at 10 35 30" src="https://github.com/user-attachments/assets/a180f24b-bceb-4343-94ba-392f23959bac" />

## Задание 3. List — очередь задач

Добавьте 5 задач в очередь через `RPUSH`

Заберите 3 задачи из очереди (FIFO — первый вошёл, первый вышел)

Добавление:

```bash
RPUSH task_queue "Task 1"
RPUSH task_queue "Task 2"
RPUSH task_queue "Task 3"
RPUSH task_queue "Task 4"
RPUSH task_queue "Task 5"
```

<img width="313" height="168" alt="Screenshot 2026-05-02 at 10 38 03" src="https://github.com/user-attachments/assets/cdf4c3f7-a620-4bb7-8377-efd1ce1351e6" />

Проверка очереди:

```bash
LRANGE task_queue 0 -1
```

<img width="294" height="109" alt="Screenshot 2026-05-02 at 10 38 58" src="https://github.com/user-attachments/assets/859a6d97-bb10-42f5-aa21-ede7fc8f5a71" />

Забираем 3 задачи по FIFO, то есть с начала списка:

```bash
LPOP task_queue
LPOP task_queue
LPOP task_queue
```

<img width="253" height="116" alt="Screenshot 2026-05-02 at 10 40 23" src="https://github.com/user-attachments/assets/7ec48125-e56b-4861-bc45-259964142b3b" />

Проверка того, что осталось:

```bash
LRANGE task_queue 0 -1
```

<img width="286" height="65" alt="Screenshot 2026-05-02 at 10 41 45" src="https://github.com/user-attachments/assets/f6b0b757-96f7-48f5-ad0d-f95aa8d2e746" />

## Задание 4. TTL — время жизни ключа

Создайте ключ с TTL 10 секунд

Сразу проверьте оставшееся время

Подождите и попробуйте получить значение

Создание ключа с TTL 10 секунд:

```bash
SET temp:key "temporary value" EX 10
```

<img width="387" height="32" alt="Screenshot 2026-05-02 at 10 46 37" src="https://github.com/user-attachments/assets/873c797d-a34b-4aea-bc2b-766641203363" />

Сразу проверка оставшегося времени:

```bash
TTL temp:key
```

<img width="229" height="33" alt="Screenshot 2026-05-02 at 10 47 42" src="https://github.com/user-attachments/assets/764fdf3e-b99f-4984-aa4e-4764952b8901" />

Получение значения:

```bash
GET temp:key
```

<img width="216" height="36" alt="Screenshot 2026-05-02 at 10 48 31" src="https://github.com/user-attachments/assets/3da61c7b-9277-4020-9b30-be4dd2652eb2" />

По истечению 10 секунд попытка получить значение:

<img width="237" height="34" alt="Screenshot 2026-05-02 at 10 49 43" src="https://github.com/user-attachments/assets/0a28bc67-9974-40b3-a992-d1e9aa06bb97" />

## Задание 5. Транзакция MULTI/EXEC

Смоделируйте «перевод» 1 балла GPA от студента 1 к студенту 2

До транзакции:

```bash
HGET student:1 gpa
HGET student:2 gpa
```

<img width="282" height="62" alt="Screenshot 2026-05-02 at 10 53 22" src="https://github.com/user-attachments/assets/567e8bf2-a25f-4a8c-b102-620be831c2c3" />

Транзакция:

```bash
MULTI
HINCRBYFLOAT student:1 gpa -1
HINCRBYFLOAT student:2 gpa 1
ZINCRBY students:gpa -1 "Ivan Petrov"
ZINCRBY students:gpa 1 "Anna Smirnova"
EXEC
```

<img width="433" height="235" alt="Screenshot 2026-05-02 at 10 54 41" src="https://github.com/user-attachments/assets/36db6cb3-e8c0-4dff-9334-b31bd473daa3" />

Проверка после транзакции:

```bash
HGETALL student:1
HGETALL student:2
ZREVRANGE students:gpa 0 2 WITHSCORES
```

<img width="395" height="317" alt="Screenshot 2026-05-02 at 10 56 22" src="https://github.com/user-attachments/assets/19130a57-592a-4806-830b-845b21a5ad5c" />

## Задание 6 (бонус). Pub/Sub

Откройте **два** терминала с `redis-cli`.

**Терминал 1** — подписчик:

```bash
docker exec -it redis redis-cli
```

```
SUBSCRIBE news
```

**Терминал 2** — издатель:

```bash
docker exec -it redis redis-cli
```

```
PUBLISH news "Hello from Redis!"
PUBLISH news "Second message"
```

<img width="527" height="180" alt="Screenshot 2026-05-02 at 10 59 45" src="https://github.com/user-attachments/assets/a71315a0-5dd0-4804-b767-17f19793ef38" />
<br>
<img width="513" height="114" alt="Screenshot 2026-05-02 at 11 00 00" src="https://github.com/user-attachments/assets/d9f316f0-3a90-49af-973f-5dd029460bc7" />
