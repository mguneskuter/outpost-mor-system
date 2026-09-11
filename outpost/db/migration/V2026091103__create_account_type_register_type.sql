CREATE TABLE account_type_register_type (
    account_type_register_type_id BIGINT PRIMARY KEY,
    account_type_id BIGINT NOT NULL REFERENCES account_type (account_type_id),
    register_type_id BIGINT NOT NULL REFERENCES register_type (
        register_type_id
    ),
    CONSTRAINT uq_account_type_register_type_pair UNIQUE (
        account_type_id, register_type_id
    )
);
