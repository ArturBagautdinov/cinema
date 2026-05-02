**ДЗ**

Задать структуру:

```
CREATE (alex:User {name: "Alex"}),
       (maria:User {name: "Maria"}),
       (john:User {name: "John"})

CREATE (inception:Movie {title: "Inception"}),
       (matrix:Movie {title: "The Matrix"})

MATCH (a:User {name: "Alex"}), (m:User {name: "Maria"})
CREATE (a)-[:FRIENDS]->(m)

MATCH (a:User {name: "Alex"}), (i:Movie {title: "Inception"})
CREATE (a)-[:WATCHED {rating: 5}]->(i)
```

<img width="1460" height="332" alt="Screenshot 2026-05-02 at 12 37 53" src="https://github.com/user-attachments/assets/9e627e4c-fa47-476f-923d-061bee2dd073" />

<img width="1448" height="293" alt="Screenshot 2026-05-02 at 12 38 57" src="https://github.com/user-attachments/assets/30c2fe4f-8776-49d3-a79d-c885332496d8" />

## Выполнить запросы:

- Найти всех друзей Алекса

```bash
MATCH (:User {name: "Alex"})-[:FRIENDS]->(friend:User)
RETURN friend.name AS friend;
```

<img width="1462" height="763" alt="Screenshot 2026-05-02 at 12 44 54" src="https://github.com/user-attachments/assets/acf69a9e-e85c-48ca-8612-aaf9100a7a62" />

- Найти фильмы, которые смотрели друзья Алекса, но не смотрел сам Алекс

```bash
MATCH (:User {name: "Alex"})-[:FRIENDS]->(friend:User)-[:WATCHED]->(movie:Movie)
WHERE NOT EXISTS {
    MATCH (:User {name: "Alex"})-[:WATCHED]->(movie)
}
RETURN movie.title AS movie;
```

<img width="1462" height="147" alt="Screenshot 2026-05-02 at 12 45 54" src="https://github.com/user-attachments/assets/927086ad-7d5a-4b60-884c-10f8418df40d" />

таких фильмов нет

весь граф:

```bash
MATCH (n)
RETURN n;
```

<img width="1041" height="571" alt="Screenshot 2026-05-02 at 12 48 18" src="https://github.com/user-attachments/assets/38bbf01e-cba1-478a-a4b3-ed96417eb214" />

пользователи, фильмы и связи:

```bash
MATCH (n)-[r]->(m)
RETURN n, r, m;
```

<img width="1046" height="582" alt="Screenshot 2026-05-02 at 12 49 14" src="https://github.com/user-attachments/assets/163a4f13-9ef8-41b8-9d0c-a03b01c31061" />

## Сравнить:

- Написать аналогичный запрос на SQL

- Сравнить сложность запросов

SQL-запрос 1: найти всех друзей Алекса

```sql
SELECT f.name AS friend
FROM users a
JOIN friendships fr ON fr.user_id = a.id
JOIN users f ON f.id = fr.friend_id
WHERE a.name = 'Alex';
```

```bash
MATCH (:User {name: "Alex"})-[:FRIENDS]->(friend:User)
RETURN friend.name AS friend;
```

В Cypher запрос короче и ближе к естественному описанию графа:

Alex → FRIENDS → friend

В SQL нужно явно соединять таблицу пользователей с таблицей дружбы и снова с таблицей пользователей.

SQL-запрос 2: найти фильмы, которые смотрели друзья Алекса, но не смотрел сам Алекс

```sql
SELECT DISTINCT m.title AS movie
FROM users alex
JOIN friendships fr ON fr.user_id = alex.id
JOIN users friend ON friend.id = fr.friend_id
JOIN watched fw ON fw.user_id = friend.id
JOIN movies m ON m.id = fw.movie_id
WHERE alex.name = 'Alex'
  AND NOT EXISTS (
      SELECT 1
      FROM watched aw
      WHERE aw.user_id = alex.id
        AND aw.movie_id = m.id
  );
```

```bash
MATCH (:User {name: "Alex"})-[:FRIENDS]->(friend:User)-[:WATCHED]->(movie:Movie)
WHERE NOT EXISTS {
    MATCH (:User {name: "Alex"})-[:WATCHED]->(movie)
}
RETURN movie.title AS movie;
```

Cypher явно описывает путь по графу:

Alex → FRIENDS → friend → WATCHED → movie

SQL-запрос получается длиннее, потому что связи между сущностями представлены не напрямую, а через промежуточные таблицы:

users

friendships

users

watched

movies

Для графовых задач, где нужно искать связи, друзей, рекомендации, общие интересы и пути между объектами, Cypher проще читать и писать.

SQL лучше подходит для табличных данных, отчётов, агрегаций и классических бизнес-запросов.
