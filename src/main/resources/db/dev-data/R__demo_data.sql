-- Demo data of the dev profile only (spring.flyway.locations in application-dev.yml): a few categories,
-- products and customers the orders can reference, since nothing in the API creates them.
-- A repeatable migration (R__) runs again whenever its checksum changes, so every insert is idempotent:
-- existing rows are left alone, missing ones are added.
-- On a fresh database the ids follow the listed order: customers 1 to 3, products 1 to 5. With the
-- review threshold of 1000.00, the CSV line "1,2,2" (2 x 229.00 = 458.00) is a low-value order, while
-- "1,3,1" (1 x 1299.00) and "2,2,5" (5 x 229.00 = 1145.00) are high-value ones.

INSERT INTO categories (category_name)
VALUES ('Computers'), ('Accessories'), ('Furniture')
ON CONFLICT (category_name) DO NOTHING;

-- products has no natural unique key: a product is only added when no product carries that name yet.
INSERT INTO products (category_id, product_name, unit_price)
SELECT c.id, p.product_name, p.unit_price
FROM (VALUES (1, 'Accessories', 'Wireless keyboard', 49.90),
             (2, 'Accessories', '27-inch monitor', 229.00),
             (3, 'Computers', 'Laptop Pro 14', 1299.00),
             (4, 'Furniture', 'Office chair', 189.00),
             (5, 'Computers', 'Mini PC', 599.00)) AS p (position, category_name, product_name, unit_price)
JOIN categories c ON c.category_name = p.category_name
WHERE NOT EXISTS (SELECT 1 FROM products existing WHERE existing.product_name = p.product_name)
ORDER BY p.position;

INSERT INTO customers (first_name, last_name, telephone, email, address)
VALUES ('Alice', 'Martin', '+33 6 12 34 56 78', 'alice.martin@example.com', '12 rue de la Paix, Paris'),
       ('Bob', 'Dupont', '+33 6 23 45 67 89', 'bob.dupont@example.com', '5 avenue Foch, Lyon'),
       ('Chloe', 'Bernard', NULL, 'chloe.bernard@example.com', NULL)
ON CONFLICT (email) DO NOTHING;
