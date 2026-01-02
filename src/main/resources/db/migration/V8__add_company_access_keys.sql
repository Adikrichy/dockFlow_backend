-- Create company_access_keys table for storing RSA-4096 PKCS#12 keys
CREATE TABLE IF NOT EXISTS company_access_keys (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    public_key TEXT NOT NULL,
    encrypted_private_key TEXT NOT NULL,
    key_algorithm VARCHAR(50) NOT NULL DEFAULT 'RSA',
    key_size INTEGER NOT NULL DEFAULT 4096,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    
    CONSTRAINT fk_access_key_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_access_key_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_company_key UNIQUE (user_id, company_id)
);

-- Create index for faster lookups
CREATE INDEX idx_access_keys_user_company ON company_access_keys(user_id, company_id);
CREATE INDEX idx_access_keys_last_used ON company_access_keys(last_used_at);
