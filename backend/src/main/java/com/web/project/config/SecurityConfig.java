package com.web.project.config;

import com.web.project.config.security.RestAccessDeniedHandler;
import com.web.project.config.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private static final String SYS_USER_LOGOUT_PATH = "/api/sys-user/auth/logout";
    private static final String USER_LOGOUT_PATH = "/api/user/auth/logout";
    /**
     * 配置接口的访问权限。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        DefaultBearerTokenResolver defaultBearerTokenResolver = new DefaultBearerTokenResolver();
        http
                // 资源接口通过 Bearer Token 认证，不使用浏览器自动携带的 Cookie/Session。
                .csrf(AbstractHttpConfigurer::disable)

                .formLogin(AbstractHttpConfigurer::disable)

                .httpBasic(AbstractHttpConfigurer::disable)

                // HTTP 认证不使用 Servlet Session；Access 和 Refresh 都核对 Redis 登录会话。
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(authorize -> authorize

                        // 头像由页面直接以图片资源读取，地址本身不包含敏感信息。
                        .requestMatchers(HttpMethod.GET, "/files/*")
                        .permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/files/*")
                        .permitAll()

                        // 登录、刷新公开；退出接口由控制器独立验证签名以保持幂等。
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/sys-user/auth/login",
                                "/api/sys-user/auth/refresh",
                                SYS_USER_LOGOUT_PATH
                        )
                        .permitAll()

                        // 普通用户注册、登录、刷新、发送验证码和重置密码接口公开访问
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/user/auth/login",
                                "/api/user/auth/refresh",
                                "/api/user/auth/register",
                                "/api/user/auth/send-code",
                                "/api/user/auth/reset-password",
                                USER_LOGOUT_PATH
                        )
                        .permitAll()

                        // 所有管理端接口必须拥有 admin 权限
                        .requestMatchers("/api/sys-user/**")
                        .hasAuthority("SCOPE_admin")

                        // 所有普通用户端接口必须拥有 user 权限
                        .requestMatchers("/api/user/**")
                        .hasAuthority("SCOPE_user")

                        .anyRequest()
                        .authenticated()
                )
                // 授权拒绝与 Bearer 认证失败都返回统一 Result，避免框架默认错误页面。
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(request -> {
                            boolean logoutRequest = HttpMethod.POST.matches(request.getMethod())
                                    && (SYS_USER_LOGOUT_PATH.equals(request.getServletPath())
                                    || USER_LOGOUT_PATH.equals(request.getServletPath()));
                            return logoutRequest ? null : defaultBearerTokenResolver.resolve(request);
                        })
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        return http.build();
    }
}
