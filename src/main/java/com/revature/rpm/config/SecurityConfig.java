package com.revature.rpm.config;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.revature.rpm.util.ResourceAccessTokenGenerator;
import com.revature.rpm.web.filters.AuthFilter;
import com.revature.rpm.web.filters.GatewaySubversionFilter;
import com.revature.rpm.web.filters.ResourceAccessFilter;

/**
 * Provides the configuration for the Spring Security framework.
 */
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
	
	private UserDetailsService userDetailsService;
	
	@Autowired
	public SecurityConfig(@Lazy UserDetailsService service) {
		this.userDetailsService = service;
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
				/*
				 * Disables the protection against Cross-Site Request Forgery (CSRF), otherwise
				 * requests cannot be made to this business service from the zuul-service.
				 */
				.csrf().disable()

				/*
				 * Necessary to prevent Spring Security from blocking frames that will be loaded
				 * for the H2 console (will be removed once a external datasource is
				 * implemented).
				 */
				.headers().frameOptions().disable().and()

				/*
				 * Ensure that a stateless session is used; session will not be used to store
				 * user information/state.
				 */
				.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()

				/*
				 * Handle any exceptions thrown during authentication by sending a response
				 * status of unauthorized (401).
				 */
				.exceptionHandling()
				.authenticationEntryPoint((req, rsp, e) -> rsp.sendError(HttpServletResponse.SC_UNAUTHORIZED)).and()
				
				/*
				 * Registering filters:
				 * 
				 * 		- CustomAuthenticationFilter
				 * 		- JwtUsernameAndPasswordAuthenticationFilter
				 * 		- JwtTokenAuthenticationFilter
				 */
	            .addFilterBefore(new GatewaySubversionFilter(gatewayTokenConfig()), AuthFilter.class)
				.addFilter(new AuthFilter(authenticationManager(), resourceAccessTokenGenerator(), resourceAccessTokenConfig()))
				.addFilterAfter(new ResourceAccessFilter(resourceAccessTokenConfig()), AuthFilter.class)
				
				/*
				 * Allows for the access to specific endpoints to be restricted and for others
				 * to be unrestricted
				 */
				.authorizeRequests()

				/*
				 *  Allow unrestricted access to:
				 *  
				 *  	- POST requests to /users - for user registration
				 *  	- GET requests to /users/isAvailable - for username and email availability requests
				 *  	- GET requests to /actuator/info - for AWS Elastic Load Balancer target group monitoring
				 *  	- GET requests to /actuator/routes - for AWS Elastic Load Balancer target group monitoring
				 */
				.mvcMatchers(HttpMethod.POST, "/users").permitAll()
				.mvcMatchers(HttpMethod.GET, "/users/isAvailable").permitAll()
				
				.mvcMatchers(HttpMethod.GET, "/actuator/info").permitAll()
				.mvcMatchers(HttpMethod.GET, "/actuator/routes").permitAll()
				
				/*
				 *  Allow only admins to access:
				 *  	
				 *  	- All requests to /h2-console
				 *  	- GET requests to /auth/users
				 *  	- POST requests to /auth/users/admin
				 */
				.mvcMatchers("/h2-console/**").hasRole("ADMIN")
				.mvcMatchers(HttpMethod.GET, "/users/**").hasRole("ADMIN")
				
				// All other requests must be authenticated
				.anyRequest().authenticated();
		
	}

	@Override
	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
	}

	@Bean
	public BCryptPasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
	
	@Bean ResourceAccessTokenGenerator resourceAccessTokenGenerator() {
		return new ResourceAccessTokenGenerator();
	}
	
	@Bean
	public ResourceAccessTokenConfig resourceAccessTokenConfig() {
		return new ResourceAccessTokenConfig();
	}
	
	@Bean
	public GatewayTokenConfig gatewayTokenConfig() {
		return new GatewayTokenConfig();
	}
	
}