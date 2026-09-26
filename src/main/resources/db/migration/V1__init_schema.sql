-- Initial schema of both domains, sharing one database without any foreign key between them:
--   identity: users, roles, role_user (registration, login, role-only authorization)
--   commerce: categories, products, customers, orders (the orders the integration flows receive)
-- Flyway is the only owner of the schema; Hibernate validates the entities against it (ddl-auto: validate).

-- ---------------------------------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------------------------------

CREATE TABLE roles (
    id        BIGSERIAL    PRIMARY KEY,
    role_name VARCHAR(50)  NOT NULL UNIQUE
);

CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password       VARCHAR(255) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    account_locked BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE role_user (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

-- The two roles of the role-only authorization model. No account is seeded: the ADMIN account is
-- created at startup from environment variables, so no default password ever lives in a migration.
INSERT INTO roles (role_name) VALUES ('ADMIN'), ('USER');

-- ---------------------------------------------------------------------------------------------------
-- Commerce
-- ---------------------------------------------------------------------------------------------------

CREATE TABLE categories (
    id            BIGSERIAL    PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE products (
    id           BIGSERIAL      PRIMARY KEY,
    category_id  BIGINT         NOT NULL REFERENCES categories (id),
    product_name VARCHAR(150)   NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX idx_products_category_id ON products (category_id);

CREATE TABLE customers (
    id         BIGSERIAL    PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    telephone  VARCHAR(30),
    email      VARCHAR(255) NOT NULL UNIQUE,
    address    VARCHAR(255)
);

-- total is a snapshot of quantity * products.unit_price taken when the order is persisted, never
-- recalculated: a later price change must not alter an order already received.
-- source records which inbound channel the order came through, status what the flow decided.
CREATE TABLE orders (
    id          BIGSERIAL      PRIMARY KEY,
    customer_id BIGINT         NOT NULL REFERENCES customers (id),
    product_id  BIGINT         NOT NULL REFERENCES products (id),
    quantity    INTEGER        NOT NULL CHECK (quantity > 0),
    total       NUMERIC(14, 2) NOT NULL CHECK (total >= 0),
    source      VARCHAR(10)    NOT NULL CHECK (source IN ('API', 'FILE')),
    status      VARCHAR(20)    NOT NULL CHECK (status IN ('RECEIVED', 'AUTO_CONFIRMED', 'PENDING_REVIEW',
                                                          'APPROVED', 'REJECTED', 'FAILED'))
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_product_id ON orders (product_id);
