CREATE TABLE accounts.accounts (
    id UUID PRIMARY KEY,
    keycloak_id UUID NOT NULL UNIQUE,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    cellphone VARCHAR(255) NOT NULL,
    date_of_birth DATE NOT NULL,
    cpf VARCHAR(11) NOT NULL UNIQUE,
    bio VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE accounts.addresses (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts.accounts(id) ON DELETE CASCADE,
    zip_code VARCHAR(8) NOT NULL,
    street VARCHAR(255) NOT NULL,
    number VARCHAR(255) NOT NULL,
    complement VARCHAR(255),
    neighborhood VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    state VARCHAR(2) NOT NULL,
    is_main BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_addresses_account_id ON accounts.addresses(account_id);
