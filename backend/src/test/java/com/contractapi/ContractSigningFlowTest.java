package com.contractapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.contractapi.constants.ContractStatus;
import com.contractapi.constants.ErrorCode;
import com.contractapi.constants.SignerStatus;
import com.contractapi.dto.GenerateContractRequest;
import com.contractapi.dto.InviteSignersRequest;
import com.contractapi.dto.InviteSignersRequest.SignerItem;
import com.contractapi.dto.SignConfirmRequest;
import com.contractapi.entity.Contract;
import com.contractapi.entity.ContractSigner;
import com.contractapi.exception.ApiException;
import com.contractapi.mapper.ContractMapper;
import com.contractapi.mapper.ContractSignerMapper;
import com.contractapi.service.ContractExpiryService;
import com.contractapi.service.ContractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ContractSigningFlowTest {
  private static final long OWNER = 100L;

  @Autowired private ContractService contractService;
  @Autowired private ContractExpiryService expiryService;
  @Autowired private ContractMapper contractMapper;
  @Autowired private ContractSignerMapper signerMapper;

  @BeforeEach
  void clean() {
    signerMapper.delete(null);
    contractMapper.delete(null);
  }

  private Contract newDraft() {
    return contractService.generate(new GenerateContractRequest(OWNER, 1L, "测试合同",
        Map.of("partyA", "甲公司", "partyB", "乙公司", "amount", "1000", "date", "2026-09-20"), "TEXT"));
  }

  private InviteSignersRequest inviteOf(SignerItem... items) {
    return new InviteSignersRequest(OWNER, LocalDateTime.now().plusDays(3), List.of(items));
  }

  private Contract pendingContract() {
    Contract contract = newDraft();
    return contractService.invite(contract.getId(),
        inviteOf(new SignerItem("张三", 55L, 1), new SignerItem("李四", 66L, 2)));
  }

  @Test
  void generatePersistsContentAndVariables() {
    Contract contract = newDraft();
    assertEquals(ContractStatus.DRAFT.name(), contract.getStatus());
    Contract stored = contractMapper.selectById(contract.getId());
    assertTrue(stored.getContent().contains("甲公司"));
    assertFalse(stored.getContent().contains("${partyA}"));
    assertTrue(stored.getVariables().contains("\"partyA\":\"甲公司\""));
    assertEquals("TEXT", stored.getFormat());
  }

  @Test
  void generateWithUnknownTemplateFails() {
    ApiException ex = assertThrows(ApiException.class, () ->
        contractService.generate(new GenerateContractRequest(OWNER, 999L, "x", Map.of(), "TEXT")));
    assertEquals(ErrorCode.TEMPLATE_NOT_FOUND, ex.getCode());
  }

  @Test
  void inviteRegistersIdentityOrderAndDeadline() {
    Contract contract = pendingContract();
    assertEquals(ContractStatus.PENDING_SIGN.name(), contract.getStatus());
    assertNotNull(contract.getDeadline());
    List<ContractSigner> signers = contract.getSigners();
    assertEquals(2, signers.size());
    assertEquals("张三", signers.get(0).getSignerName());
    assertEquals(55L, signers.get(0).getSignerUserId());
    assertEquals(1, signers.get(0).getSignOrder());
    assertEquals(SignerStatus.PENDING.name(), signers.get(0).getStatus());
    assertEquals(2, signers.get(1).getSignOrder());
  }

  @Test
  void inviteByNonOwnerIsForbidden() {
    Contract contract = newDraft();
    ApiException ex = assertThrows(ApiException.class, () ->
        contractService.invite(contract.getId(),
            new InviteSignersRequest(999L, LocalDateTime.now().plusDays(3), List.of(new SignerItem("张三", 55L, 1)))));
    assertEquals(ErrorCode.FORBIDDEN, ex.getCode());
  }

  @Test
  void duplicateInviteIsRejected() {
    Contract contract = pendingContract();
    ApiException ex = assertThrows(ApiException.class, () ->
        contractService.invite(contract.getId(), inviteOf(new SignerItem("王五", 77L, 1))));
    assertEquals(ErrorCode.CONTRACT_STATUS_INVALID, ex.getCode());
    assertEquals(2, signerMapper.selectCount(null));
  }

  @Test
  void signAllInOrderAutoCompletesContract() {
    Contract contract = pendingContract();
    Contract midway = contractService.sign(contract.getId(), new SignConfirmRequest("张三", 55L));
    assertEquals(ContractStatus.PENDING_SIGN.name(), midway.getStatus());
    assertEquals(SignerStatus.SIGNED.name(), midway.getSigners().get(0).getStatus());
    assertNotNull(midway.getSigners().get(0).getSignedAt());

    Contract done = contractService.sign(contract.getId(), new SignConfirmRequest("李四", 66L));
    assertEquals(ContractStatus.SIGNED.name(), done.getStatus());
    assertNotNull(done.getSignedAt());
    assertTrue(done.getSigners().stream().allMatch(s -> SignerStatus.SIGNED.name().equals(s.getStatus())));
    assertTrue(done.getSigners().stream().allMatch(s -> s.getSignedAt() != null));
    assertEquals(ContractStatus.SIGNED.name(), contractMapper.selectById(contract.getId()).getStatus());
  }

  @Test
  void signOutOfOrderIsRejected() {
    Contract contract = pendingContract();
    ApiException ex = assertThrows(ApiException.class, () ->
        contractService.sign(contract.getId(), new SignConfirmRequest("李四", 66L)));
    assertEquals(ErrorCode.SIGN_ORDER_NOT_READY, ex.getCode());
    assertEquals(2, signerMapper.selectCount(null).intValue());
    assertTrue(signerMapper.selectList(null).stream().allMatch(s -> SignerStatus.PENDING.name().equals(s.getStatus())));
  }

  @Test
  void duplicateSignOnlyOneSucceeds() {
    Contract contract = pendingContract();
    contractService.sign(contract.getId(), new SignConfirmRequest("张三", 55L));
    ApiException ex = assertThrows(ApiException.class, () ->
        contractService.sign(contract.getId(), new SignConfirmRequest("张三", 55L)));
    assertEquals(ErrorCode.SIGNER_ALREADY_CONFIRMED, ex.getCode());
    assertEquals(1, signerMapper.selectList(null).stream()
        .filter(s -> SignerStatus.SIGNED.name().equals(s.getStatus())).count());
  }

  @Test
  void concurrentSignOnlyOneSucceeds() throws Exception {
    Contract contract = pendingContract();
    int threads = 2;
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    List<Throwable> failures = new CopyOnWriteArrayList<>();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads; i++) {
      pool.submit(() -> {
        ready.countDown();
        try {
          start.await();
          contractService.sign(contract.getId(), new SignConfirmRequest("张三", 55L));
          successes.incrementAndGet();
        } catch (Throwable t) {
          failures.add(t);
        }
      });
    }
    ready.await();
    start.countDown();
    pool.shutdown();
    assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
    assertEquals(1, successes.get(), "并发确认只允许一次成功");
    assertEquals(1, failures.size());
    assertEquals(SignerStatus.SIGNED.name(), signerMapper.selectList(null).get(0).getStatus());
  }

  @Test
  void unknownSignerAndIdentityMismatchAreRejected() {
    Contract contract = pendingContract();
    ApiException notInvited = assertThrows(ApiException.class, () ->
        contractService.sign(contract.getId(), new SignConfirmRequest("陌生人", 1L)));
    assertEquals(ErrorCode.SIGNER_NOT_FOUND, notInvited.getCode());
    ApiException mismatched = assertThrows(ApiException.class, () ->
        contractService.sign(contract.getId(), new SignConfirmRequest("张三", 999L)));
    assertEquals(ErrorCode.FORBIDDEN, mismatched.getCode());
    assertTrue(signerMapper.selectList(null).stream().allMatch(s -> SignerStatus.PENDING.name().equals(s.getStatus())));
  }

  @Test
  void rejectExpiresContract() {
    Contract contract = pendingContract();
    Contract expired = contractService.reject(contract.getId(), new SignConfirmRequest("李四", 66L));
    assertEquals(ContractStatus.EXPIRED.name(), expired.getStatus());
    assertEquals(SignerStatus.REJECTED.name(), expired.getSigners().get(1).getStatus());
    ApiException ex = assertThrows(ApiException.class, () ->
        contractService.sign(contract.getId(), new SignConfirmRequest("张三", 55L)));
    assertEquals(ErrorCode.CONTRACT_EXPIRED, ex.getCode());
  }

  @Test
  void overdueContractExpiresOnTouch() {
    Contract contract = pendingContract();
    Contract stored = contractMapper.selectById(contract.getId());
    stored.setDeadline(LocalDateTime.now().minusMinutes(1));
    contractMapper.updateById(stored);
    ApiException ex = assertThrows(ApiException.class, () ->
        contractService.sign(contract.getId(), new SignConfirmRequest("张三", 55L)));
    assertEquals(ErrorCode.CONTRACT_EXPIRED, ex.getCode());
    assertEquals(ContractStatus.EXPIRED.name(), contractMapper.selectById(contract.getId()).getStatus());
    assertTrue(signerMapper.selectList(null).stream().allMatch(s -> SignerStatus.PENDING.name().equals(s.getStatus())));
  }

  @Test
  void scheduledTaskExpiresOverdueContracts() {
    Contract contract = pendingContract();
    Contract stored = contractMapper.selectById(contract.getId());
    stored.setDeadline(LocalDateTime.now().minusMinutes(1));
    contractMapper.updateById(stored);
    expiryService.expireAllOverdue();
    assertEquals(ContractStatus.EXPIRED.name(), contractMapper.selectById(contract.getId()).getStatus());
  }

  @Test
  void terminalStatesAreImmutable() {
    Contract signed = pendingContract();
    contractService.sign(signed.getId(), new SignConfirmRequest("张三", 55L));
    contractService.sign(signed.getId(), new SignConfirmRequest("李四", 66L));
    assertThrows(ApiException.class, () -> contractService.invite(signed.getId(), inviteOf(new SignerItem("王五", 77L, 1))));
    assertThrows(ApiException.class, () -> contractService.sign(signed.getId(), new SignConfirmRequest("张三", 55L)));
    assertThrows(ApiException.class, () -> contractService.reject(signed.getId(), new SignConfirmRequest("张三", 55L)));

    Contract expired = pendingContract();
    contractService.reject(expired.getId(), new SignConfirmRequest("张三", 55L));
    assertThrows(ApiException.class, () -> contractService.invite(expired.getId(), inviteOf(new SignerItem("王五", 77L, 1))));
    assertThrows(ApiException.class, () -> contractService.sign(expired.getId(), new SignConfirmRequest("李四", 66L)));
    assertThrows(ApiException.class, () -> contractService.reject(expired.getId(), new SignConfirmRequest("李四", 66L)));
  }

  @Test
  void failedInviteRollsBackEverything() {
    Contract contract = newDraft();
    String tooLongName = "长".repeat(200);
    assertThrows(Exception.class, () ->
        contractService.invite(contract.getId(),
            inviteOf(new SignerItem("张三", 55L, 1), new SignerItem(tooLongName, 66L, 2))));
    assertEquals(ContractStatus.DRAFT.name(), contractMapper.selectById(contract.getId()).getStatus());
    assertEquals(0, signerMapper.selectCount(null).intValue());
  }

  @Test
  void listFiltersByStatusConsistently() {
    Contract draft = newDraft();
    Contract signed = pendingContract();
    contractService.sign(signed.getId(), new SignConfirmRequest("张三", 55L));
    contractService.sign(signed.getId(), new SignConfirmRequest("李四", 66L));
    Contract expired = pendingContract();
    contractService.reject(expired.getId(), new SignConfirmRequest("张三", 55L));

    List<Contract> signedList = contractService.list(OWNER, "SIGNED");
    assertEquals(1, signedList.size());
    assertEquals(signed.getId(), signedList.get(0).getId());
    List<Contract> expiredList = contractService.list(OWNER, "EXPIRED");
    assertEquals(1, expiredList.size());
    assertEquals(expired.getId(), expiredList.get(0).getId());
    List<Contract> draftList = contractService.list(OWNER, "DRAFT");
    assertEquals(1, draftList.size());
    assertEquals(draft.getId(), draftList.get(0).getId());
    assertEquals(3, contractService.list(OWNER, null).size());
    Contract reread = contractService.detail(signed.getId());
    assertEquals(ContractStatus.SIGNED.name(), reread.getStatus());
    assertNotNull(reread.getSignedAt());
  }

  @Test
  void invalidStatusFilterIsRejected() {
    ApiException ex = assertThrows(ApiException.class, () -> contractService.list(OWNER, "BOGUS"));
    assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
  }
}
