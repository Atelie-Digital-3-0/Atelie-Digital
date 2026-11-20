CREATE SCHEMA IF NOT EXISTS store;

CREATE TABLE store.categories (
                                  id UUID PRIMARY KEY,
                                  name VARCHAR(150) NOT NULL UNIQUE,
                                  description VARCHAR(500),
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP,
                                  deleted_at TIMESTAMP
);


CREATE TABLE store.store_addresses (
                                       id UUID PRIMARY KEY,
                                       street VARCHAR(255) NOT NULL,
                                       address_number VARCHAR(20) NOT NULL,
                                       neighborhood VARCHAR(100) NOT NULL,
                                       city VARCHAR(100) NOT NULL,
                                       state VARCHAR(2) NOT NULL,
                                       zip_code VARCHAR(9) NOT NULL,
                                       complement VARCHAR(255),
                                       store_id UUID
);


CREATE TABLE store.stores (
                              id UUID PRIMARY KEY,
                              artisan_id VARCHAR(255) NOT NULL,
                              category_id UUID NOT NULL REFERENCES store.categories(id),
                              address_id UUID UNIQUE NOT NULL REFERENCES store.store_addresses(id),
                              name VARCHAR(150) NOT NULL,
                              description TEXT,
                              image VARCHAR(255),
                              banner VARCHAR(255),
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP,
                              deleted_at TIMESTAMP
);


ALTER TABLE store.store_addresses
    ADD CONSTRAINT fk_store_address_store
        FOREIGN KEY (store_id) REFERENCES store.stores(id);


CREATE INDEX idx_stores_artisan_id ON store.stores(artisan_id);
CREATE INDEX idx_stores_category_id ON store.stores(category_id);
CREATE INDEX idx_stores_deleted_at ON store.stores(deleted_at);