package com.contractapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contractapi.entity.Contract;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ContractMapper extends BaseMapper<Contract> {

  /** 行级加锁读取，保证同一合同的并发确认只有一个事务能继续推进。 */
  @Select("SELECT * FROM contracts WHERE id = #{id} FOR UPDATE")
  Contract selectByIdForUpdate(@Param("id") Long id);

  /** 草稿进入待签署：只有草稿状态才能落盘，并发邀请只成功一次。 */
  @Update("UPDATE contracts SET status = #{toStatus} WHERE id = #{id} AND status = #{fromStatus}")
  int casStatus(@Param("id") Long id,
                @Param("fromStatus") String fromStatus,
                @Param("toStatus") String toStatus);

  /** 全部签署完成：签署记录与合同终态在同一条 SQL 一起落盘。 */
  @Update("UPDATE contracts SET status = 'SIGNED', signed_at = #{signedAt} WHERE id = #{id} AND status = 'PENDING_SIGN'")
  int markSigned(@Param("id") Long id, @Param("signedAt") LocalDateTime signedAt);

  /** 惰性到期：该合同存在逾期未签签署方时一次性置为已过期。 */
  @Update("UPDATE contracts SET status = 'EXPIRED' WHERE id = #{id} AND status = 'PENDING_SIGN' AND id IN ("
      + "SELECT contract_id FROM contract_signers "
      + "WHERE status = 'PENDING' AND deadline < #{now})")
  int expireOneIfDue(@Param("id") Long id, @Param("now") LocalDateTime now);

  /** 到期扫描：仍待签署且存在逾期签署方的合同一次性置为已过期。 */
  @Update("UPDATE contracts SET status = 'EXPIRED' "
      + "WHERE status = 'PENDING_SIGN' AND id IN ("
      + "SELECT s.contract_id FROM contract_signers s "
      + "WHERE s.status = 'PENDING' AND s.deadline < #{now})")
  int expireOverdue(@Param("now") LocalDateTime now);
}
