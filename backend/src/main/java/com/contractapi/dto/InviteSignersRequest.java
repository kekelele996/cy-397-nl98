package com.contractapi.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InviteSignersRequest(Long userId, LocalDateTime deadline, List<SignerItem> signers) {
  public record SignerItem(String signerName, Long signerUserId, Integer signOrder) {}
}
