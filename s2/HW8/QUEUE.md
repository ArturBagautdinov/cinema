## 1. Проектирование схемы БД. Создайте таблицу tasks, которая будет служить хранилищем очереди.

Очередь будет хранить фоновые задачи:

RECALCULATE_RECOMMENDATIONS — пересчёт рекомендаций пользователя

SEND_PURCHASE_RECEIPT — отправка чека после покупки

CHECK_RENTAL_EXPIRATION — проверка срока аренды

Приоритеты:

priority = 0   обычная задача

priority = 100 критическая задача

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'task_status') THEN
        CREATE TYPE task_status AS ENUM ('READY', 'RUNNING', 'COMPLETED', 'FAILED');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS queue_business_event
(
    event_id   BIGSERIAL PRIMARY KEY,
    event_name TEXT        NOT NULL,
    entity_type TEXT       NOT NULL,
    entity_id BIGINT,
    meta       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS tasks
(
    task_id      BIGSERIAL PRIMARY KEY,

    task_type    TEXT        NOT NULL,
    payload      JSONB       NOT NULL DEFAULT '{}'::jsonb,

    priority     INT         NOT NULL DEFAULT 0 CHECK (priority IN (0, 100)),
    status       task_status NOT NULL DEFAULT 'READY',

    attempts     INT         NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts INT         NOT NULL DEFAULT 5 CHECK (max_attempts > 0),

    scheduled_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,

    worker_id    TEXT,
    last_error   TEXT,

    business_event_id BIGINT REFERENCES queue_business_event(event_id)
);

CREATE INDEX IF NOT EXISTS ix_tasks_ready_priority
    ON tasks (priority DESC, scheduled_at ASC, created_at ASC, task_id ASC)
    WHERE status = 'READY';

CREATE INDEX IF NOT EXISTS ix_tasks_running
    ON tasks (updated_at)
    WHERE status = 'RUNNING';

CREATE INDEX IF NOT EXISTS ix_tasks_completed_at
    ON tasks (completed_at)
    WHERE status IN ('COMPLETED', 'FAILED');
```

## Реализация Продьюсера (1 сервис), Консьюмеров (2 сервиса)

Producer в цикле создаёт события.

80% задач — обычные, priority = 0.

20% задач — критические, priority = 100.

Задача вставляется в одной транзакции с фиктивной бизнес-логикой. Здесь фиктивная бизнес-логика — вставка записи в queue_business_event.

Воркер берет задачу, переводит её в статус Running, «обрабатывает» (имитация через sleep) и помечает как Completed или Failed

Producer:

```java
package ru.queue;

import java.sql.*;
import java.time.Instant;
import java.util.Random;

public class Producer {

    private static final String URL = "jdbc:postgresql://localhost:5484/postgres";
    private static final String USER = "postgres";
    private static final String PASSWORD = "Fhnehbr2021";

    private static final Random RANDOM = new Random();

    public static void main(String[] args) throws Exception {
        int rps = args.length > 0 ? Integer.parseInt(args[0]) : 10;

        System.out.println("Producer started. RPS = " + rps);

        long delayMillis = Math.max(1, 1000 / rps);

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            while (true) {
                createBusinessEventAndTask(connection);
                Thread.sleep(delayMillis);
            }
        }
    }

    private static void createBusinessEventAndTask(Connection connection) {
        String insertBusinessEventSql = """
                INSERT INTO queue_business_event(event_name, entity_type, entity_id, meta)
                VALUES (?, ?, ?, ?::jsonb)
                RETURNING event_id
                """;

        String insertTaskSql = """
                INSERT INTO tasks(task_type, payload, priority, status, scheduled_at, business_event_id)
                VALUES (?, ?::jsonb, ?, 'READY', now(), ?)
                """;

        try {
            connection.setAutoCommit(false);

            String eventName = randomEventName();
            String entityType = randomEntityType();
            long entityId = RANDOM.nextLong(1, 10_000);

            long businessEventId;

            try (PreparedStatement ps = connection.prepareStatement(insertBusinessEventSql)) {
                ps.setString(1, eventName);
                ps.setString(2, entityType);
                ps.setLong(3, entityId);
                ps.setString(4, """
                        {
                          "source": "producer",
                          "createdBy": "homework-demo"
                        }
                        """);

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    businessEventId = rs.getLong("event_id");
                }
            }

            int priority = RANDOM.nextInt(100) < 20 ? 100 : 0;
            String taskType = taskTypeByEvent(eventName);

            String payloadJson = """
                    {
                      "eventName": "%s",
                      "entityType": "%s",
                      "entityId": %d,
                      "generatedAt": "%s"
                    }
                    """.formatted(eventName, entityType, entityId, Instant.now());

            try (PreparedStatement ps = connection.prepareStatement(insertTaskSql)) {
                ps.setString(1, taskType);
                ps.setString(2, payloadJson);
                ps.setInt(3, priority);
                ps.setLong(4, businessEventId);
                ps.executeUpdate();
            }

            connection.commit();

            System.out.printf(
                    "Created task: type=%s, priority=%d, business_event_id=%d%n",
                    taskType,
                    priority,
                    businessEventId
            );

        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                rollbackException.printStackTrace();
            }

            System.err.println("Producer error: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private static String randomEventName() {
        String[] events = {
                "VIEWING_FINISHED",
                "MOVIE_PURCHASED",
                "RENTAL_CREATED"
        };
        return events[RANDOM.nextInt(events.length)];
    }

    private static String randomEntityType() {
        String[] entities = {
                "viewing",
                "purchase",
                "rental"
        };
        return entities[RANDOM.nextInt(entities.length)];
    }

    private static String taskTypeByEvent(String eventName) {
        return switch (eventName) {
            case "VIEWING_FINISHED" -> "RECALCULATE_RECOMMENDATIONS";
            case "MOVIE_PURCHASED" -> "SEND_PURCHASE_RECEIPT";
            case "RENTAL_CREATED" -> "CHECK_RENTAL_EXPIRATION";
            default -> "UNKNOWN_TASK";
        };
    }
}
```
Worker:

```java
package ru.queue;

import java.sql.*;
import java.util.Random;
import java.util.UUID;

public class Worker {

    private static final String URL = "jdbc:postgresql://localhost:5484/postgres";
    private static final String USER = "postgres";
    private static final String PASSWORD = "Fhnehbr2021";

    private static final Random RANDOM = new Random();

    public static void main(String[] args) throws Exception {
        String workerId = args.length > 0 ? args[0] : "worker-" + UUID.randomUUID();

        System.out.println(workerId + " started");

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            while (true) {
                Task task = takeTask(connection, workerId);

                if (task == null) {
                    Thread.sleep(1000);
                    continue;
                }

                processTask(connection, workerId, task);
            }
        }
    }

    private static Task takeTask(Connection connection, String workerId) {
        String sql = """
                WITH picked AS (
                    SELECT task_id
                    FROM tasks
                    WHERE status = 'READY'
                      AND scheduled_at <= now()
                    ORDER BY priority DESC, scheduled_at ASC, created_at ASC, task_id ASC
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE tasks t
                SET status = 'RUNNING',
                    worker_id = ?,
                    started_at = now(),
                    updated_at = now()
                FROM picked
                WHERE t.task_id = picked.task_id
                RETURNING t.task_id, t.task_type, t.priority, t.payload
                """;

        try {
            connection.setAutoCommit(false);

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, workerId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        connection.commit();
                        return null;
                    }

                    Task task = new Task(
                            rs.getLong("task_id"),
                            rs.getString("task_type"),
                            rs.getInt("priority"),
                            rs.getString("payload")
                    );

                    connection.commit();

                    System.out.printf(
                            "%s took task_id=%d, type=%s, priority=%d%n",
                            workerId,
                            task.taskId(),
                            task.taskType(),
                            task.priority()
                    );

                    return task;
                }
            }

        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                rollbackException.printStackTrace();
            }

            System.err.println(workerId + " take task error: " + e.getMessage());
            return null;

        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private static void processTask(Connection connection, String workerId, Task task) throws InterruptedException {
        int processingTimeMs = task.priority() == 100
                ? RANDOM.nextInt(300, 800)
                : RANDOM.nextInt(1000, 3000);

        Thread.sleep(processingTimeMs);

        boolean success = RANDOM.nextInt(100) >= 15;

        if (success) {
            completeTask(connection, workerId, task.taskId());
        } else {
            failTask(connection, workerId, task.taskId(), "Random processing error");
        }
    }

    private static void completeTask(Connection connection, String workerId, long taskId) {
        String sql = """
                UPDATE tasks
                SET status = 'COMPLETED',
                    completed_at = now(),
                    updated_at = now(),
                    last_error = NULL
                WHERE task_id = ?
                  AND worker_id = ?
                  AND status = 'RUNNING'
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ps.setString(2, workerId);
            int updated = ps.executeUpdate();

            if (updated == 1) {
                System.out.printf("%s completed task_id=%d%n", workerId, taskId);
            }

        } catch (SQLException e) {
            System.err.println(workerId + " complete task error: " + e.getMessage());
        }
    }

    private static void failTask(Connection connection, String workerId, long taskId, String error) {
        String sql = """
                UPDATE tasks
                SET status = 'FAILED',
                    completed_at = now(),
                    updated_at = now(),
                    last_error = ?
                WHERE task_id = ?
                  AND worker_id = ?
                  AND status = 'RUNNING'
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, error);
            ps.setLong(2, taskId);
            ps.setString(3, workerId);
            int updated = ps.executeUpdate();

            if (updated == 1) {
                System.out.printf("%s failed task_id=%d%n", workerId, taskId);
            }

        } catch (SQLException e) {
            System.err.println(workerId + " fail task error: " + e.getMessage());
        }
    }

    private record Task(
            long taskId,
            String taskType,
            int priority,
            String payload
    ) {
    }
}
```

## Нагрузка и мониторинг Лага

Запустите продьюсера на высокую интенсивность (300 вставок в секунду), чтобы очередь начала расти.

```bash
mvn exec:java \
-Dexec.mainClass="ru.queue.Producer" \
-Dexec.args="300"
```

<img width="643" height="769" alt="Screenshot 2026-05-08 at 17 17 32" src="https://github.com/user-attachments/assets/a1c79145-de1f-49c2-830c-6951595900dc" />


Запуск двух воркеров:

```bash
mvn exec:java -Dexec.mainClass="ru.queue.Worker" -Dexec.args="worker-1"
```

<img width="668" height="378" alt="Screenshot 2026-05-08 at 17 18 21" src="https://github.com/user-attachments/assets/2d2916da-7911-456e-ba3e-e8101393b46b" />



```bash
mvn exec:java -Dexec.mainClass="ru.queue.Worker" -Dexec.args="worker-2"
```
<img width="684" height="378" alt="Screenshot 2026-05-08 at 17 18 45" src="https://github.com/user-attachments/assets/f8d4b7fc-8758-44b7-952d-b3cd8276ff97" />


Проверка роста очереди

```sql
SELECT count(*)
FROM tasks
WHERE status = 'READY';
```

<img width="246" height="474" alt="Screenshot 2026-05-08 at 17 20 20" src="https://github.com/user-attachments/assets/96a592f5-af12-4648-aa41-4034f811f438" />

Лаг очереди: Напишите SQL-запрос, который показывает разницу между now() и временем created_at самой старой задачи в статусе Ready. Это покажет, как долго задачи ждут выполнения.

```sql
SELECT
    count(*) AS ready_tasks,
    now() - MIN(created_at) AS queue_lag
FROM tasks
WHERE status = 'READY';
```

<img width="355" height="169" alt="Screenshot 2026-05-08 at 17 21 36" src="https://github.com/user-attachments/assets/816b3d1b-a8e2-49ce-b128-5dead9956a4b" />

В очереди сейчас 43703 необработанных задач

Самая старая задача ждёт обработки
уже 26 минуты 21 секунду.

и лаг продолжает расти:

<img width="336" height="148" alt="Screenshot 2026-05-08 at 17 27 48" src="https://github.com/user-attachments/assets/264eea4e-bd38-4890-9034-ea67cf541c0a" />



Пропускная способность: Посчитайте, сколько задач в секунду обрабатывают оба консьюмера суммарно.



<img width="446" height="161" alt="Screenshot 2026-05-08 at 17 24 13" src="https://github.com/user-attachments/assets/2b7f1f8f-9d05-4100-9525-8a9eae2649c6" />


Два worker-а вместе
обрабатывают примерно 3.65 задач/сек.

```sql
SELECT
    worker_id,
    COUNT(*) / 60.0 AS tasks_per_second
FROM tasks
WHERE status IN ('COMPLETED', 'FAILED')
  AND completed_at >= now() - interval '60 seconds'
GROUP BY worker_id
ORDER BY worker_id;
```

<img width="392" height="228" alt="Screenshot 2026-05-08 at 17 25 37" src="https://github.com/user-attachments/assets/30e2c791-d7af-4551-8612-523d63c09d4b" />

первый воркер примерно 1.83 задач в секунду, а второй - 1.76

Демонстрация приоритетов

```sql
SELECT
    task_id,
    priority,
    created_at,
    started_at,
    started_at - created_at AS waiting_time,
    worker_id
FROM tasks
WHERE priority = 100
  AND started_at IS NOT NULL
ORDER BY started_at DESC
LIMIT 20;
```

<img width="857" height="331" alt="Screenshot 2026-05-08 at 17 38 41" src="https://github.com/user-attachments/assets/2f53359c-d9f3-4ccb-997a-3af4cf77f1cb" />

Задачи с приоритетом 100 выполняются

```sql
SELECT
    task_id,
    priority,
    status,
    created_at,
    now() - created_at AS waiting_in_queue
FROM tasks
WHERE priority = 0
  AND status = 'READY'
ORDER BY created_at ASC
LIMIT 10;
```

<img width="626" height="391" alt="Screenshot 2026-05-08 at 21 24 58" src="https://github.com/user-attachments/assets/92cec3ed-0e5f-4791-834d-bf3edad338e4" />

Задачи с приоритетом 0 все еще ждут в очереди

## Дополнительно (за пару баллов)

Механизм Retry: Если задача завершилась ошибкой, воркер должен увеличить счетчик attempts и перенести scheduled_at на 5 минут в будущее (exponential backoff).

Оптимизация (Notify): Вместо постоянного опроса базы (polling) раз в секунду, используйте механизм LISTEN / NOTIFY, чтобы продьюсер «будил» консьюмеров при появлении новой задачи.

Борьба с Bloat: Настройте агрессивный autovacuum для таблицы очереди или запустите ручной VACUUM ANALYZE во время теста, чтобы увидеть, как «раздувание» таблицы влияет на время выборки задач.






