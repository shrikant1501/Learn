-- Seed data — loaded by Spring Boot after Hibernate creates the schema.
-- This runs every time the app starts (because ddl-auto=create-drop recreates the table).
-- In production you would use Flyway or Liquibase for migrations instead.
--
-- NOTE: created_at and updated_at must be provided here because:
--   - The columns are NOT NULL (correct — every row must have timestamps)
--   - data.sql runs raw SQL — it does NOT trigger JPA lifecycle events (@PrePersist)
--   - AuditingEntityListener only fires when you call repository.save() in Java code
--   - For manually inserted seed rows, we provide literal timestamps directly

INSERT INTO todos (title, description, completed, created_at, updated_at) VALUES
    ('Learn Spring Boot',               'Complete all phases of the learning project',        false, NOW(), NOW()),
    ('Understand Dependency Injection', 'Learn how Spring manages beans and wires them',      false, NOW(), NOW()),
    ('Read about REST principles',      'Statelessness, uniform interface, resource naming',  true,  NOW(), NOW()),
    ('Learn JPA and Hibernate',         'Entities, repositories, transactions, JPQL',         false, NOW(), NOW());
