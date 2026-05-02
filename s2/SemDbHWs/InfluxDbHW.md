# InfluxDB — Домашнее задание

### Данные для вставки

```text
current,motor_id=M-1001,type=induction,load=high value=145.5
current,motor_id=M-1001,type=induction,load=high value=148.2
current,motor_id=M-1001,type=induction,load=high value=151.7
current,motor_id=M-1002,type=synchronous,load=medium value=98.4
current,motor_id=M-1002,type=synchronous,load=medium value=102.9
current,motor_id=M-1003,type=induction,load=low value=65.1
pressure,pipe_id=MP-01,section=main,zone=A value=4.2
pressure,pipe_id=MP-01,section=main,zone=A value=4.6
pressure,pipe_id=MP-01,section=main,zone=A value=5.1
pressure,pipe_id=MP-02,section=secondary,zone=B value=2.8
pressure,pipe_id=MP-02,section=secondary,zone=B value=3.0
pressure,pipe_id=MP-03,section=main,zone=C value=6.4
temperature,sensor_id=T-01,location=workshop-1,equipment=compressor value=72.5
temperature,sensor_id=T-01,location=workshop-1,equipment=compressor value=75.3
temperature,sensor_id=T-02,location=workshop-2,equipment=pump value=58.9
temperature,sensor_id=T-02,location=workshop-2,equipment=pump value=61.4
vibration,motor_id=M-1001,axis=x,severity=normal value=2.1
vibration,motor_id=M-1001,axis=y,severity=warning value=4.8
vibration,motor_id=M-1002,axis=x,severity=normal value=1.9
vibration,motor_id=M-1003,axis=z,severity=critical value=8.7
```

### Базовые запросы

Просмотреть все данные за последние 30 минут

```bash
from(bucket: "mybucket")
  |> range(start: -30m)
```

<img width="1438" height="568" alt="Screenshot 2026-05-02 at 18 29 33" src="https://github.com/user-attachments/assets/8f0893c7-0353-402e-bd09-038516672ca5" />

Посмотреть измерения только 1 датчика (только ток электродвигателя M-1001)

```bash
from(bucket: "mybucket")
  |> range(start: -30m)
  |> filter(fn: (r) => r._measurement == "current")
  |> filter(fn: (r) => r.motor_id == "M-1001")
```

<img width="1448" height="658" alt="Screenshot 2026-05-02 at 18 30 23" src="https://github.com/user-attachments/assets/444a05e4-f185-48d9-82d7-ab1f7679b917" />

Максимальное значение на 1 датчике (максимальный ток электродвигателя M-1001)

```bash
from(bucket: "mybucket")
  |> range(start: -30m)
  |> filter(fn: (r) => r._measurement == "current")
  |> filter(fn: (r) => r.motor_id == "M-1001")
  |> max()
```

<img width="1446" height="649" alt="Screenshot 2026-05-02 at 18 32 45" src="https://github.com/user-attachments/assets/676daa9c-8018-41e6-a45c-767e8eb9a67e" />

Среднее значение на датчике (Средний ток на M-1001)

```bash
from(bucket: "mybucket")
  |> range(start: -30m)
  |> filter(fn: (r) => r._measurement == "current")
  |> filter(fn: (r) => r.motor_id == "M-1001")
  |> mean()
```

<img width="1449" height="602" alt="Screenshot 2026-05-02 at 18 34 56" src="https://github.com/user-attachments/assets/c464012c-5958-466e-887b-ce47b11025eb" />

###  2-3 аналитических запроса с фильтром по значению

1) Найти случаи повышенного тока больше 140 А

```bash
from(bucket: "mybucket")
  |> range(start: -30m)
  |> filter(fn: (r) => r._measurement == "current")
  |> filter(fn: (r) => r._value > 140.0)
```

<img width="1437" height="596" alt="Screenshot 2026-05-02 at 18 37 26" src="https://github.com/user-attachments/assets/e5066285-b33d-4d12-8cd7-c7b00b4b5bf3" />

<img width="1412" height="383" alt="Screenshot 2026-05-02 at 18 37 39" src="https://github.com/user-attachments/assets/0d696dbc-e01e-4fc3-bbe2-4ff2f17d5330" />

<img width="1426" height="424" alt="Screenshot 2026-05-02 at 18 37 55" src="https://github.com/user-attachments/assets/43322994-7b46-435d-b458-e4ec8017b3ae" />


2) Найти давление > 5 бар

```bash
from(bucket: "mybucket")
  |> range(start: -30m)
  |> filter(fn: (r) => r._measurement == "pressure")
  |> filter(fn: (r) => r._value > 5.0)
```

<img width="1445" height="587" alt="Screenshot 2026-05-02 at 18 39 29" src="https://github.com/user-attachments/assets/4f9293e5-1f4e-4737-b4dd-b36faab35405" />


3) Найти критическую вибрацию выше 5

```bash
from(bucket: "mybucket")
  |> range(start: -30m)
  |> filter(fn: (r) => r._measurement == "vibration")
  |> filter(fn: (r) => r._value > 5.0)
```

<img width="1446" height="576" alt="Screenshot 2026-05-02 at 18 40 59" src="https://github.com/user-attachments/assets/73a659b0-9262-4697-acca-05f047665a34" />

### Запрос на агрегацию данных

Среднее значение по каждому двигателю

```bash
from(bucket: "mybucket")
  |> range(start: -30m)
  |> filter(fn: (r) => r._measurement == "current")
  |> group(columns: ["motor_id"])
  |> mean()
```

<img width="1439" height="614" alt="Screenshot 2026-05-02 at 18 43 07" src="https://github.com/user-attachments/assets/10a76819-e79b-47ee-90fc-0f5ed6b3c8f4" />

### Создание Dashboard с 1-2 графиками

<img width="1166" height="634" alt="Screenshot 2026-05-02 at 18 52 10" src="https://github.com/user-attachments/assets/220bbc6f-4942-48f1-b76b-446f7f6c9d44" />

<img width="1251" height="671" alt="Screenshot 2026-05-02 at 18 52 21" src="https://github.com/user-attachments/assets/8d196c90-4963-43a9-9f65-1da33101d482" />









