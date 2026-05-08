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
                RETURNING task_id
                """;

        String notifySql = """
                NOTIFY tasks_channel, 'new_task'
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

            long taskId;

            try (PreparedStatement ps = connection.prepareStatement(insertTaskSql)) {
                ps.setString(1, taskType);
                ps.setString(2, payloadJson);
                ps.setInt(3, priority);
                ps.setLong(4, businessEventId);

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    taskId = rs.getLong("task_id");
                }
            }

            try (Statement st = connection.createStatement()) {
                st.execute(notifySql);
            }

            connection.commit();

            System.out.printf(
                    "Created task_id=%d, type=%s, priority=%d, business_event_id=%d%n",
                    taskId,
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