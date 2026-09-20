package com.contractapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractapi.entity.Contract;
import com.contractapi.service.SigningService;
import com.contractapi.utils.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractApiHttpTest {

  @MockBean
  private TimeProvider timeProvider;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private com.contractapi.service.ContractService contractService;

  @Autowired
  private SigningService signingService;

  @BeforeEach
  void setUp() {
    Mockito.when(timeProvider.now()).thenReturn(java.time.LocalDateTime.of(2026, 9, 20, 10, 0));
  }

  @Test
  void generate_invite_sign_sign_flow_over_http() throws Exception {
    Long userId = 3001L;
    String generateBody = """
        {"userId":3001,"templateId":1,"title":"HTTP 租赁合同","format":"TEXT",
         "variables":{"partyA":"甲公司","partyB":"乙公司","amount":"8000","date":"2026-10-01"}}
        """;
    String generated = mockMvc.perform(post("/api/contracts/generate")
            .contentType(MediaType.APPLICATION_JSON).content(generateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("甲公司")))
        .andExpect(jsonPath("$.fillParams").value(org.hamcrest.Matchers.containsString("乙公司")))
        .andReturn().getResponse().getContentAsString();
    long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(generated).get("id").asLong();

    String inviteBody = """
        {"userId":3001,"signers":[
          {"signerId":"a","signerName":"张三","signOrder":1,"deadline":"2026-09-21T10:00:00"},
          {"signerId":"b","signerName":"李四","signOrder":2,"deadline":"2026-09-22T10:00:00"}]}
        """;
    mockMvc.perform(post("/api/contracts/{id}/signers", id)
            .contentType(MediaType.APPLICATION_JSON).content(inviteBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING_SIGN"))
        .andExpect(jsonPath("$.signers[0].signedAt").doesNotExist());

    // 越权签署方
    mockMvc.perform(post("/api/contracts/{id}/sign/intruder", id))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SIGNER_NOT_FOUND"));

    // 顺序未到
    mockMvc.perform(post("/api/contracts/{id}/sign/b", id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SIGNING_ORDER_BLOCKED"));

    mockMvc.perform(post("/api/contracts/{id}/sign/a", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING_SIGN"))
        .andExpect(jsonPath("$.signers[0].status").value("SIGNED"))
        .andExpect(jsonPath("$.signers[0].signedAt").value("2026-09-20T10:00:00"));

    // 重复确认
    mockMvc.perform(post("/api/contracts/{id}/sign/a", id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SIGNER_ALREADY_ACTIONED"));

    mockMvc.perform(post("/api/contracts/{id}/sign/b", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SIGNED"))
        .andExpect(jsonPath("$.signedAt").value("2026-09-20T10:00:00"));

    // 终态再操作
    mockMvc.perform(post("/api/contracts/{id}/sign/b", id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONTRACT_TERMINAL"));

    // 状态筛选回读
    mockMvc.perform(get("/api/contracts").param("userId", String.valueOf(userId)).param("status", "SIGNED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("SIGNED"));
  }

  @Test
  void reject_over_http_expires_contract() throws Exception {
    Long userId = 3002L;
    Contract contract = contractService.generate(new com.contractapi.dto.GenerateContractRequest(
        userId, 1L, "拒绝流",
        java.util.Map.of("partyA", "甲", "partyB", "乙", "amount", "1", "date", "2026-10-01"), null));
    signingService.inviteSigners(contract.getId(),
        new com.contractapi.dto.InviteSignersRequest(userId, java.util.List.of(
            new com.contractapi.dto.SignerInviteRequest("a", "张三", 1,
                java.time.LocalDateTime.of(2026, 9, 21, 10, 0)))));

    mockMvc.perform(post("/api/contracts/{id}/reject/a", contract.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EXPIRED"))
        .andExpect(jsonPath("$.signers[0].status").value("REJECTED"))
        .andExpect(jsonPath("$.signers[0].rejectedAt").value("2026-09-20T10:00:00"));

    mockMvc.perform(get("/api/contracts/{id}", contract.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EXPIRED"));
  }
}
