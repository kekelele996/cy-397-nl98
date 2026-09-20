package com.contractapi.mapper;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contractapi.entity.Contract;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ContractMapper extends BaseMapper<Contract> {

  /** 草稿 -> 待签署，仅当当前状态为草稿时生效（并发邀请只允许一次成功） */
  @Update("UPDATE contracts SET status='PENDING_SIGN', deadline=#{deadline}, updated_at=#{now} WHERE id=#{id} AND status='DRAFT'")
  int markPendingSign(@Param("id") Long id, @Param("deadline") LocalDateTime deadline, @Param("now") LocalDateTime now);

  /** 待签署 -> 已签署/已过期，仅当当前状态为待签署时生效 */
  @Update("UPDATE contracts SET status=#{to}, signed_at=#{signedAt}, updated_at=#{now} WHERE id=#{id} AND status='PENDING_SIGN'")
  int closePendingSign(@Param("id") Long id, @Param("to") String to, @Param("signedAt") LocalDateTime signedAt, @Param("now") LocalDateTime now);

  /** 单份合同到期未签 -> 已过期（惰性检查用） */
  @Update("UPDATE contracts SET status='EXPIRED', updated_at=#{now} WHERE id=#{id} AND status='PENDING_SIGN' AND deadline IS NOT NULL AND deadline < #{now}")
  int expireIfOverdue(@Param("id") Long id, @Param("now") LocalDateTime now);

  /** 全部到期未签合同 -> 已过期（定时任务用） */
  @Update("UPDATE contracts SET status='EXPIRED', updated_at=#{now} WHERE status='PENDING_SIGN' AND deadline IS NOT NULL AND deadline < #{now}")
  int expireAllOverdue(@Param("now") LocalDateTime now);
}
