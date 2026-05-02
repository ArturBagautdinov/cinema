# Домашнее задание — MongoDB

Создать минимум 3 коллекции, хотя бы 2 из которых связаны `ObjectId`, хотя бы 1 из документов в коллекции хранят JSON объекты либо массивы

### 3 коллекции:

users

albums

tracks

### Связи через ObjectId:

tracks.artistId → users._id

tracks.albumId → albums._id

albums.artistId → users._id

### вложенные JSON-объекты и массивы:

users.subscription

users.favoriteGenres

albums.tags

tracks.stats

### создание пользователей:

```bash
const timurId = new ObjectId();
const amirId = new ObjectId();
const alinaId = new ObjectId();

db.users.insertMany([
  {
    _id: timurId,
    username: "timur",
    email: "timur@example.com",
    country: "Russia",
    age: 21,
    subscription: {
      plan: "free",
      months: 3,
      autoRenewal: false
    },
    favoriteGenres: ["Hip-Hop", "Rap", "Electronic"],
    createdAt: new Date()
  },
  {
    _id: amirId,
    username: "amir",
    email: "amir@example.com",
    country: "Russia",
    age: 23,
    subscription: {
      plan: "premium",
      months: 12,
      autoRenewal: true
    },
    favoriteGenres: ["Rock", "Hip-Hop"],
    isArtist: true,
    createdAt: new Date()
  },
  {
    _id: alinaId,
    username: "alina",
    email: "alina@example.com",
    country: "Kazakhstan",
    age: 20,
    subscription: {
      plan: "free",
      months: 1,
      autoRenewal: false
    },
    favoriteGenres: ["Pop", "Electronic"],
    isArtist: true,
    createdAt: new Date()
  }
]);
```
<img width="459" height="877" alt="Screenshot 2026-05-02 at 14 06 06" src="https://github.com/user-attachments/assets/8cb67b1b-a5f4-4884-a990-ee7f9c7eb97b" />

### Создание альбомов

```bash
const album1Id = new ObjectId();
const album2Id = new ObjectId();
const album3Id = new ObjectId();

db.albums.insertMany([
  {
    _id: album1Id,
    title: "City Lights",
    artistId: amirId,
    year: 2024,
    tags: ["urban", "night", "rap"],
    info: {
      label: "Independent",
      language: "Russian"
    }
  },
  {
    _id: album2Id,
    title: "Digital Dreams",
    artistId: alinaId,
    year: 2025,
    tags: ["electronic", "future", "synth"],
    info: {
      label: "Future Sound",
      language: "English"
    }
  },
  {
    _id: album3Id,
    title: "Old School Beats",
    artistId: amirId,
    year: 2023,
    tags: ["classic", "hip-hop"],
    info: {
      label: "Independent",
      language: "Russian"
    }
  }
]);
```

<img width="405" height="693" alt="Screenshot 2026-05-02 at 14 06 39" src="https://github.com/user-attachments/assets/c014fa2d-89e7-4028-9def-e62448b08eb3" />

### Создание треков

```bash
db.tracks.insertMany([
  {
    title: "Hello Bandits",
    description: "A dynamic rap track with energetic beat",
    durationMs: 180000,
    artistId: amirId,
    albumId: album1Id,
    genre: "Hip-Hop",
    stats: {
      listens: 1500,
      likes: 230
    },
    createdAt: new Date()
  },
  {
    title: "Night Ride",
    description: "Atmospheric city track",
    durationMs: 210000,
    artistId: amirId,
    albumId: album1Id,
    genre: "Rap",
    stats: {
      listens: 2400,
      likes: 410
    },
    createdAt: new Date()
  },
  {
    title: "Cyber Sky",
    description: "Electronic song with synth sound",
    durationMs: 195000,
    artistId: alinaId,
    albumId: album2Id,
    genre: "Electronic",
    stats: {
      listens: 3200,
      likes: 700
    },
    createdAt: new Date()
  },
  {
    title: "Future Bass",
    description: "Dance electronic composition",
    durationMs: 175000,
    artistId: alinaId,
    albumId: album2Id,
    genre: "Electronic",
    stats: {
      listens: 2800,
      likes: 640
    },
    createdAt: new Date()
  },
  {
    title: "Street Memory",
    description: "Old school hip-hop track",
    durationMs: 220000,
    artistId: amirId,
    albumId: album3Id,
    genre: "Hip-Hop",
    stats: {
      listens: 1100,
      likes: 180
    },
    createdAt: new Date()
  }
]);
```

<img width="471" height="948" alt="Screenshot 2026-05-02 at 14 07 48" src="https://github.com/user-attachments/assets/bedb86fe-5d35-4600-9c06-7f885ccbbc70" />

Все данные:

```bash
db.users.find().pretty();
db.albums.find().pretty();
db.tracks.find().pretty();
```

<img width="431" height="846" alt="Screenshot 2026-05-02 at 14 09 54" src="https://github.com/user-attachments/assets/7186da54-2fa7-4ee5-ba06-b9a34e4cb58f" />


 ### Написать 2 `find` запроса, хотя бы 1 с projection (`{ field1: 0, field2: 1 }`)

 Find запрос 1: Найти пользователей из России

```bash
db.users.find({ country: "Russia" });
```

<img width="523" height="358" alt="Screenshot 2026-05-02 at 14 13 26" src="https://github.com/user-attachments/assets/2f8e2cb8-e41f-498a-9eb6-ca5a76191a06" />

Find запрос 2 с projection: Найти треки жанра Electronic и вывести только название, жанр и прослушивания

```bash
db.tracks.find(
  { genre: "Electronic" },
  {
    _id: 0,
    title: 1,
    genre: 1,
    "stats.listens": 1
  }
);
```

<img width="544" height="211" alt="Screenshot 2026-05-02 at 14 15 37" src="https://github.com/user-attachments/assets/d0b3bffc-2e7c-49c3-baba-c0984e4be657" />

### Написать 2 `update` запроса

Update запрос 1: Обновить подписку пользователя Timur

```bash
db.users.updateOne(
  { username: "timur" },
  {
    $set: {
      "subscription.plan": "premium",
      "subscription.months": 12,
      "subscription.autoRenewal": true
    }
  }
);
```
<img width="324" height="261" alt="Screenshot 2026-05-02 at 14 17 44" src="https://github.com/user-attachments/assets/f397faef-0624-490a-bee8-8493f10bd734" />

<img width="527" height="224" alt="Screenshot 2026-05-02 at 14 18 06" src="https://github.com/user-attachments/assets/315fa461-2239-4bb0-bd1e-f208a905e00a" />

Update запрос 2: Увеличить количество прослушиваний у всех Hip-Hop треков

```bash
db.tracks.updateMany(
  { genre: "Hip-Hop" },
  {
    $inc: {
      "stats.listens": 500
    },
    $currentDate: {
      updatedAt: true
    }
  }
);
```

<img width="269" height="281" alt="Screenshot 2026-05-02 at 14 19 22" src="https://github.com/user-attachments/assets/43823a6f-8b65-4355-8032-76c9f78b8f65" />

<img width="400" height="363" alt="Screenshot 2026-05-02 at 14 19 45" src="https://github.com/user-attachments/assets/31ae2ad4-fb5a-4178-ae67-a55a3aa80139" />

### Написать 1 любой запрос с `aggregate`: Посчитать количество треков и общую длительность по каждому альбому

```bash
db.tracks.aggregate([
  {
    $group: {
      _id: "$albumId",
      trackCount: { $sum: 1 },
      totalDurationMs: { $sum: "$durationMs" },
      averageListens: { $avg: "$stats.listens" }
    }
  },
  {
    $lookup: {
      from: "albums",
      localField: "_id",
      foreignField: "_id",
      as: "album"
    }
  },
  {
    $unwind: "$album"
  },
  {
    $project: {
      _id: 0,
      albumTitle: "$album.title",
      trackCount: 1,
      totalDurationMs: 1,
      averageListens: 1
    }
  },
  {
    $sort: {
      totalDurationMs: -1
    }
  }
]);
```

<img width="400" height="796" alt="Screenshot 2026-05-02 at 14 23 24" src="https://github.com/user-attachments/assets/e2484488-f433-46fd-aad9-e2098934dfbb" />
