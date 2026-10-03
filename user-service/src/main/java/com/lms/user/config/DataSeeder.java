package com.lms.user.config;

import com.lms.user.entity.Role;
import com.lms.user.entity.User;
import com.lms.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds sample users on first start (only when the table is empty).
 * Insert order matters: admin=1, instructor=2, instructor2=3, student1=4, student2=5, student3=6.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        List<User> users = List.of(
                build("System Admin", "admin@example.com", "Admin@123", Role.ADMIN),
                build("Ian Instructor", "instructor@example.com", "Instructor@123", Role.INSTRUCTOR),
                build("Priya Professor", "instructor2@example.com", "Instructor@123", Role.INSTRUCTOR),
                build("Sam Student", "student1@example.com", "Student@123", Role.STUDENT),
                build("Sara Student", "student2@example.com", "Student@123", Role.STUDENT),
                build("Sanjay Student", "student3@example.com", "Student@123", Role.STUDENT)
        );
        for (User user : users) {
            userRepository.save(user);
        }
        log.info("Seeded {} sample users", users.size());
    }

    private User build(String name, String email, String rawPassword, Role role) {
        return User.builder().name(name).email(email)
                .password(passwordEncoder.encode(rawPassword)).role(role).build();
    }
}
