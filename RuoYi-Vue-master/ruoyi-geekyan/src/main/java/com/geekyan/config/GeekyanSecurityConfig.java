package com.geekyan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
public class GeekyanSecurityConfig {

    @Bean
    public WebSecurityCustomizer geekyanWebSecurityCustomizer() {
        return web -> web.ignoring()
                .antMatchers("/geekyan/account/visitor-login")
                .antMatchers("/geekyan/account/login")
                .antMatchers("/geekyan/account/register")
                .antMatchers("/geekyan/offline-dict/search")
                .antMatchers("/geekyan/offline-dict/reverse")
                .antMatchers("/geekyan/offline-dict/fuzzy")
                .antMatchers("/geekyan/offline-dict/dicts")
                .antMatchers("/geekyan/word/search")
                .antMatchers("/geekyan/word/translate-phrase")
                .antMatchers("/geekyan/word/offline-search")
                .antMatchers("/geekyan/word/dicts")
                .antMatchers("/geekyan/word/fuzzy")
                .antMatchers("/geekyan/word/reverse")
                .antMatchers("/geekyan/word/mnemonic")
                .antMatchers("/geekyan/word/book")
                .antMatchers("/geekyan/word/history");
    }
}
