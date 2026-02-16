a. COUNT(), SUM(), AVG(), MIN(), MAX(), STRING_AGG()

// Count

Сколько актёров из США?
select count(*) as unitedStatesActorsCount from cinema.actor where country = 'USA'

Сколько фильмов 2025 года выпуска?
select count(*) as moviews2025Count from cinema.movie where release_year = '1994'


// Sum

Общая стоимость всех аренд?
select sum(price) as allRentalsPrice from cinema.rental

Общая стоимость всех подписок?
select sum(price) as allSubscriptionsPrice from cinema.subscription


// Avg

Средняя цена подписки?
select avg(price) as averageSubscriptionPrice from cinema.subscription

Средняя сумма покупок?
select avg(price) as averagePurchase from cinema.purchase


// Min

Самая дешёвая подписка?
select Min(price) as minSubscriptionPrice from cinema.subscription

Самая дорогой прокат фильма?
select Max(price) as maxRentalPrice from cinema.rental


// String_agg

Имена участников семейной группы с id = 1 через запятую?
select String_agg(u.name, ',') as familyMembers from cinema.users u inner join cinema.family_member fm on u.user_id = fm.user_id where fm.family_group_id = 1

Все жанры через запятую?
select string_agg(name, ',') as genres from cinema.genre


b. GROUP BY, HAVING

// Group by

Во скольких фильмах снимался актер?
select a.name, count(m.movie_id) as moviesCount from cinema.actor a inner join cinema.movie_actor ma on ma.actor_id = a.actor_id inner join cinema.movie m on m.movie_id = ma.movie_id group by a.name

Сколько фильмов снял каждый режиссёр?
select d.name, count(m.director_id) as moviesCount from cinema.director d inner join cinema.movie m on d.director_id = m.director_id group by d.name


// Having

Режиссёры, снявшие больше одного фильма?
select d.name as hasMoreThanOneMovie from cinema.director d inner join cinema.movie m on d.director_id = m.director_id group by d.name having count(m.movie_id) > 1

Фильмы с более двумя оставленными комментариями? 
select m.title, count(r.comment) as commentsCount from cinema.movie m inner join cinema.review r on m.movie_id = r.movie_id group by m.title having count(r.comment) >= 1

c. GROUPING SETS, ROLLUP и CUBE

// Grouping sets

Суммарная выручка от аренды по языкам и странам, промежуточные итоги
select m.language, m.country, sum(r.price) as price from cinema.movie m inner join cinema.rental r on m.movie_id = r.movie_id group by grouping sets ((m.language, m.country), (m.language), (m.country), ())

Количество фильмов по устройствам и прогрессу просмотра, промежуточные итоги
select v.device, v.progress, count(m.movie_id) as moviesCount from cinema.viewing v inner join cinema.movie m on m.movie_id = v.movie_id group by grouping sets ((v.device, v.progress), (v.device), ())

// Rollup

Суммарная выручка от аренды по языкам и странам, плюс промежуточные итоги
select m.language, m.country, sum(r.price) as price from cinema.movie m inner join cinema.rental r on m.movie_id = r.movie_id group by rollup (m.language, m.country)

Количество фильмов по устройствам и прогрессу просмотра, промежуточные итоги
select v.device, v.progress, count(m.movie_id) as moviesCount from cinema.viewing v inner join cinema.movie m on m.movie_id = v.movie_id group by rollup (v.device, v.progress)

// Cube 

Суммарная выручка от аренды по языкам и странам, плюс промежуточные итоги
select m.language, m.country, sum(r.price) as price from cinema.movie m inner join cinema.rental r on m.movie_id = r.movie_id group by cube (m.language, m.country)

Количество фильмов по устройствам и прогрессу просмотра, промежуточные итоги
select m.language, m.country, sum(r.price) as price from cinema.movie m inner join cinema.rental r on m.movie_id = r.movie_id group by cube (m.language, m.country)

d. SELECT, FROM, WHERE, GROUP BY, HAVING, ORDER BY

Фильмы до 2020 года, кроме испанских, отсортированные по названию в обратном порядке
select title, country from cinema.movie where release_year < '2020' group by title, country having country <> 'Spain' order by title desc

Описание фильмов и их жанры кроме комедий, где в названии есть пробел:
select m.description, g.name from cinema.movie m inner join cinema.movie_genre mg on m.movie_id = mg.movie_id inner join cinema.genre g on g.genre_id = mg.genre_id where m.title like '% %' group by m.description, g.name having g.name <> 'Comedy' order by g.name
