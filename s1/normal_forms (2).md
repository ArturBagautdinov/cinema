Таблица User

CREATE TABLE Cinema.User (
UniqueID SERIAL PRIMARY KEY,
id VARCHAR(50) UNIQUE NOT NULL,
name VARCHAR(100),
role VARCHAR(50),
email VARCHAR(100) UNIQUE,
registrationDate DATE,
phone VARCHAR(20)
);

Проблема: Избыточность role, аномалия обновления
role может дублироваться и потенциально тянуться в отдельную таблицу, если есть ограниченный набор ролей (например: admin, customer).

В остальном таблица в 3НФ, зависимости полностью от первичного ключа (UniqueID).

Решение:
Сделать отдельную справочную таблицу UserRole для ролей.

CREATE TABLE Cinema.UserRole (
role_id SERIAL PRIMARY KEY,
role_name VARCHAR(50) UNIQUE NOT NULL
);

А в User:

role_id INT REFERENCES Cinema.UserRole(role_id)

Таблицы Subscription, Rental, Purchase

Subscription хранит plan_type и price.
Rental хранит price для каждой аренды.
Purchase хранит price для каждой покупки.

Проблема:
Если один и тот же план/аренда/покупка повторяется, цена дублируется.
Любое изменение цены требует обновления всех записей → аномалия обновления.

Решение:
отдельные справочники для каждого типа

CREATE TABLE Cinema.SubscriptionPlan (
plan_id SERIAL PRIMARY KEY,
plan_type VARCHAR(50),
price NUMERIC(10,2)
);

CREATE TABLE Cinema.RentalPrice (
rental_id SERIAL PRIMARY KEY,
movie_id INT REFERENCES Cinema.Movie(UniqueID),
price NUMERIC(10,2)
);

CREATE TABLE Cinema.PurchasePrice (
purchase_id SERIAL PRIMARY KEY,
movie_id INT REFERENCES Cinema.Movie(UniqueID),
price NUMERIC(10,2)
);

Проблемы: проблемы обновления
Subscription: status
Rental: status
Movie: ageRating, country, language, genre
Director: country
Purchase: paymentMethod

Решение: добавить таблицы status, ageRating итд, а в исходных таблица оставить только ссылки на таблицы: status_id, country_id итд.

