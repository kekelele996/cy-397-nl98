package com.contractapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.contractapi.constants.ContractStatus;
import com.contractapi.constants.SignerStatus;
import com.contractapi.dto.ContractDetail;
import com.contractapi.entity.Contract;
import com.contractapi.entity.ContractSigner;
import com.contractapi.exception.ApiException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

class ContractSigningConcurrencyTest extends AbstractIntegrationTest {

  private static long userSeq = 2000;
  private Long userId;

  @BeforeEach
  void setUp() {
    fixClock();
    userSeq++;
    userId = userSeq;
  }

  /** 同一签署方并发重复确认：只允许一次成功。 */
  @RepeatedTest(5)
  void concurrent_duplicate_sign_allows_only_one_success() throws Exception {
    Contract contract = generateContract(userId);
    signingService.inviteSigners(contract.getId(), inviteRequest(userId, "u-a", "u-b"));

    BatchResult result = runConcurrently(12, () -> signingService.sign(contract.getId(), "u-a"));

    assertThat(result.success()).isEqualTo(1);
    assertThat(result.unexpectedErrors()).isEmpty();

    ContractDetail detail = contractService.getDetail(contract.getId());
    ContractSigner signerA = detail.signers().get(0);
    assertThat(signerA.getStatus()).isEqualTo(SignerStatus.SIGNED.name());
    assertThat(signerA.getSignedAt()).isEqualTo(clock);
    // 第二名签署方未动，合同仍停留在待签署
    assertThat(detail.status()).isEqualTo(ContractStatus.PENDING_SIGN.name());
    assertThat(detail.signers().get(1).getStatus()).isEqualTo(SignerStatus.PENDING.name());
  }

  /** 并发“签署 vs 拒绝”：先落盘的唯一一种结果生效，不存在半成功状态。 */
  @RepeatedTest(5)
  void concurrent_sign_vs_reject_has_single_outcome() throws Exception {
    Contract contract = generateContract(userId);
    signingService.inviteSigners(contract.getId(), inviteRequest(userId, "u-a", "u-b"));
    signingService.sign(contract.getId(), "u-a");

    AtomicInteger idx = new AtomicInteger();
    BatchResult result = runConcurrently(10, () -> {
      if (idx.getAndIncrement() % 2 == 0) {
        signingService.sign(contract.getId(), "u-b");
      } else {
        signingService.reject(contract.getId(), "u-b");
      }
    });

    assertThat(result.success()).isEqualTo(1);
    assertThat(result.unexpectedErrors()).isEmpty();

    ContractDetail detail = contractService.getDetail(contract.getId());
    ContractSigner signerB = detail.signers().get(1);
    if (SignerStatus.SIGNED.name().equals(signerB.getStatus())) {
      assertThat(detail.status()).isEqualTo(ContractStatus.SIGNED.name());
      assertThat(detail.signedAt()).isEqualTo(clock);
    } else {
      assertThat(signerB.getStatus()).isEqualTo(SignerStatus.REJECTED.name());
      assertThat(detail.status()).isEqualTo(ContractStatus.EXPIRED.name());
      assertThat(detail.signedAt()).isNull();
    }
    // 此后终态不可再调整
    assertTerminal(contract.getId(), "u-b");
  }

  /** 不同签署方按顺序并发确认：合同最终整体转为已签署，无记录丢失。 */
  @RepeatedTest(3)
  void sequential_signers_under_concurrency_end_signed() throws Exception {
    Contract contract = generateContract(userId);
    signingService.inviteSigners(contract.getId(), inviteRequest(userId, "u-a", "u-b"));

    AtomicInteger idx = new AtomicInteger();
    // 第一轮并发 u-a 与 u-b 混在一起；之后再补一轮，确保顺序放开后 u-b 也完成。
    BatchResult first = runConcurrently(8, () -> {
      int n = idx.getAndIncrement();
      signingService.sign(contract.getId(), n % 2 == 0 ? "u-a" : "u-b");
    });
    BatchResult second = runConcurrently(4, () -> signingService.sign(contract.getId(), "u-b"));

    assertThat(first.unexpectedErrors()).isEmpty();
    assertThat(second.unexpectedErrors()).isEmpty();
    // 两批合起来 u-a 成功一次、u-b 成功一次
    assertThat(first.success() + second.success()).isEqualTo(2);

    ContractDetail detail = contractService.getDetail(contract.getId());
    assertThat(detail.status()).isEqualTo(ContractStatus.SIGNED.name());
    assertThat(detail.signers()).extracting(ContractSigner::getStatus)
        .containsExactly(SignerStatus.SIGNED.name(), SignerStatus.SIGNED.name());
    assertThat(detail.signers()).extracting(ContractSigner::getSignedAt).doesNotContainNull();
  }

  /** 并发重复邀请：只有一次登记成功，其余全部冲突且不产生多余签署方。 */
  @RepeatedTest(3)
  void concurrent_invite_registers_signers_once() throws Exception {
    Contract contract = generateContract(userId);
    BatchResult result = runConcurrently(6, () ->
        signingService.inviteSigners(contract.getId(), inviteRequest(userId, "u-a", "u-b")));

    assertThat(result.success()).isEqualTo(1);
    assertThat(result.unexpectedErrors()).isEmpty();

    ContractDetail detail = contractService.getDetail(contract.getId());
    assertThat(detail.status()).isEqualTo(ContractStatus.PENDING_SIGN.name());
    assertThat(detail.signers()).hasSize(2);
    assertThat(detail.signers()).extracting(ContractSigner::getSignerId)
        .containsExactly("u-a", "u-b");
  }

  private void assertTerminal(Long contractId, String signerId) {
    try {
      signingService.sign(contractId, signerId);
      throw new AssertionError("终态合同不应允许再签署");
    } catch (ApiException expected) {
      assertThat(expected.getCode()).isEqualTo("CONTRACT_TERMINAL");
    }
  }

  private BatchResult runConcurrently(int threads, Runnable action) throws InterruptedException {
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();
    ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

    for (int i = 0; i < threads; i++) {
      pool.submit(() -> {
        ready.countDown();
        try {
          start.await();
          action.run();
          success.incrementAndGet();
        } catch (ApiException expectedConflict) {
          // 冲突类错误是并发下的预期路径
        } catch (Throwable other) {
          errors.add(other);
        }
        return null;
      });
    }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    return new BatchResult(success.get(), List.copyOf(errors));
  }

  private record BatchResult(int success, List<Throwable> unexpectedErrors) {}
}
