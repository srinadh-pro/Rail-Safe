package com.railsafe.repository;

import com.railsafe.entity.Role;
import com.railsafe.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    List<User> findByRoleAndEnabledTrueOrderByFullNameAsc(Role role);

    List<User> findByRoleInAndEnabledTrueOrderByFullNameAsc(Collection<Role> roles);

    List<User> findByRoleInOrderByFullNameAsc(Collection<Role> roles);
}
