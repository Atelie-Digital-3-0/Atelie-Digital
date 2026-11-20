-- Ordem de criacao dos enderecos: usada para escolher qual endereco vira principal quando o principal e removido.
ALTER TABLE accounts.addresses
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Normaliza dados existentes antes de criar a constraint: mantem so um principal por conta...
WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY account_id ORDER BY created_at, id) AS position
    FROM accounts.addresses
    WHERE is_main
)
UPDATE accounts.addresses a
SET is_main = FALSE
FROM ranked
WHERE a.id = ranked.id AND ranked.position > 1;

-- ...e promove um endereco nas contas que tem enderecos mas nenhum principal.
WITH first_address AS (
    SELECT DISTINCT ON (account_id) id
    FROM accounts.addresses
    WHERE account_id NOT IN (SELECT account_id FROM accounts.addresses WHERE is_main)
    ORDER BY account_id, created_at, id
)
UPDATE accounts.addresses a
SET is_main = TRUE
FROM first_address
WHERE a.id = first_address.id;

-- No maximo um endereco principal por conta.
CREATE UNIQUE INDEX uk_addresses_one_main_per_account
    ON accounts.addresses (account_id)
    WHERE is_main;
