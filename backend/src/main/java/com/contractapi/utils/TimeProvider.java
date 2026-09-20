package com.contractapi.utils;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** 统一时间来源，便于测试中控制“当前时间”验证到期流转。 */
@Component
public class TimeProvider {
  private final Clock clock;

  public TimeProvider() {
    this(Clock.systemDefaultZone());
  }

  TimeProvider(Clock clock) {
    this.clock = clock;
  }

  public LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
