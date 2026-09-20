MERGE INTO contract_templates (id, type, title, content, variables, enabled) KEY(id) VALUES
  (1, 'LEASE', '租赁合同', '甲方：${partyA}\n乙方：${partyB}\n租金：${amount}\n日期：${date}', '["partyA","partyB","amount","date"]', TRUE),
  (2, 'LABOR', '劳动合同', '用人单位：${employer}\n劳动者：${employee}\n岗位：${position}\n期限：${date}', '["employer","employee","position","date"]', TRUE),
  (3, 'LOAN', '借款合同', '出借人：${lender}\n借款人：${borrower}\n金额：${amount}\n日期：${date}', '["lender","borrower","amount","date"]', TRUE),
  (4, 'PARTNERSHIP', '合作协议', '甲方：${partyA}\n乙方：${partyB}\n合作内容：${subject}\n日期：${date}', '["partyA","partyB","subject","date"]', TRUE),
  (5, 'NDA', '保密协议', '披露方：${discloser}\n接收方：${receiver}\n日期：${date}', '["discloser","receiver","date"]', TRUE),
  (6, 'DISABLED', '停用模板', '正文：${x}', '["x"]', FALSE);

MERGE INTO legal_faq (id, category, question, answer) KEY(id) VALUES (1, '合同纠纷', '合同逾期未签署怎么办', '可先发出书面催告并保存沟通证据。');
