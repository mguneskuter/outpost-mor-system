-- A merchant API key authenticates exactly one merchant, so its hash is unique.
ALTER TABLE merchant_api_key
ADD CONSTRAINT uq_merchant_api_key_api_key_hash UNIQUE (api_key_hash);
