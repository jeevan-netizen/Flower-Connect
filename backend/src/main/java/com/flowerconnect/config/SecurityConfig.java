package com.flowerconnect.config;

import com.flowerconnect.config.CorsProperties;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.jwt.JwtAuthenticationFilter;
import com.flowerconnect.security.jwt.JwtService;
import com.flowerconnect.security.jwt.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final CorsProperties corsProperties;
    private final CustomAuthenticationEntryPoint entryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(
            JwtService jwtService,
            UserDetailsServiceImpl userDetailsService,
            PasswordEncoder passwordEncoder,
            CorsProperties corsProperties,
            CustomAuthenticationEntryPoint entryPoint,
            CustomAccessDeniedHandler accessDeniedHandler,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            Clock clock,
            @Autowired(required = false) RateLimitFilter rateLimitFilter) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.corsProperties = corsProperties;
        this.entryPoint = entryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/locations/**").permitAll()
                .requestMatchers("/api/v1/discover/**").permitAll()
                .requestMatchers("/api/v1/search/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs").permitAll()
                // Vendor self-service. Registration is the public entry point that
                // creates the FLORIST account; everything else is vendor-only.
                // This is the *role* boundary only. The stricter *approval* boundary
                // (PENDING_APPROVAL / REJECTED / SUSPENDED vendors are refused) is
                // enforced per handler by @RequiresApprovedVendor, so that the vendor's
                // own profile routes stay reachable while their profile is not approved.
                // See docs/decisions.md (D-13).
                .requestMatchers(HttpMethod.POST, "/api/v1/vendors/register").permitAll()
                // Customer address book (plan task 4.1): the caller's own
                // addresses, resolved from the JWT subject. CUSTOMER-only —
                // a florist or admin account is refused with 403 rather than
                // silently sharing another role's address book.
                .requestMatchers("/api/v1/addresses/**").hasRole("CUSTOMER")
                .requestMatchers("/api/v1/vendors/**").hasRole("FLORIST")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            );
        if (rateLimitFilter != null) {
            http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        }
        http.addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository, objectMapper, clock), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(corsProperties.getOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
