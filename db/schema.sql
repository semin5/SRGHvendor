CREATE DATABASE srghvendor CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE srghvendor;

CREATE TABLE work_sites (
  source_row INT UNSIGNED NOT NULL PRIMARY KEY,
  work_name VARCHAR(255),
  site_url VARCHAR(255),
  login_id VARCHAR(255),
  password VARCHAR(255),
  vendor_name VARCHAR(255),
  contact_person VARCHAR(255),
  contact_phone VARCHAR(255),
  remarks TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vendor_info (
  source_row INT UNSIGNED NOT NULL PRIMARY KEY,
  vendor_name VARCHAR(255),
  product VARCHAR(255),
  contact_person VARCHAR(255),
  phone_number VARCHAR(255),
  office_phone VARCHAR(255),
  address VARCHAR(255),
  website VARCHAR(255),
  courier_service VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE maintenance_vendors (
  source_row INT UNSIGNED NOT NULL PRIMARY KEY,
  vendor_name VARCHAR(255),
  phone_number VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE video_monthly_inspections (
  source_row INT UNSIGNED NOT NULL PRIMARY KEY,
  inspection_year SMALLINT UNSIGNED,
  inspection_month TINYINT UNSIGNED,
  change_details TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
