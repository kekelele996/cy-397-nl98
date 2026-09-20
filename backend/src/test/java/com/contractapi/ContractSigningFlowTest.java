package com.contractapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contractapi.constants.ContractStatus;
import com.contractapi.constants.SignerStatus;
import com.contractapi.dto.ContractDetail;
import com.contractapi.dto.GenerateContractRequest;
import com.contractapi.dto.InviteSignersRequest;
import com.contractapi.dto.SignerInviteRequest;
import com.contractapi.entity.Contract;
import com.contractapi.entity.ContractSigner;
import com.contractapi.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractSigningFlowTest extends AbstractIntegrationTest {

  private static final Function<ContractSigner, String> STATUS = ContractSigner::getStatus;

  private static long userSeq = 1000;
  private Long userId;

  @BeforeEach
  void setUp() {
    fixClock();
    userSeq++;
    userId = userSeq;
  }

  @Test
  void generate_keeps_rendered_body_and_fill_params() {
    Contract contract = generateContract(userId);

    ContractDetail detail = contractService.getDetail(contract.getId());
    assertThat(detail.status()).isEqualTo(ContractStatus.DRAFT.name());
    assertThat(detail.content())
        .contains("甲方：甲公司")
        .contains("乙方：乙公司")
        .contains("租金：10000")
        .contains("日期：2026-10-01")
        .doesNotContain("${");
    assertThat(detail.fillParams())
        .contains("\"partyA\":\"甲公司\"")
        .contains("\"amount\":\"10000\"");
  }

  @Test
  void generate_rejects_disabled_template_and_missing_variables() {
    assertThatThrownBy(() ->
        contractService.generate(new GenerateContractRequest(userId, 6L, "停用", Map.of(), null)))
        .isInstanceOf(ApiException.class)
        .extracting("code").isEqualTo("TEMPLATE_UNAVAILABLE");

    assertThatThrownBy(() ->
        contractService.generate(new GenerateContractRequest(userId, 1L, "缺变量", Map.of("partyA", "甲"), null)))
        .isInstanceOf(ApiException.class)
        .extracting("code").isEqualTo("MISSING_TEMPLATE_VARIABLE");
  }

  @Test
  void all_signers_complete_contract_becomes_signed_with_timestamps() {
    Contract contract = generateContract(userId);
    ContractDetail pending = signingService.inviteSigners(contract.getId(),
        inviteRequest(userId, "u-a", "u-b"));

    assertThat(pending.status()).isEqualTo(ContractStatus.PENDING_SIGN.name());
    assertThat(pending.signers()).hasSize(2);
    assertThat(pending.signers()).extracting(STATUS)
        .containsExactly(SignerStatus.PENDING.name(), SignerStatus.PENDING.name());
    assertThat(pending.signers()).extracting(ContractSigner::getSignOrder).containsExactly(1, 2);

    ContractDetail afterFirst = signingService.sign(contract.getId(), "u-a");
    assertThat(afterFirst.status()).isEqualTo(ContractStatus.PENDING_SIGN.name());
    assertThat(afterFirst.signers().get(0).getStatus()).isEqualTo(SignerStatus.SIGNED.name());
    assertThat(afterFirst.signers().get(0).getSignedAt()).isEqualTo(clock);
    assertThat(afterFirst.signers().get(1).getStatus()).isEqualTo(SignerStatus.PENDING.name());
    assertThat(afterFirst.signedAt()).isNull();

    ContractDetail signed = signingService.sign(contract.getId(), "u-b");
    assertThat(signed.status()).isEqualTo(ContractStatus.SIGNED.name());
    assertThat(signed.signedAt()).isEqualTo(clock);
    assertThat(signed.signers()).extracting(STATUS)
        .containsExactly(SignerStatus.SIGNED.name(), SignerStatus.SIGNED.name());
    assertThat(signed.signers().get(1).getSignedAt()).isEqualTo(clock);
  }

  @Test
  void signing_must_follow_order() {
    Contract contract = generateContract(userId);
    signingService.inviteSigners(contract.getId(), inviteRequest(userId, "u-a", "u-b"));

    assertThatThrownBy(() -> signingService.sign(contract.getId(), "u-b"))
        .isInstanceOf(ApiException.class)
        .extracting("code").isEqualTo("SIGNING_ORDER_BLOCKED");
  }

  @Test
  void any_rejection_expires_contract() {
    Contract contract = generateContract(userId);
    signingService.inviteSigners(contract.getId(), inviteRequest(userId, "u-a", "u-b"));

    signingService.sign(contract.getId(), "u-a");
    ContractDetail expired = signingService.reject(contract.getId(), "u-b");

    assertThat(expired.status()).isEqualTo(ContractStatus.EXPIRED.name());
    assertThat(expired.signers().get(0).getStatus()).isEqualTo(SignerStatus.SIGNED.name());
    assertThat(expired.signers().get(1).getStatus()).isEqualTo(SignerStatus.REJECTED.name());
    assertThat(expired.signers().get(1).getRejectedAt()).isEqualTo(clock);
    assertThat(expired.signedAt()).isNull();
  }

  @Test
  void overdue_pending_signer_expires_contract_on_read() {
    Contract contract = generateContract(userId);
    signingService.inviteSigners(contract.getId(), inviteRequest(userId, "u-a", "u-b"));

    signingService.sign(contract.getId(), "u-a");
    advanceHours(49); // 第二名签署方截止时间为 +2 天，越过该时刻即到期

    ContractDetail detail = contractService.getDetail(contract.getId());
    assertThat(detail.status()).isEqualTo(ContractStatus.EXPIRED.name());
    assertThat(detail.signers().get(1).getStatus()).isEqualTo(SignerStatus.PENDING.name());
  }

  @Test
  void sign_after_deadline_is_rejected_and_contract_expires() {
    Contract contract = generateContract(userId);
    signingService.inviteSigners(contract.getId(), inviteRequest(userId, "u-a", "u-b"));
    advanceHours(25); // 第一名签署方截止时间为 +1 天

    assertThatThrownBy(() -> signingService.sign(contract.getId(), "u-a"))
        .isInstanceOf(ApiException.class)
        .extracting("code").isEqualTo("SIGNING_DEADLINE_PASSED");
    assertThat(contractService.getDetail(contract.getId()).status())
        .isEqualTo(ContractStatus.EXPIRED.name());
  }

  @Test
  void terminal_contracts_cannot_be_adjusted() {
    Contract signed = generateContract(userId);
    signingService.inviteSigners(signed.getId(), inviteRequest(userId, "u-a", "u-b"));
    signingService.sign(signed.getId(), "u-a");
    signingService.sign(signed.getId(), "u-b");

    assertThatThrownBy(() -> signingService.sign(signed.getId(), "u-a"))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CONTRACT_TERMINAL");
    assertThatThrownBy(() -> signingService.reject(signed.getId(), "u-a"))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CONTRACT_TERMINAL");
    assertThatThrownBy(() -> signingService.inviteSigners(signed.getId(),
        inviteRequest(userId, "u-c", "u-d")))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CONTRACT_TERMINAL");

    Contract rejected = generateContract(userId);
    signingService.inviteSigners(rejected.getId(), inviteRequest(userId, "u-a", "u-b"));
    signingService.reject(rejected.getId(), "u-a");

    assertThatThrownBy(() -> signingService.sign(rejected.getId(), "u-b"))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CONTRACT_TERMINAL");
    assertThatThrownBy(() -> signingService.reject(rejected.getId(), "u-b"))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CONTRACT_TERMINAL");
  }

  @Test
  void duplicate_invitation_and_invalid_signers_leave_draft_untouched() {
    Contract contract = generateContract(userId);
    signingService.inviteSigners(contract.getId(), inviteRequest(userId, "u-a", "u-b"));

    // 待签署后不能再次邀请
    assertThatThrownBy(() -> signingService.inviteSigners(contract.getId(),
        new InviteSignersRequest(userId,
            List.of(new SignerInviteRequest("u-c", "王五", 1, clock.plusDays(1))))))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("SIGNING_NOT_OPEN");

    // 草稿校验：身份重复
    Contract draft1 = generateContract(userId);
    assertThatThrownBy(() -> signingService.inviteSigners(draft1.getId(),
        new InviteSignersRequest(userId, List.of(
            new SignerInviteRequest("dup", "张", 1, clock.plusDays(1)),
            new SignerInviteRequest("dup", "李", 2, clock.plusDays(1))))))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("DUPLICATE_SIGNER");

    // 草稿校验：顺序重复
    Contract draft2 = generateContract(userId);
    assertThatThrownBy(() -> signingService.inviteSigners(draft2.getId(),
        new InviteSignersRequest(userId, List.of(
            new SignerInviteRequest("a", "张", 1, clock.plusDays(1)),
            new SignerInviteRequest("b", "李", 1, clock.plusDays(1))))))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_SIGN_ORDER");

    // 草稿校验：截止时间已过
    Contract draft3 = generateContract(userId);
    assertThatThrownBy(() -> signingService.inviteSigners(draft3.getId(),
        new InviteSignersRequest(userId, List.of(
            new SignerInviteRequest("a", "张", 1, clock.minusMinutes(1))))))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_DEADLINE");

    // 任一校验失败：合同仍为草稿、无签署方落盘
    for (Long draftId : List.of(draft1.getId(), draft2.getId(), draft3.getId())) {
      ContractDetail detail = contractService.getDetail(draftId);
      assertThat(detail.status()).isEqualTo(ContractStatus.DRAFT.name());
      assertThat(detail.signers()).isEmpty();
    }
  }

  @Test
  void unknown_signer_or_other_owner_cannot_act() {
    Contract contract = generateContract(userId);
    signingService.inviteSigners(contract.getId(), inviteRequest(userId, "u-a", "u-b"));

    assertThatThrownBy(() -> signingService.sign(contract.getId(), "intruder"))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("SIGNER_NOT_FOUND");

    long otherOwner = 999_999L;
    assertThatThrownBy(() -> signingService.inviteSigners(contract.getId(),
        inviteRequest(otherOwner, "x", "y")))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOT_CONTRACT_OWNER");
  }

  @Test
  void list_filters_by_status_and_reads_back_consistently() {
    Contract draft = generateContract(userId);
    Contract pending = generateContract(userId);
    signingService.inviteSigners(pending.getId(), inviteRequest(userId, "u-a", "u-b"));
    Contract signed = generateContract(userId);
    signingService.inviteSigners(signed.getId(), inviteRequest(userId, "u-a", "u-b"));
    signingService.sign(signed.getId(), "u-a");
    signingService.sign(signed.getId(), "u-b");

    List<Contract> drafts = contractService.list(userId, ContractStatus.DRAFT.name());
    assertThat(drafts).extracting(Contract::getId).contains(draft.getId())
        .doesNotContain(pending.getId(), signed.getId());

    List<Contract> all = contractService.list(userId, null);
    assertThat(all).hasSizeGreaterThanOrEqualTo(3);

    List<Contract> signedList = contractService.list(userId, ContractStatus.SIGNED.name());
    assertThat(signedList).extracting(Contract::getId).containsExactly(signed.getId());
    assertThat(signedList.get(0).getSignedAt()).isNotNull();
  }

  @Test
  void sweep_expires_all_due_contracts_and_filter_reflects_it() {
    Contract c1 = generateContract(userId);
    signingService.inviteSigners(c1.getId(), inviteRequest(userId, "u-a", "u-b"));
    Contract c2 = generateContract(userId);
    signingService.inviteSigners(c2.getId(), inviteRequest(userId, "u-c", "u-d"));
    advanceHours(25);

    int expired = signingService.sweepExpired();
    assertThat(expired).isGreaterThanOrEqualTo(2);
    assertThat(contractService.list(userId, ContractStatus.EXPIRED.name()))
        .extracting(Contract::getId).contains(c1.getId(), c2.getId());
  }
}
