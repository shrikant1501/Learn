-- Seed data — loaded by Spring Boot after Hibernate creates the schema.
-- This runs every time the app starts (because ddl-auto=create-drop recreates the table).
-- In production you would use Flyway or Liquibase for migrations instead.

INSERT INTO todos (title, description, completed) VALUES
    ('Learn Spring Boot',            'Complete Phase 5 — JPA and H2 database',             false),
    ('Understand Dependency Injection', 'Learn how Spring manages beans and wires them',   false),
    ('Read about REST principles',   'Statelessness, uniform interface, resource naming',  true),
    ('Learn JPA and Hibernate',      'Entities, repositories, transactions, JPQL',         false);
