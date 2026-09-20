package com.contractapi.service;

import java.time.LocalDateTime;
import com.contractapi.mapper.ContractMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合同到期处理：到期未签转为已过期。
 * 独立事务提交，避免调用方后续异常把过期状态回滚。
 */
@Service
public class ContractExpiryService {
  private static final Logger log = LoggerFactory.getLogger(ContractExpiryService.class);

  private final ContractMapper contractMapper;

  public ContractExpiryService(ContractMapper contractMapper) {
    this.contractMapper = contractMapper;
  }

  /** 惰性检查：单份合同到期未签则转为已过期 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void expireIfOverdue(Long contractId) {
    int expired = contractMapper.expireIfOverdue(contractId, LocalDateTime.now());
    if (expired > 0) {
      log.info("合同到期未签，转为已过期 id={}", contractId);
    }
  }

  /** 定时扫描：全部到期未签合同转为已过期 */
  @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
  @Transactional
  public void expireAllOverdue() {
    int expired = contractMapper.expireAllOverdue(LocalDateTime.now());
    if (expired > 0) {
      log.info("定时任务处理到期合同 {} 份", expired);
    }
  }
}
