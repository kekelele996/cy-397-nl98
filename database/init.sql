CREATE TABLE IF NOT EXISTS contract_templates (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(32) NOT NULL,
  title VARCHAR(120) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  variables JSON,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_templates_type (type)
);

CREATE TABLE IF NOT EXISTS contracts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  fill_params JSON,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  signed_at DATETIME NULL,
  signers JSON,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_contracts_user_status (user_id, status),
  CONSTRAINT fk_contracts_template FOREIGN KEY (template_id) REFERENCES contract_templates (id)
);

CREATE TABLE IF NOT EXISTS contract_signers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  signer_id VARCHAR(64) NOT NULL,
  signer_name VARCHAR(120) NOT NULL,
  sign_order INT NOT NULL,
  deadline DATETIME NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  signed_at DATETIME NULL,
  rejected_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_signers_contract_signer (contract_id, signer_id),
  KEY idx_signers_contract (contract_id),
  CONSTRAINT fk_signers_contract FOREIGN KEY (contract_id) REFERENCES contracts (id)
);

CREATE TABLE IF NOT EXISTS legal_tickets (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, type VARCHAR(32), description TEXT, status VARCHAR(32), attachments JSON);
CREATE TABLE IF NOT EXISTS legal_faq (id BIGINT PRIMARY KEY AUTO_INCREMENT, category VARCHAR(60), question VARCHAR(200), answer TEXT);

INSERT INTO contract_templates (type, title, content, variables, enabled) VALUES
  ('LEASE', '租赁合同', '甲方：${partyA}\n乙方：${partyB}\n租金：${amount}\n日期：${date}', '["partyA","partyB","amount","date"]', TRUE),
  ('LABOR', '劳动合同', '用人单位：${employer}\n劳动者：${employee}\n岗位：${position}\n期限：${date}', '["employer","employee","position","date"]', TRUE),
  ('LOAN', '借款合同', '出借人：${lender}\n借款人：${borrower}\n金额：${amount}\n日期：${date}', '["lender","borrower","amount","date"]', TRUE),
  ('PARTNERSHIP', '合作协议', '甲方：${partyA}\n乙方：${partyB}\n合作内容：${subject}\n日期：${date}', '["partyA","partyB","subject","date"]', TRUE),
  ('NDA', '保密协议', '披露方：${discloser}\n接收方：${receiver}\n日期：${date}', '["discloser","receiver","date"]', TRUE)
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO legal_faq(category, question, answer) VALUES ('合同纠纷','合同逾期未签署怎么办','可先发出书面催告并保存沟通证据。');
