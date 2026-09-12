CREATE TEMP TABLE seed_merchant_api_key (
    account_id BIGINT PRIMARY KEY,
    api_key_hash TEXT NOT NULL,
    hmac_secret TEXT NOT NULL,
    is_active BOOLEAN NOT NULL
);

-- Plaintext: demo-hmac-secret. The ciphertext was encrypted with the exact
-- OUTPOST_HMAC_ENCRYPTION_KEY value in .env.example. To regenerate after
-- changing that key: OUTPOST_HMAC_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= jshell -q <<< 'import javax.crypto.*; import javax.crypto.spec.*; import java.security.*; import java.util.*; var k=new SecretKeySpec(Base64.getDecoder().decode(System.getenv("OUTPOST_HMAC_ENCRYPTION_KEY")),"AES"); var iv=new byte[12]; new SecureRandom().nextBytes(iv); var c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,k,new GCMParameterSpec(128,iv)); var p=c.doFinal("demo-hmac-secret".getBytes()); System.out.println(Base64.getEncoder().encodeToString(java.nio.ByteBuffer.allocate(iv.length+p.length).put(iv).put(p).array()));' -- noqa: LT05
INSERT INTO seed_merchant_api_key
SELECT
    account.account_id,
    encode(digest('demo-outpost-api-key', 'sha256'), 'hex') AS api_key_hash,
    'MTIzNDU2Nzg5MDEypG6klax6DJlGPy5yHDDXUn370yQJZ3t0WBCnZ/UkxYA='
        AS hmac_secret,
    TRUE AS is_active
FROM account
INNER JOIN account_type
    ON account.account_type_id = account_type.account_type_id
WHERE account_type.code = 'MERCHANT';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM seed_merchant_api_key expected
        LEFT JOIN merchant_api_key actual USING (account_id)
        WHERE actual.account_id IS NULL
           OR actual.api_key_hash IS DISTINCT FROM expected.api_key_hash
           OR actual.hmac_secret IS DISTINCT FROM expected.hmac_secret
           OR actual.is_active IS DISTINCT FROM expected.is_active
    ) THEN
        RAISE EXCEPTION 'merchant api key seed found a divergent row';
    END IF;
END
$$;
INSERT INTO merchant_api_key (
    account_id, account_type_id, api_key_hash, hmac_secret, is_active
)
SELECT
    seed.account_id,
    account_type.account_type_id,
    seed.api_key_hash,
    seed.hmac_secret,
    seed.is_active
FROM seed_merchant_api_key AS seed
INNER JOIN account_type ON account_type.code = 'MERCHANT'
ON CONFLICT DO NOTHING;
DROP TABLE seed_merchant_api_key;
