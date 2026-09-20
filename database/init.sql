CREATE TABLE IF NOT EXISTS contract_templates (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(32) NOT NULL,
  title VARCHAR(120) NOT NULL,
  content TEXT NOT NULL,
  variables JSON
);

CREATE TABLE IF NOT EXISTS contracts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  content MEDIUMTEXT,
  variables JSON COMMENT '模板填充参数',
  format VARCHAR(16) NOT NULL DEFAULT 'TEXT',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  deadline DATETIME COMMENT '签署截止时间',
  signed_at DATETIME COMMENT '全部签署完成时间',
  created_at DATETIME,
  updated_at DATETIME,
  KEY idx_contracts_user_status (user_id, status)
);

CREATE TABLE IF NOT EXISTS contract_signers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  signer_name VARCHAR(120) NOT NULL COMMENT '签署方身份',
  signer_user_id BIGINT COMMENT '签署方用户ID',
  sign_order INT NOT NULL COMMENT '签署顺序',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  signed_at DATETIME COMMENT '签署时间',
  UNIQUE KEY uk_signer_contract_name (contract_id, signer_name),
  UNIQUE KEY uk_signer_contract_order (contract_id, sign_order),
  KEY idx_signer_contract (contract_id)
);

CREATE TABLE IF NOT EXISTS legal_tickets (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, type VARCHAR(32), description TEXT, status VARCHAR(32), attachments JSON);
CREATE TABLE IF NOT EXISTS legal_faq (id BIGINT PRIMARY KEY AUTO_INCREMENT, category VARCHAR(60), question VARCHAR(200), answer TEXT);

INSERT INTO contract_templates (id, type, title, content, variables) VALUES
  (1, 'LEASE', '租赁合同', '甲方：${partyA}\n乙方：${partyB}\n租金：${amount}\n日期：${date}', '["partyA","partyB","amount","date"]'),
  (2, 'LABOR', '劳动合同', '用人单位：${partyA}\n劳动者：${partyB}\n岗位：${position}\n月薪：${amount}\n入职日期：${date}', '["partyA","partyB","position","amount","date"]'),
  (3, 'LOAN', '借款合同', '出借人：${partyA}\n借款人：${partyB}\n借款金额：${amount}\n借款日期：${date}', '["partyA","partyB","amount","date"]'),
  (4, 'COOPERATION', '合作协议', '甲方：${partyA}\n乙方：${partyB}\n合作项目：${project}\n日期：${date}', '["partyA","partyB","project","date"]'),
  (5, 'NDA', '保密协议', '披露方：${partyA}\n接收方：${partyB}\n保密期限：${period}\n日期：${date}', '["partyA","partyB","period","date"]')
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO legal_faq(category, question, answer) VALUES ('合同纠纷','合同逾期未签署怎么办','可先发出书面催告并保存沟通证据。') ON DUPLICATE KEY UPDATE question=question;
