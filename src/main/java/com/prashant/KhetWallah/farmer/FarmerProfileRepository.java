package com.prashant.KhetWallah.farmer;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface FarmerProfileRepository extends JpaRepository<FarmerProfile,Long>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from FarmerProfile p where p.userId=:id")
 Optional<FarmerProfile> findForUpdate(@Param("id") Long id);
}
