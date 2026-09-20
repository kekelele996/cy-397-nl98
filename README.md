# 合同模板生成与法律工单 API

```bash
cp .env.example .env
docker compose up -d --build
```

合同模板生成与法律工单 API 提供合同模板管理、变量填充生成、签署状态跟踪、法律咨询工单和法律 FAQ 检索能力。

## 项目主要功能

- 管理租赁、劳动、借款、合作、保密协议模板与占位变量（停用模板不可用于生成）。
- 根据变量生成纯文本/HTML 合同，生成时留存渲染正文与填充参数，支持导出 PDF（wkhtmltopdf）。
- 草稿可邀请多名签署方，登记身份、签署顺序与各自截止时间。
- 签署状态支持草稿、待签署、已签署、已过期：全部顺序签署完成自动转为已签署并记录每人签署时间；任一人拒绝或到期未签转为已过期；终态不可再调整。
- 签署确认基于行锁与条件更新，重复、越权或并发确认只允许一次成功，签署记录与状态在同一事务一次落盘。
- 法律工单提交、分配、回复和关闭。
- 法律 FAQ 分类维护与关键词搜索。
- 用户合同库与模板收藏。

## 本地开发

```bash
cd backend
mvn spring-boot:run
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
│   └── src/main/resources
├── database
│   └── init.sql
└── docker-compose.yml
```

## 主要 API

- `GET /api/templates` 可用模板列表
- `POST /api/templates` 新增模板
- `POST /api/contracts/generate` 从可用模板生成合同（留存正文与填充参数）
- `POST /api/contracts/{id}/signers` 草稿邀请签署方（身份、顺序、截止时间）
- `POST /api/contracts/{id}/sign/{signerId}` 签署方确认签署
- `POST /api/contracts/{id}/reject/{signerId}` 签署方拒绝签署
- `GET /api/contracts/{id}` 合同详情（含各方签署状态与时间）
- `GET /api/contracts?userId=&status=` 用户合同库，按状态筛选
- `POST /api/tickets` 提交法律工单
- `POST /api/tickets/{id}/replies` 添加工单回复
- `GET /api/knowledge` 搜索法律 FAQ

## 合同签署闭环

状态流转：`DRAFT（草稿）→ PENDING_SIGN（待签署）→ SIGNED（已签署）/ EXPIRED（已过期）`。

1. `POST /api/contracts/generate` 选择**可用模板**并提交变量，系统渲染正文并把 `content`（正文）与 `fillParams`（填充参数 JSON）一并落盘，状态为草稿。
2. `POST /api/contracts/{id}/signers` 由创建人一次性邀请多名签署方，登记 `signerId`、姓名、`signOrder`（顺序，不可重复）与 `deadline`（各自截止时间），合同进入待签署。
3. 签署方按顺序调用 `POST /api/contracts/{id}/sign/{signerId}`：越权（不在名单）、顺序未到、已到期、重复/并发确认都会被拒绝；全部签署后合同自动转为已签署，并记录每人的 `signedAt` 与合同 `signedAt`。
4. 任一人调用 `POST /api/contracts/{id}/reject/{signerId}`，或任一签署方到期未签（读取详情惰性生效 / 定时扫描生效），合同转为已过期。
5. 已签署、已过期均为终态，不能再邀请、签署或拒绝。签署记录与合同状态在同一数据库事务内一次落盘，任一步失败整体回滚。

邀请签署方请求示例：

```json
{
  "userId": 1001,
  "signers": [
    {"signerId": "party-a", "signerName": "张三", "signOrder": 1, "deadline": "2026-09-25T18:00:00"},
    {"signerId": "party-b", "signerName": "李四", "signOrder": 2, "deadline": "2026-09-30T18:00:00"}
  ]
}
```

## 环境变量说明

| 变量 | 说明 |
| --- | --- |
| `COMPOSE_PROJECT_NAME` | Compose 项目名，默认 `contractapi` |
| `MYSQL_*` | MySQL 数据库配置 |
| `JWT_SECRET` | JWT 签名密钥 |

## License

MIT
