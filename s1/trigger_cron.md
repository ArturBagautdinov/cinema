
-- 1. NEW 
--    Перед вставкой пользователя выставляем date_created и роль по умолчанию
CREATE OR REPLACE FUNCTION cinema.trg_users_set_defaults()
RETURNS trigger AS $$
BEGIN
  IF NEW.date_created IS NULL THEN
    NEW.date_created := CURRENT_TIMESTAMP;
  END IF;

  IF NEW.role IS NULL THEN
    NEW.role := 'user';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_before_insert_users_set_defaults
BEFORE INSERT ON cinema.users
FOR EACH ROW
EXECUTE FUNCTION cinema.trg_users_set_defaults();

-- 2. NEW 
--    Контроль положительной цены аренды
CREATE OR REPLACE FUNCTION cinema.trg_rental_check_price()
RETURNS trigger AS $$
BEGIN
  IF NEW.price IS NULL OR NEW.price <= 0 THEN
    RAISE EXCEPTION 'Rental price must be positive, got %', NEW.price;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_before_ins_upd_rental_check_price
BEFORE INSERT OR UPDATE ON cinema.rental
FOR EACH ROW
EXECUTE FUNCTION cinema.trg_rental_check_price();


-- 3. OLD
--    Если меняется роль пользователя, проставляем last_login

CREATE OR REPLACE FUNCTION cinema.trg_users_last_login_on_role_change()
RETURNS trigger AS $$
BEGIN
  IF OLD.role IS DISTINCT FROM NEW.role THEN
    NEW.last_login := CURRENT_TIMESTAMP;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_before_update_users_last_login_on_role_change
BEFORE UPDATE ON cinema.users
FOR EACH ROW
EXECUTE FUNCTION cinema.trg_users_last_login_on_role_change();

-- 4. OLD
-- Логирование при удалении актера
CREATE OR REPLACE FUNCTION cinema.trg_actor_before_delete_log()
RETURNS trigger AS $$
BEGIN
  RAISE NOTICE 'Actor % (id = %) is being deleted. Country: %, Birth date: %',
    OLD.name,
    OLD.actor_id,
    OLD.country,
    OLD.birth_date;

  RETURN OLD; 
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_before_delete_actor_log
BEFORE DELETE ON cinema.actor
FOR EACH ROW
EXECUTE FUNCTION cinema.trg_actor_before_delete_log();


-- 5. BEFORE
--    Контроль положительной цены покупки
CREATE OR REPLACE FUNCTION cinema.trg_purchase_check_price()
RETURNS trigger AS $$
BEGIN
  IF NEW.price IS NULL OR NEW.price <= 0 THEN
    RAISE EXCEPTION 'Purchase price must be positive, got %', NEW.price;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_before_ins_upd_purchase_check_price
BEFORE INSERT OR UPDATE ON cinema.purchase
FOR EACH ROW
EXECUTE FUNCTION cinema.trg_purchase_check_price();



-- 6. AFTER
--    AFTER INSERT: логируем добавление фильма

CREATE OR REPLACE FUNCTION cinema.trg_movie_after_insert_notice()
RETURNS trigger AS $$
BEGIN
  RAISE NOTICE 'Inserted movie % (id = %)', NEW.title, NEW.movie_id;
  RETURN NULL;  -- для AFTER-ROW результат игнорируется
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_after_insert_movie_notice
AFTER INSERT ON cinema.movie
FOR EACH ROW
EXECUTE FUNCTION cinema.trg_movie_after_insert_notice();



-- 7. AFTER
--    AFTER UPDATE: логируем изменение прогресса просмотра

CREATE OR REPLACE FUNCTION cinema.trg_viewing_after_update_notice()
RETURNS trigger AS $$
BEGIN
  RAISE NOTICE 'Viewing % for user %: progress changed from % to %',
    NEW.viewing_id, NEW.user_id, OLD.progress, NEW.progress;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_after_update_viewing_notice
AFTER UPDATE ON cinema.viewing
FOR EACH ROW
EXECUTE FUNCTION cinema.trg_viewing_after_update_notice();



-- 8. BEFORE
--    BEFORE UPDATE: логируем смену статуса подписки

CREATE OR REPLACE FUNCTION cinema.trg_subscription_status_change()
RETURNS trigger AS $$
BEGIN
  IF OLD.status IS DISTINCT FROM NEW.status THEN
    RAISE NOTICE 'Subscription % (user %): status % -> %',
      NEW.subscription_id, NEW.user_id, OLD.status, NEW.status;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_before_update_subscription_status
BEFORE UPDATE ON cinema.subscription
FOR EACH ROW
EXECUTE FUNCTION cinema.trg_subscription_status_change();



-- 9. ROW level
--    BEFORE DELETE: логируем удаление отзыва

CREATE OR REPLACE FUNCTION cinema.trg_review_before_delete()
RETURNS trigger AS $$
BEGIN
  RAISE NOTICE 'Review % on movie % by user % (rating %) is being deleted',
    OLD.review_id, OLD.movie_id, OLD.user_id, OLD.rating;

  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_before_delete_review_notice
BEFORE DELETE ON cinema.review
FOR EACH ROW
EXECUTE FUNCTION cinema.trg_review_before_delete();



-- 10. ROW level
--   Запись даты при обновлении поля is_returned

CREATE OR REPLACE FUNCTION cinema.trg_rental_set_return_date()
RETURNS trigger AS $$
BEGIN
  IF OLD.is_returned = false AND NEW.is_returned = true THEN
    NEW.return_date := CURRENT_TIMESTAMP;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_before_update_rental_set_return_date
BEFORE UPDATE ON cinema.rental
FOR EACH ROW
EXECUTE FUNCTION cinema.trg_rental_set_return_date();



-- 11. STATEMENT level
--     BEFORE UPDATE STATEMENT: лог по обновлению viewing

CREATE OR REPLACE FUNCTION cinema.trg_before_update_viewing_stmt()
RETURNS trigger AS $$
BEGIN
  RAISE NOTICE 'BEFORE UPDATE on table % (statement level)', TG_TABLE_NAME;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_before_update_viewing_stmt
BEFORE UPDATE ON cinema.viewing
FOR EACH STATEMENT
EXECUTE FUNCTION cinema.trg_before_update_viewing_stmt();



-- 12. STATEMENT level
--     AFTER UPDATE STATEMENT: лог по обновлению viewing

CREATE OR REPLACE FUNCTION cinema.trg_after_update_viewing_stmt()
RETURNS trigger AS $$
BEGIN
  RAISE NOTICE 'AFTER UPDATE on table % (statement level)', TG_TABLE_NAME;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_after_update_viewing_stmt
AFTER UPDATE ON cinema.viewing
FOR EACH STATEMENT
EXECUTE FUNCTION cinema.trg_after_update_viewing_stmt();


--Просмотр всех триггеров
SELECT
    event_object_table AS table_name,
    trigger_name,
    action_timing AS timing,
    event_manipulation AS event,
    action_statement AS definition
FROM information_schema.triggers
ORDER BY table_name, trigger_name;

![Все триггеры](./Screenshot 2025-11-30 at 16.00.02.png)


// КРОНЫ 

--Ежедневное удаление просроченных подписок
SELECT cron.schedule(
    'cleanup_expired_subscriptions',
    '0 2 * * *',   -- каждый день в 02:00
    $$DELETE FROM cinema.subscription WHERE end_date < NOW();$$
);


--Проверка истёкших аренд и установка статуса "overdue"
SELECT cron.schedule(
    'update_overdue_rentals',
    '*/30 * * * *',   -- каждые 30 минут
    $$UPDATE cinema.rental 
      SET status = 'overdue'
      WHERE return_date IS NOT NULL
        AND return_date < NOW()
        AND status <> 'overdue';$$
);

--Автоматическое удаление старых отзывов-спойлеров

SELECT cron.schedule(
    'delete_old_spoilers',
    '0 3 * * 0',     -- каждое воскресенье в 03:00
    $$DELETE FROM cinema.review
      WHERE is_spoiler = true
        AND review_date < NOW() - INTERVAL '1 year';$$
);


-- Просмотр истории выполнения CRON-задач
SELECT *
FROM cron.job_run_details
ORDER BY start_time DESC;

--Просмотр списков всех CRON-задач
SELECT *
FROM cron.job;
