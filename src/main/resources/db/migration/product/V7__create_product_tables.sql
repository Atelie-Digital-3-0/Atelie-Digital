CREATE SCHEMA IF NOT EXISTS product;


-- PRODUCT

CREATE TABLE product.products (
                                  id UUID PRIMARY KEY,
                                  store_id UUID NOT NULL,
                                  name VARCHAR(150) NOT NULL,
                                  description TEXT,

                                  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP,

                                  CONSTRAINT fk_product_store
                                      FOREIGN KEY (store_id)
                                          REFERENCES store.stores(id)
);

CREATE INDEX idx_products_store_id
    ON product.products(store_id);

CREATE INDEX idx_products_active_store
    ON product.products(store_id)
    WHERE is_deleted = FALSE;



-- PRODUCT VARIATIONS


CREATE TABLE product.product_variations (
                                            id UUID PRIMARY KEY,
                                            product_id UUID NOT NULL,

                                            price NUMERIC(12, 2) NOT NULL,
                                            weight NUMERIC(10, 3),
                                            length NUMERIC(10, 3),
                                            width NUMERIC(10, 3),
                                            height NUMERIC(10, 3),

                                            sku VARCHAR(50),
                                            stock INTEGER NOT NULL DEFAULT 0,

                                            color VARCHAR(100),
                                            size VARCHAR(100),

                                            is_primary BOOLEAN NOT NULL DEFAULT FALSE,
                                            is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            updated_at TIMESTAMP,

                                            CONSTRAINT fk_variation_product
                                                FOREIGN KEY (product_id)
                                                    REFERENCES product.products(id),

                                            CONSTRAINT chk_variation_price
                                                CHECK (price >= 0),

                                            CONSTRAINT chk_variation_stock
                                                CHECK (stock >= 0),

                                            CONSTRAINT chk_variation_weight
                                                CHECK (weight IS NULL OR weight > 0),

                                            CONSTRAINT chk_variation_length
                                                CHECK (length IS NULL OR length > 0),

                                            CONSTRAINT chk_variation_width
                                                CHECK (width IS NULL OR width > 0),

                                            CONSTRAINT chk_variation_height
                                                CHECK (height IS NULL OR height > 0)
);

-- SKU unique globally, including logically deleted variations.
CREATE UNIQUE INDEX uk_product_variations_sku
    ON product.product_variations(sku)
    WHERE sku IS NOT NULL;

-- A product can have at most one active primary variation.
CREATE UNIQUE INDEX uk_product_variations_primary
    ON product.product_variations(product_id)
    WHERE is_primary = TRUE AND is_deleted = FALSE;

CREATE INDEX idx_product_variations_product_id
    ON product.product_variations(product_id);

CREATE INDEX idx_product_variations_active
    ON product.product_variations(product_id)
    WHERE is_deleted = FALSE;



-- PRODUCT IMAGES


CREATE TABLE product.product_images (
                                        id UUID PRIMARY KEY,
                                        variation_id UUID NOT NULL,

                                        storage_key VARCHAR(512) NOT NULL,
                                        display_order INTEGER NOT NULL DEFAULT 0,

                                        is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                        CONSTRAINT fk_product_image_variation
                                            FOREIGN KEY (variation_id)
                                                REFERENCES product.product_variations(id),

                                        CONSTRAINT chk_product_image_display_order
                                            CHECK (display_order >= 0)
);

-- Prevent duplicate active image positions within a variation.
CREATE UNIQUE INDEX uk_product_images_display_order
    ON product.product_images(variation_id, display_order)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_product_images_variation_id
    ON product.product_images(variation_id);

CREATE INDEX idx_product_images_active
    ON product.product_images(variation_id, display_order)
    WHERE is_deleted = FALSE;
