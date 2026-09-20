package com.contractapi.mapper;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contractapi.entity.ContractSigner;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ContractSignerMapper extends BaseMapper<ContractSigner> {

  /** 待签署 -> 已签署并记录签署时间，仅当当前为待签署时生效（重复/并发确认只允许一次成功） */
  @Update("UPDATE contract_signers SET status='SIGNED', signed_at=#{signedAt} WHERE id=#{id} AND status='PENDING'")
  int markSigned(@Param("id") Long id, @Param("signedAt") LocalDateTime signedAt);

  /** 待签署 -> 已拒绝，仅当当前为待签署时生效 */
  @Update("UPDATE contract_signers SET status='REJECTED' WHERE id=#{id} AND status='PENDING'")
  int markRejected(@Param("id") Long id);
}
