package ru.queue;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

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

        try (
                Connection workConnection = DriverManager.getConnection(URL, USER, PASSWORD);
                Connection listenConnection = DriverManager.getConnection(URL, USER, PASSWORD)
        ) {
            listenForNotifications(listenConnection);

            while (true) {
                Task task = takeTask(workConnection, workerId);

                if (task == null) {
                    System.out.println(workerId + " has no tasks. Waiting for NOTIFY...");
                    waitForNotify(listenConnection, workerId);
                    continue;
                }

                processTask(workConnection, workerId, task);
            }
        }
    }

    private static void listenForNotifications(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("LISTEN tasks_channel");
        }
    }

    private static void waitForNotify(Connection connection, String workerId) throws SQLException {
        PGConnection pgConnection = connection.unwrap(PGConnection.class);

        while (true) {
            PGNotification[] notifications = pgConnection.getNotifications(10_000);

            if (notifications != null && notifications.length > 0) {
                for (PGNotification notification : notifications) {
                    System.out.printf(
                            "%s received NOTIFY: channel=%s, parameter=%s%n",
                            workerId,
                            notification.getName(),
                            notification.getParameter()
                    );
                }
                return;
            }

            System.out.println(workerId + " still waiting...");
            return;
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
                RETURNING
                    t.task_id,
                    t.task_type,
                    t.priority,
                    t.payload,
                    t.attempts,
                    t.max_attempts
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
                            rs.getString("payload"),
                            rs.getInt("attempts"),
                            rs.getInt("max_attempts")
                    );

                    connection.commit();

                    System.out.printf(
                            "%s took task_id=%d, type=%s, priority=%d, attempts=%d/%d%n",
                            workerId,
                            task.taskId(),
                            task.taskType(),
                            task.priority(),
                            task.attempts(),
                            task.maxAttempts()
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

        boolean success = RANDOM.nextInt(100) >= 25;

        if (success) {
            completeTask(connection, workerId, task.taskId());
        } else {
            retryOrFailTask(connection, workerId, task.taskId(), "Random processing error");
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

    private static void retryOrFailTask(Connection connection, String workerId, long taskId, String error) {
        String sql = """
                UPDATE tasks
                SET attempts = attempts + 1,
                    status = CASE
                        WHEN attempts + 1 >= max_attempts THEN 'FAILED'::task_status
                        ELSE 'READY'::task_status
                    END,
                    scheduled_at = CASE
                        WHEN attempts + 1 >= max_attempts THEN scheduled_at
                        ELSE now() + (
                            interval '5 minutes' * power(2, attempts)
                        )
                    END,
                    completed_at = CASE
                        WHEN attempts + 1 >= max_attempts THEN now()
                        ELSE NULL
                    END,
                    worker_id = CASE
                        WHEN attempts + 1 >= max_attempts THEN worker_id
                        ELSE NULL
                    END,
                    updated_at = now(),
                    last_error = ?
                WHERE task_id = ?
                  AND worker_id = ?
                  AND status = 'RUNNING'
                RETURNING status, attempts, max_attempts, scheduled_at
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, error);
            ps.setLong(2, taskId);
            ps.setString(3, workerId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    int attempts = rs.getInt("attempts");
                    int maxAttempts = rs.getInt("max_attempts");
                    Timestamp scheduledAt = rs.getTimestamp("scheduled_at");

                    if ("FAILED".equals(status)) {
                        System.out.printf(
                                "%s finally failed task_id=%d, attempts=%d/%d%n",
                                workerId,
                                taskId,
                                attempts,
                                maxAttempts
                        );
                    } else {
                        System.out.printf(
                                "%s returned task_id=%d to READY, attempts=%d/%d, scheduled_at=%s%n",
                                workerId,
                                taskId,
                                attempts,
                                maxAttempts,
                                scheduledAt
                        );
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println(workerId + " retry/fail task error: " + e.getMessage());
        }
    }

    private record Task(
            long taskId,
            String taskType,
            int priority,
            String payload,
            int attempts,
            int maxAttempts
    ) {
    }
}