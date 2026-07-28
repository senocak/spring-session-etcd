package com.github.senocak.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.github.senocak.file.entity.Role;

public interface RoleRepository extends JpaRepository<Role,Long> {
    Role findByName(String name);
}
