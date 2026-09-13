CREATE TEMP TABLE seed_merchant_api_key (
    account_id BIGINT PRIMARY KEY,
    api_key_hash TEXT NOT NULL,
    hmac_secret TEXT NOT NULL,
    is_active BOOLEAN NOT NULL
);

-- Each demo merchant has its own key; one key identifies one merchant.
-- DEMO_MERCHANT: API key demo-outpost-api-key, HMAC secret demo-hmac-secret.
-- DEMO_MERCHANT_2: API key demo-merchant-2-api-key, HMAC secret
-- demo-merchant-2-hmac-secret.
-- api_key_hash is the SHA-256 hex of the API key. hmac_secret is the HMAC
-- secret encrypted with the exact OUTPOST_HMAC_ENCRYPTION_KEY value in
-- .env.example. To regenerate after changing that key, replace <secret>: OUTPOST_HMAC_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= jshell -q <<< 'import javax.crypto.*; import javax.crypto.spec.*; import java.security.*; import java.util.*; var k=new SecretKeySpec(Base64.getDecoder().decode(System.getenv("OUTPOST_HMAC_ENCRYPTION_KEY")),"AES"); var iv=new byte[12]; new SecureRandom().nextBytes(iv); var c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,k,new GCMParameterSpec(128,iv)); var p=c.doFinal("<secret>".getBytes()); System.out.println(Base64.getEncoder().encodeToString(java.nio.ByteBuffer.allocate(iv.length+p.length).put(iv).put(p).array()));' -- noqa: LT05
INSERT INTO seed_merchant_api_key
SELECT
    account.account_id,
    expected.api_key_hash,
    expected.hmac_secret,
    TRUE AS is_active
FROM (
    VALUES
    (
        'DEMO_MERCHANT',
        '8e76b4677297200712e7f3e1348767a1fb76e1b43072209a2726e0057f8e36c6',
        'MTIzNDU2Nzg5MDEypG6klax6DJlGPy5yHDDXUn370yQJZ3t0WBCnZ/UkxYA='
    ),
    (
        'DEMO_MERCHANT_2',
        '01fd51a365912fe68f279f86bd3af97a2c669c7e2841286f812f413d7a5a659c',
        -- A base64 ciphertext cannot be wrapped.
        'SA3ZlvGhb60WooJlmilchZv/JW0TW865w/k+hTt2MpiOidMA1il1CE+CnoFbKpMxwHPMFzRI2g==' -- noqa: LT05
    )
) AS expected (account_code, api_key_hash, hmac_secret)
INNER JOIN account ON expected.account_code = account.code
INNER JOIN account_type
    ON account.account_type_id = account_type.account_type_id
WHERE account_type.code = 'MERCHANT';

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
ON CONFLICT (api_key_hash) DO NOTHING;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM seed_merchant_api_key expected
        LEFT JOIN merchant_api_key actual USING (api_key_hash)
        WHERE actual.api_key_hash IS NULL
           OR actual.account_id IS DISTINCT FROM expected.account_id
           OR actual.hmac_secret IS DISTINCT FROM expected.hmac_secret
           OR actual.is_active IS DISTINCT FROM expected.is_active
    ) THEN
        RAISE EXCEPTION 'merchant api key seed found a divergent row';
    END IF;
END
$$;
DROP TABLE seed_merchant_api_key;
