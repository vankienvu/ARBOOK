package com.arbook.backend.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arbook.backend.user.entity.Role;
import com.arbook.backend.user.entity.RoleCode;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(RoleCode code);
}
