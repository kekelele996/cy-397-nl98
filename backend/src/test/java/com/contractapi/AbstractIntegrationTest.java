package com.contractapi;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.contractapi.dto.GenerateContractRequest;
import com.contractapi.dto.InviteSignersRequest;
import com.contractapi.dto.SignerInviteRequest;
import com.contractapi.entity.Contract;
import com.contractapi.service.SigningService;
import com.contractapi.utils.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {
  @MockBean
  protected TimeProvider timeProvider;

  @Autowired
  protected com.contractapi.service.ContractService contractService;

  @Autowired
  protected SigningService signingService;

  protected LocalDateTime clock;

  protected void fixClock() {
    clock = LocalDateTime.of(2026, 9, 20, 10, 0);
    org.mockito.Mockito.when(timeProvider.now()).thenAnswer(invocation -> clock);
  }

  protected void advanceHours(long hours) {
    clock = clock.plusHours(hours);
  }

  protected Contract generateContract(Long userId) {
    Map<String, String> vars = new LinkedHashMap<>();
    vars.put("partyA", "甲公司");
    vars.put("partyB", "乙公司");
    vars.put("amount", "10000");
    vars.put("date", "2026-10-01");
    return contractService.generate(new GenerateContractRequest(userId, 1L, "测试租赁合同", vars, "TEXT"));
  }

  protected InviteSignersRequest inviteRequest(Long userId, String idA, String idB) {
    List<SignerInviteRequest> signers = List.of(
        new SignerInviteRequest(idA, "张三", 1, clock.plusDays(1)),
        new SignerInviteRequest(idB, "李四", 2, clock.plusDays(2)));
    return new InviteSignersRequest(userId, signers);
  }
}
