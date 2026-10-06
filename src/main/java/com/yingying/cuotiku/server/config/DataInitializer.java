package com.yingying.cuotiku.server.config;

import com.yingying.cuotiku.server.entity.Role;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public ApplicationRunner seedAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                       AppProperties properties) {
        return args -> {
            String phone = properties.admin().phone();
            if (phone == null || phone.isBlank() || phone.startsWith("${")) {
                log.warn("未配置 app.admin.phone（环境变量 ADMIN_PHONE），跳过管理员初始化");
                return;
            }
            if (userRepository.existsByPhone(phone)) {
                return;
            }
            User admin = new User();
            admin.setPhone(phone);
            admin.setMemberNo("ADMIN");
            admin.setPassword(passwordEncoder.encode(properties.admin().password()));
            admin.setRole(Role.ADMIN);
            admin.setAiEnabled(true);
            admin.setEnabled(true);
            userRepository.save(admin);
            log.info("已初始化管理员账号：{}", phone);
        };
    }
}
