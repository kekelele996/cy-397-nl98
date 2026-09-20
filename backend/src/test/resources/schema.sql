CREATE TABLE IF NOT EXISTS contract_templates (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(32) NOT NULL,
  title VARCHAR(120) NOT NULL,
  content CLOB NOT NULL,
  variables CLOB,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT uk_templates_type UNIQUE (type)
);

CREATE TABLE IF NOT EXISTS contracts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  content CLOB NOT NULL,
  fill_params CLOB,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  signed_at TIMESTAMP NULL,
  signers CLOB,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_contracts_template FOREIGN KEY (template_id) REFERENCES contract_templates (id)
);
CREATE INDEX IF NOT EXISTS idx_contracts_user_status ON contracts (user_id, status);

CREATE TABLE IF NOT EXISTS contract_signers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  signer_id VARCHAR(64) NOT NULL,
  signer_name VARCHAR(120) NOT NULL,
  sign_order INT NOT NULL,
  deadline TIMESTAMP NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  signed_at TIMESTAMP NULL,
  rejected_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_signers_contract_signer UNIQUE (contract_id, signer_id),
  CONSTRAINT fk_signers_contract FOREIGN KEY (contract_id) REFERENCES contracts (id)
);
CREATE INDEX IF NOT EXISTS idx_signers_contract ON contract_signers (contract_id);

CREATE TABLE IF NOT EXISTS legal_tickets (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, type VARCHAR(32), description CLOB, status VARCHAR(32), attachments CLOB);
CREATE TABLE IF NOT EXISTS legal_faq (id BIGINT PRIMARY KEY AUTO_INCREMENT, category VARCHAR(60), question VARCHAR(200), answer CLOB);
