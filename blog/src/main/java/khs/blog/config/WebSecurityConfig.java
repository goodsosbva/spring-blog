//package khs.blog.config;
//
//import khs.blog.service.UserDetailService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.web.SecurityFilterChain;
//
//
//@RequiredArgsConstructor
//@Configuration
//public class WebSecurityConfig {
//
//    private final UserDetailService userService;
//
//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        return http
//                .authorizeHttpRequests(auth -> auth
//                        .requestMatchers("/h2-console/**", "/static/**", "/login", "/signup", "/user", "/api/token").permitAll()
//                        .anyRequest().authenticated()
//                )
//                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // H2 콘솔 iframe용
//                .formLogin(form -> form
//                        .loginPage("/login")
//                        .defaultSuccessUrl("/articles", true)
//                        .permitAll()
//                )
//                .logout(logout -> logout
//                        .logoutSuccessUrl("/login")
//                        .invalidateHttpSession(true)
//                )
//                .csrf(csrf -> csrf.disable())
//                .build();
//    }
//
//
//    @Bean
//    public AuthenticationManager authenticationManager(
//            HttpSecurity http,
//            BCryptPasswordEncoder bCryptPasswordEncoder
//    ) throws Exception {
//
//        AuthenticationManagerBuilder authBuilder =
//                http.getSharedObject(AuthenticationManagerBuilder.class);
//
//        authBuilder
//                .userDetailsService(userService)
//                .passwordEncoder(bCryptPasswordEncoder);
//
//        return authBuilder.build();
//    }
//
//
//
//    @Bean
//    public BCryptPasswordEncoder bCryptPasswordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
//
//}
