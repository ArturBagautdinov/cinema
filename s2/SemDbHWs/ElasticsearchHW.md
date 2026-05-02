# Домашнее задание Elasticsearch

### 1. Поднять Elastic

<img width="1147" height="644" alt="Screenshot 2026-05-02 at 19 12 31" src="https://github.com/user-attachments/assets/228a4cc4-d930-4ea5-b08d-45c3ba4e7374" />

### 2. Создать индекс

```bash
curl -X PUT "http://localhost:9200/first_index" \
  -H "Content-Type: application/json" \
  -d '{
    "mappings": {
      "properties": {
        "title": {
          "type": "text",
          "analyzer": "russian"
        },
        "price": {
          "type": "float"
        },
        "available": {
          "type": "boolean"
        }
      }
    }
  }'
```

<img width="661" height="290" alt="Screenshot 2026-05-02 at 19 13 42" src="https://github.com/user-attachments/assets/fd6b86b3-3781-46d7-8b01-ff320f079ed8" />

### 3. Заполнить данными

```bash
curl -X PUT "http://localhost:9200/first_index/_doc/1" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Беспроводные наушники",
    "price": 59.99,
    "available": true
  }'
```

<img width="1157" height="147" alt="Screenshot 2026-05-02 at 19 14 59" src="https://github.com/user-attachments/assets/1eb3acd5-f3fd-44fa-8364-61b1598ccb2c" />

Добавить еще несколько документов через _bulk

```bash
curl -X POST "http://localhost:9200/first_index/_bulk" \
  -H "Content-Type: application/x-ndjson" \
  --data-binary '
{"index":{"_id":2}}
{"title":"Кабель USB","price":12.99,"available":true}
{"index":{"_id":3}}
{"title":"Кулер для процессора","price":200.14,"available":true}
{"index":{"_id":4}}
{"title":"Селфи-палка","price":61.6,"available":true}
{"index":{"_id":5}}
{"title":"Внешний аккумулятор","price":198.49,"available":true}
{"index":{"_id":6}}
{"title":"Смарт-часы","price":181.26,"available":true}
{"index":{"_id":7}}
{"title":"Мышь беспроводная","price":94.08,"available":false}
{"index":{"_id":8}}
{"title":"Внешний диск 2TB","price":177.49,"available":true}
{"index":{"_id":9}}
{"title":"Процессор","price":125.64,"available":false}
{"index":{"_id":10}}
{"title":"Видеокарта","price":238.9,"available":true}
{"index":{"_id":11}}
{"title":"Игровая клавиатура","price":231.37,"available":true}
{"index":{"_id":12}}
{"title":"Роутер","price":47.7,"available":true}
{"index":{"_id":13}}
{"title":"Монитор 24","price":105.82,"available":true}
{"index":{"_id":14}}
{"title":"Наушники проводные","price":25.5,"available":false}
{"index":{"_id":15}}
{"title":"USB-хаб","price":259.53,"available":true}
'
```

<img width="1512" height="658" alt="Screenshot 2026-05-02 at 19 16 54" src="https://github.com/user-attachments/assets/f9921372-cdb0-4bba-8b8b-c2486513c419" />

### 4. Написать 4 запроса (поиск по названию, фильтры, `match`, `range`, `term`, `bool`)

Запрос 1 (match) — поиск по названию: найти товары, в названии которых есть слово наушники.

```bash
curl -X GET "http://localhost:9200/first_index/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match": {
        "title": "наушники"
      }
    }
  }'
```

<img width="790" height="736" alt="Screenshot 2026-05-02 at 19 23 20" src="https://github.com/user-attachments/assets/a9976b4a-bc63-430e-8280-16ffa0b79779" />

Запрос 2 (term) - найти товары, которые есть в наличии

```bash
curl -X GET "http://localhost:9200/first_index/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": {
        "available": true
      }
    }
  }'
```

<img width="657" height="834" alt="Screenshot 2026-05-02 at 19 25 11" src="https://github.com/user-attachments/assets/c7378677-61cc-4839-be65-6f78fa54ce78" />

<img width="592" height="862" alt="Screenshot 2026-05-02 at 19 25 53" src="https://github.com/user-attachments/assets/0fedf122-c1fc-4756-b3ca-4441cbf2a1d3" />

Запрос 3 (range) - найти товары с ценой от 50 до 150 рублей

```bash
curl -X GET "http://localhost:9200/first_index/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "range": {
        "price": {
          "gte": 50,
          "lte": 150
        }
      }
    }
  }'
```

<img width="662" height="863" alt="Screenshot 2026-05-02 at 19 28 16" src="https://github.com/user-attachments/assets/2da1b45f-14d3-41e5-8b12-8906f49702fd" />

Запрос 4 (bool)

Найти товары, которые:

есть в наличии

стоят от 15 до 200

содержат в названии слово "Внешний"

```
curl -X GET "http://localhost:9200/first_index/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          {
            "match": {
              "title": "внешний"
            }
          }
        ],
        "filter": [
          {
            "term": {
              "available": true
            }
          },
          {
            "range": {
              "price": {
                "gte": 15,
                "lte": 200
              }
            }
          }
        ]
      }
    }
  }'
```

<img width="658" height="884" alt="Screenshot 2026-05-02 at 19 31 52" src="https://github.com/user-attachments/assets/2ea25f74-436e-474a-bd64-5cc1e48c88d4" />










