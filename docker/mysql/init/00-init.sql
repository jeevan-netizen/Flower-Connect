-- MySQL init script (runs on first container start).
-- Ensures the baseline Flyway migration target exists even if Flyway
-- runs against an empty database created by the compose MYSQL_DATABASE var.
CREATE DATABASE IF NOT EXISTS flowerconnect CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;