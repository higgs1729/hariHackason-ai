package com.hanamizuki.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hanamizuki.backend.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Soft-deleted rows are excluded by the entity's @SQLRestriction. */
    Optional<User> findByUserAccount(String userAccount);

    boolean existsByUserAccount(String userAccount);

    /**
     * ID かニックネームの部分一致。/ 账号或昵称模糊匹配。
     *
     * <p>Self is excluded here rather than filtered afterwards, so the limit
     * counts rows the caller can actually act on.
     *
     * <p>No index covers a leading-wildcard LIKE, so this is a table scan. At
     * a hackathon's user count that is the right trade against the work a
     * full-text index would cost; it is the first thing to revisit if the
     * table ever grows.
     */
    @Query("select u from User u where u.id <> :selfId and ("
            + "lower(u.userAccount) like lower(concat('%', :q, '%')) or "
            + "lower(u.userName) like lower(concat('%', :q, '%')))")
    List<User> search(@Param("q") String q, @Param("selfId") Long selfId, Limit limit);
}
