package com.github.senocak.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.github.senocak.file.entity.User;

public interface UserRepository extends JpaRepository<User,Long> {
    User findByEmail(String email);
}
