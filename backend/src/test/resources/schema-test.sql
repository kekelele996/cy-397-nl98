CREATE TABLE IF NOT EXISTS contract_templates (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(32) NOT NULL,
  title VARCHAR(120) NOT NULL,
  content TEXT NOT NULL,
  variables TEXT
);

CREATE TABLE IF NOT EXISTS contracts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  content TEXT,
  variables TEXT,
  format VARCHAR(16) NOT NULL DEFAULT 'TEXT',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  deadline DATETIME,
  signed_at DATETIME,
  created_at DATETIME,
  updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS contract_signers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  signer_name VARCHAR(120) NOT NULL,
  signer_user_id BIGINT,
  sign_order INT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  signed_at DATETIME
);

INSERT INTO contract_templates (id, type, title, content, variables) VALUES
  (1, 'LEASE', '租赁合同', '甲方：${partyA}\n乙方：${partyB}\n租金：${amount}\n日期：${date}', '["partyA","partyB","amount","date"]'),
  (2, 'LABOR', '劳动合同', '用人单位：${partyA}\n劳动者：${partyB}\n岗位：${position}\n月薪：${amount}\n入职日期：${date}', '["partyA","partyB","position","amount","date"]');
