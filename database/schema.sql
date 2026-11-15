-- ========================================================
-- SpendWise Database Schema
-- Compatible with MySQL 8.0+
-- ========================================================

CREATE DATABASE IF NOT EXISTS spendwise;
USE spendwise;

-- 1. Users Table (Guests and Registered Users)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    guest_identifier VARCHAR(64) NOT NULL UNIQUE,
    username VARCHAR(30) UNIQUE,
    email VARCHAR(254) UNIQUE,
    password_hash VARCHAR(100),
    reset_token_hash VARCHAR(64),
    reset_token_expires_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS password_change_otps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    otp_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    verified_at TIMESTAMP NULL,
    consumed_at TIMESTAMP NULL,
    CONSTRAINT fk_password_change_otp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 2. Personal Transactions Table
CREATE TABLE IF NOT EXISTS personal_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL, -- 'INCOME' or 'EXPENSE'
    category VARCHAR(50) NOT NULL,
    description TEXT,
    transaction_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_pt_user_date ON personal_transactions (user_id, transaction_date);
CREATE INDEX idx_pt_category ON personal_transactions (user_id, category);

-- 3. Expense Groups Table
CREATE TABLE IF NOT EXISTS expense_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name VARCHAR(120) NOT NULL,
    description TEXT,
    invite_code VARCHAR(20) NOT NULL UNIQUE,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_eg_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_eg_invite_code ON expense_groups (invite_code);

-- 4. Group Members Table
CREATE TABLE IF NOT EXISTS group_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES expense_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_group_user UNIQUE (group_id, user_id)
);

CREATE INDEX idx_gm_group ON group_members (group_id);
CREATE INDEX idx_gm_user ON group_members (user_id);

-- 5. Group Expenses Table
CREATE TABLE IF NOT EXISTS group_expenses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    description VARCHAR(200) NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    paid_by BIGINT NOT NULL,
    split_type VARCHAR(20) NOT NULL, -- 'EQUAL' or 'CUSTOM'
    expense_date DATE NOT NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ge_group FOREIGN KEY (group_id) REFERENCES expense_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_ge_payer FOREIGN KEY (paid_by) REFERENCES group_members(id) ON DELETE RESTRICT
);

CREATE INDEX idx_ge_group ON group_expenses (group_id);

-- 6. Expense Splits Table
CREATE TABLE IF NOT EXISTS expense_splits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    share_amount DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_es_expense FOREIGN KEY (expense_id) REFERENCES group_expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_es_member FOREIGN KEY (member_id) REFERENCES group_members(id) ON DELETE CASCADE,
    CONSTRAINT uk_expense_member UNIQUE (expense_id, member_id)
);

CREATE TABLE IF NOT EXISTS group_expense_contributions (
    expense_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    amount_paid DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_gec_expense FOREIGN KEY (expense_id) REFERENCES group_expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_gec_member FOREIGN KEY (member_id) REFERENCES group_members(id) ON DELETE CASCADE,
    CONSTRAINT uk_expense_contribution_member UNIQUE (expense_id, member_id)
);

CREATE TABLE IF NOT EXISTS group_expense_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id BIGINT NOT NULL,
    description VARCHAR(150) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_gei_expense FOREIGN KEY (expense_id) REFERENCES group_expenses(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS group_expense_item_members (
    item_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    CONSTRAINT fk_geim_item FOREIGN KEY (item_id) REFERENCES group_expense_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_geim_member FOREIGN KEY (member_id) REFERENCES group_members(id) ON DELETE CASCADE,
    CONSTRAINT uk_item_member UNIQUE (item_id, member_id)
);

CREATE TABLE IF NOT EXISTS group_receipt_uploads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    uploaded_by BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(80) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receipt_group FOREIGN KEY (group_id) REFERENCES expense_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_receipt_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_es_expense ON expense_splits (expense_id);
CREATE INDEX idx_es_member ON expense_splits (member_id);

-- 7. Settlements Table (Optional Tracking for Settled Balances)
CREATE TABLE IF NOT EXISTS settlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    from_member_id BIGINT NOT NULL,
    to_member_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    settled_date DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    notes VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_st_group FOREIGN KEY (group_id) REFERENCES expense_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_st_from FOREIGN KEY (from_member_id) REFERENCES group_members(id) ON DELETE CASCADE,
    CONSTRAINT fk_st_to FOREIGN KEY (to_member_id) REFERENCES group_members(id) ON DELETE CASCADE
);

CREATE INDEX idx_st_group ON settlements (group_id);
