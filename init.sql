-- init.sql

-- 1. Create the database (if it doesn't already exist)
CREATE DATABASE IF NOT EXISTS springboot_db;

-- 2. Create the user for the Spring Boot app
CREATE USER IF NOT EXISTS 'springuser'@'%' IDENTIFIED BY 'springpass';

-- 3. Grant all necessary permissions to the user on the new database
GRANT ALL PRIVILEGES ON springboot_db.* TO 'springuser'@'%';

-- 4. Apply privilege changes
FLUSH PRIVILEGES;
