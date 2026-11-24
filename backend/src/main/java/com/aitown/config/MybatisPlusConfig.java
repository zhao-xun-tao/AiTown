package com.aitown.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.aitown.mapper")
public class MybatisPlusConfig {
}
