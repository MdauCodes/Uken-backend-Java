package com.mdau.ukena.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u WHERE u.creatorId = :creatorId")
    Optional<User> findByCreatorId(@Param("creatorId") String creatorId);

    @Query("SELECT u FROM User u WHERE u.creatorId IN :creatorIds")
    List<User> findAllByCreatorIdIn(@Param("creatorIds") List<String> creatorIds);
}