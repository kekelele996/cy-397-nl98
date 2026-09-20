package com.contractapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contractapi.entity.ContractSigner;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ContractSignerMapper extends BaseMapper<ContractSigner> {

  @Select("SELECT * FROM contract_signers WHERE contract_id = #{contractId} ORDER BY sign_order ASC, id ASC")
  List<ContractSigner> findByContractId(@Param("contractId") Long contractId);

  @Select("SELECT COUNT(1) FROM contract_signers WHERE contract_id = #{contractId} AND signer_id = #{signerId}")
  int countBySignerId(@Param("contractId") Long contractId, @Param("signerId") String signerId);

  @Select("SELECT * FROM contract_signers WHERE contract_id = #{contractId} AND signer_id = #{signerId} FOR UPDATE")
  ContractSigner findForUpdate(@Param("contractId") Long contractId, @Param("signerId") String signerId);

  /** 条件确认签署：重复点击/并发确认时第二笔更新 0 行，天然只有一次成功。 */
  @Update("UPDATE contract_signers SET status = 'SIGNED', signed_at = #{signedAt} "
      + "WHERE id = #{id} AND status = 'PENDING'")
  int markSigned(@Param("id") Long id, @Param("signedAt") LocalDateTime signedAt);

  /** 条件确认拒绝：与签署互斥，先成功的那个落盘。 */
  @Update("UPDATE contract_signers SET status = 'REJECTED', rejected_at = #{rejectedAt} "
      + "WHERE id = #{id} AND status = 'PENDING'")
  int markRejected(@Param("id") Long id, @Param("rejectedAt") LocalDateTime rejectedAt);

  @Select("SELECT COUNT(1) FROM contract_signers WHERE contract_id = #{contractId} AND status = 'PENDING'")
  int countPending(@Param("contractId") Long contractId);

  @Select("SELECT COUNT(1) FROM contract_signers WHERE contract_id = #{contractId} AND sign_order < #{order} AND status <> 'SIGNED'")
  int countEarlierNotSigned(@Param("contractId") Long contractId, @Param("order") Integer order);

  @Select("SELECT COUNT(1) FROM contract_signers WHERE contract_id = #{contractId} AND status = 'PENDING' AND deadline < #{now}")
  int countOverduePending(@Param("contractId") Long contractId, @Param("now") LocalDateTime now);
}
