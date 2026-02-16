CREATE INDEX IF NOT EXISTS ix_viewing_user ON viewing(user_id);
CREATE INDEX IF NOT EXISTS ix_viewing_movie ON viewing(movie_id);
CREATE INDEX IF NOT EXISTS ix_purchase_user ON purchase(user_id);
CREATE INDEX IF NOT EXISTS ix_rental_user ON rental(user_id);

CREATE INDEX IF NOT EXISTS ix_review_comment_tsv ON review USING GIN (comment_tsv);

CREATE INDEX IF NOT EXISTS ix_viewing_meta_gin ON viewing USING GIN (meta);
CREATE INDEX IF NOT EXISTS ix_purchase_payment_meta_gin ON purchase USING GIN (payment_meta);

CREATE INDEX IF NOT EXISTS ix_viewing_tags_gin ON viewing USING GIN (tags);

CREATE INDEX IF NOT EXISTS ix_rental_period_gist ON rental USING GIST (rental_period);
CREATE INDEX IF NOT EXISTS ix_purchase_amount_range_gist ON purchase USING GIST (amount_range);

CREATE INDEX IF NOT EXISTS ix_viewing_device_not_null ON viewing(device) WHERE device IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_purchase_coupon_not_null ON purchase(coupon_code) WHERE coupon_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_rental_return_not_null ON rental(return_date) WHERE return_date IS NOT NULL;

INSERT INTO user_role(role_name)
VALUES ('ADMIN'),
       ('USER'),
       ('KID') ON CONFLICT DO NOTHING;

INSERT INTO subscription_status(status_name)
VALUES ('ACTIVE'),
       ('PAUSED'),
       ('CANCELLED') ON CONFLICT DO NOTHING;

INSERT INTO rental_status(status_name)
VALUES ('OPEN'),
       ('RETURNED'),
       ('OVERDUE') ON CONFLICT DO NOTHING;

INSERT INTO payment_method(method_name)
VALUES ('CARD'),
       ('PAYPAL'),
       ('APPLEPAY') ON CONFLICT DO NOTHING;

INSERT INTO subscription_plan(plan_type, price)
VALUES ('BASIC', 9.99),
       ('STANDARD', 12.99),
       ('PREMIUM', 15.99) ON CONFLICT DO NOTHING;

INSERT INTO age_rating(age_rating)
VALUES ('G'),
       ('PG'),
       ('PG-13'),
       ('R') ON CONFLICT DO NOTHING;

INSERT INTO country(country_name)
VALUES ('USA'),
       ('UK'),
       ('NL'),
       ('DE'),
       ('FR') ON CONFLICT DO NOTHING;

INSERT INTO language(language_name)
VALUES ('English'),
       ('Dutch'),
       ('German'),
       ('French') ON CONFLICT DO NOTHING;

INSERT INTO genre(name, description)
VALUES ('Drama', ''),
       ('Comedy', ''),
       ('Action', ''),
       ('Sci-Fi', ''),
       ('Horror', '') ON CONFLICT DO NOTHING;
