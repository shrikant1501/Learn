-- V3: Seed data for the todos table.
-- Flyway runs this EXACTLY ONCE and records its checksum.
-- If you need different seed data later, create V4__update_seed.sql — never edit this file.
--
-- Why move from data.sql to a migration?
--   data.sql ran on EVERY restart (with ddl-auto=create-drop it was harmless because
--   the table was recreated each time). Now that Flyway controls schema creation,
--   data.sql would fail on the second restart because the INSERT rows already exist.
--   A Flyway migration is tracked — it runs once and is never repeated.
--
-- NOW() works on both H2 and PostgreSQL.

INSERT INTO todos (title, description, completed, created_at, updated_at) VALUES
    ('Learn Spring Boot',               'Complete all phases of the learning project',       FALSE, NOW(), NOW()),
    ('Understand Dependency Injection', 'Learn how Spring manages beans and wires them',     FALSE, NOW(), NOW()),
    ('Read about REST principles',      'Statelessness, uniform interface, resource naming', TRUE,  NOW(), NOW()),
    ('Learn JPA and Hibernate',         'Entities, repositories, transactions, JPQL',        FALSE, NOW(), NOW());
