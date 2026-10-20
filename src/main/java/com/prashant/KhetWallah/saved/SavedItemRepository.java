package com.prashant.KhetWallah.saved;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SavedItemRepository extends JpaRepository<SavedItem,Long>{
 boolean existsByAccountIdAndTargetTypeAndTargetId(Long accountId,String targetType,Long targetId);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select s from SavedItem s where s.accountId=:accountId and s.targetType=:targetType and s.targetId=:targetId")
 Optional<SavedItem> findForUpdate(@org.springframework.data.repository.query.Param("accountId") Long accountId,@org.springframework.data.repository.query.Param("targetType") String targetType,@org.springframework.data.repository.query.Param("targetId") Long targetId);
 Optional<SavedItem> findByAccountIdAndTargetTypeAndTargetId(Long accountId,String targetType,Long targetId);
}
