-- USER ROLES
INSERT INTO user_role (role_name)
VALUES
    ('ADMIN'),
    ('USER'),
    ('KID')
    ON CONFLICT (role_name) DO NOTHING;

-- SUBSCRIPTION STATUS
INSERT INTO subscription_status (status_name)
VALUES
    ('ACTIVE'),
    ('PAUSED'),
    ('CANCELLED')
    ON CONFLICT (status_name) DO NOTHING;

-- PAYMENT METHODS
INSERT INTO payment_method (method_name)
VALUES
    ('CARD'),
    ('PAYPAL'),
    ('APPLEPAY')
    ON CONFLICT (method_name) DO NOTHING;

-- COUNTRIES
INSERT INTO country (country_name)
VALUES
    ('USA'),
    ('UK'),
    ('DE')
    ON CONFLICT (country_name) DO NOTHING;

-- GENRES
INSERT INTO genre (name, description)
VALUES
    ('Drama', ''),
    ('Comedy', ''),
    ('Action', '')
    ON CONFLICT (name) DO NOTHING;