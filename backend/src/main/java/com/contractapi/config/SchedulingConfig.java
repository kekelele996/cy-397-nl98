package com.contractapi.config;

import com.contractapi.service.SigningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时把到期未签的合同置为已过期。
 * 测试环境关闭调度，避免后台线程与并发用例互相干扰。
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
  private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

  private final SigningService signingService;

  public SchedulingConfig(SigningService signingService) {
    this.signingService = signingService;
  }

  @Scheduled(initialDelayString = "${app.expire-scan.initial-delay:10000}",
             fixedDelayString = "${app.expire-scan.fixed-delay:60000}")
  public void sweepExpiredContracts() {
    int count = signingService.sweepExpired();
    if (count > 0) {
      log.info("到期扫描将 {} 份合同置为已过期", count);
    }
  }
}
