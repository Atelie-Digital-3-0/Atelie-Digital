CREATE TABLE accounts (
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

CREATE TABLE addresses (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    cep VARCHAR(8) NOT NULL,
    zip_code VARCHAR(255),
    street VARCHAR(255) NOT NULL,
    number VARCHAR(255) NOT NULL,
    complement VARCHAR(255),
    neighborhood VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    state VARCHAR(2) NOT NULL,
    is_main BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_addresses_account_id ON addresses(account_id);
