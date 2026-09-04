package com.example.hot6novelcraft.common.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

// 감싸는 클래스 없애고 바로 @TestConfiguration으로
@TestConfiguration
@EnableMethodSecurity
public class TestSecurityConfig { // ← 파일명도 TestSecurityConfig.java로 변경
}