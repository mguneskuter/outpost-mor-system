-- A rate with a NULL product_type_id applies to every product type that has no
-- rate of its own in the same jurisdiction.
ALTER TABLE tax_rate
ADD COLUMN product_type_id BIGINT,
ADD CONSTRAINT fk_tax_rate_product_type
FOREIGN KEY (product_type_id) REFERENCES product_type (product_type_id),
DROP CONSTRAINT uq_tax_rate_jurisdiction,
ADD CONSTRAINT uq_tax_rate_jurisdiction_product_type
UNIQUE NULLS NOT DISTINCT (country_id, country_subdivision_id, product_type_id);
