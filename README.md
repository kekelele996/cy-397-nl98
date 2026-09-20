# 合同模板生成与法律工单 API

```bash
cp .env.example .env
docker compose up -d --build
```

合同模板生成与法律工单 API 提供合同模板管理、变量填充生成、多方签署闭环、法律咨询工单和法律 FAQ 检索能力。

## 项目主要功能

- 管理租赁、劳动、借款、合作、保密协议模板与占位变量。
- 从可用模板生成合同，留存渲染后的正文与填充参数。
- 合同签署闭环：草稿邀请多名签署方（登记身份、顺序、截止时间）→ 待签署 → 全部签署完成自动转为已签署并记录各自签署时间；任一人拒绝或到期未签转为已过期；已签署或已过期不可再调整。
- 签署确认幂等：重复、越权或并发确认只允许一次成功，签署记录与状态在同一事务落盘，任一步失败整体回滚。
- 合同库按状态筛选，回读与写入一致。
- 法律工单提交、分配、回复和关闭。
- 法律 FAQ 分类维护与关键词搜索。

## 本地开发

```bash
cd backend
mvn spring-boot:run
```

运行集成测试（H2 内存库，无需 MySQL）：

```bash
cd backend
mvn test
```

## 技术栈

| 类型 | 技术 |
| --- | --- |
| 后端 | Spring Boot + Java 17 |
| ORM | MyBatis-Plus |
| 数据库 | MySQL 8.0 |
| 认证 | JWT |
| PDF | wkhtmltopdf |

## 目录结构

```text
.
├── backend
│   ├── src/main/java/com/contractapi
│   │   ├── controller/   # 接口层
│   │   ├── service/      # 业务与签署流转（ContractService/ContractFlowService/ContractExpiryService）
│   │   ├── mapper/       # MyBatis-Plus Mapper（含条件更新）
│   │   ├── entity/       # 合同、签署方、模板等实体
│   │   ├── dto/          # 请求对象
│   │   ├── constants/    # 状态枚举与错误码
│   │   └── exception/    # 统一异常与全局处理
│   ├── src/main/resources
│   └── src/test          # 签署闭环集成测试
├── database
│   └── init.sql
└── docker-compose.yml
```

## 主要 API

- `GET /api/templates` 模板列表
- `POST /api/templates` 新增模板
- `POST /api/contracts/generate` 合同生成（留存正文与填充参数）
- `POST /api/contracts/{id}/invite` 邀请签署方（身份、顺序、截止时间，草稿 → 待签署）
- `POST /api/contracts/{id}/sign` 签署方确认签署（全部完成 → 已签署）
- `POST /api/contracts/{id}/reject` 签署方拒绝（→ 已过期）
- `GET /api/contracts` 用户合同库（支持 `userId`、`status` 筛选）
- `GET /api/contracts/{id}` 合同详情（含签署方列表）
- `POST /api/contracts/{id}/pdf` 导出 PDF
- `POST /api/tickets` 提交法律工单
- `POST /api/tickets/{id}/replies` 添加工单回复
- `GET /api/knowledge` 搜索法律 FAQ

### 签署流程示例

```bash
# 1. 生成合同（草稿）
curl -X POST localhost:19412/api/contracts/generate \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"templateId":1,"title":"办公室租赁合同","variables":{"partyA":"甲公司","partyB":"乙公司","amount":"5000","date":"2026-09-20"},"format":"TEXT"}'

# 2. 邀请两名签署方（草稿 → 待签署）
curl -X POST localhost:19412/api/contracts/1/invite \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"deadline":"2026-10-01T00:00:00","signers":[{"signerName":"张三","signerUserId":55,"signOrder":1},{"signerName":"李四","signerUserId":66,"signOrder":2}]}'

# 3. 按顺序签署（全部完成后自动转为已签署）
curl -X POST localhost:19412/api/contracts/1/sign -H 'Content-Type: application/json' -d '{"signerName":"张三","signerUserId":55}'
curl -X POST localhost:19412/api/contracts/1/sign -H 'Content-Type: application/json' -d '{"signerName":"李四","signerUserId":66}'
```

## 环境变量说明

| 变量 | 说明 |
| --- | --- |
| `COMPOSE_PROJECT_NAME` | Compose 项目名，默认 `contractapi` |
| `MYSQL_*` | MySQL 数据库配置 |
| `JWT_SECRET` | JWT 签名密钥 |

## License

MIT
