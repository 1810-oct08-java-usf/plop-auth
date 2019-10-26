package com.revature.rpm.web.filters;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.revature.rpm.config.ResourceAccessTokenConfig;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

/**
 * A filter used to intercept all requests and validate the JWT, if present, in
 * the HTTP request header.
 * 
 * @author Wezley Singleton
 */
public class ResourceAccessFilter extends OncePerRequestFilter {

	private String header;
	private String prefix;
	private String secret;
	
	public ResourceAccessFilter(ResourceAccessTokenConfig tokenConfig) {
		this.header = tokenConfig.getAccessHeader();
		this.prefix = tokenConfig.getAccessPrefix();
		this.secret = tokenConfig.getAccessSecret();
	}

	/**
	 * Performs the JWT validation. If no Authorization header is present, the
	 * request is passed along to the next filter in the chain (in case of requests
	 * to unrestricted endpoints). The token is valid only if it has the proper
	 * prefix, a proper principal, and is unexpired.
	 *
	 * @param req
	 *            Provides information regarding the HTTP request.
	 *
	 * @param resp
	 *            Provides information regarding the HTTP response.
	 *
	 * @param chain
	 *            Used to pass the HTTP request and response objects to the next
	 *            filter in the chain.
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws ServletException, IOException {
		
		logger.info("Request intercepted by ResourceAccessFilter");
	
		String headerValue = req.getHeader(header);

		if (headerValue == null || !headerValue.startsWith(prefix)) {
			logger.info("No resource access header value found on request");
			chain.doFilter(req, resp);
			return;
		}

		logger.info("Resource access header detected, obtaining token value");
		String token = headerValue.replaceAll(prefix, "");

		try {

			logger.info("Parsing token for resource access claims");
			Claims claims = Jwts.parser()
					.setSigningKey(secret.getBytes())
					.parseClaimsJws(token)
					.getBody();

			String username = claims.getSubject();

			if (username != null) {
				
				@SuppressWarnings("unchecked")
				List<String> authoritiesClaim = (List<String>) claims.get("authorities");
				
				List<SimpleGrantedAuthority> grantedAuthorities = authoritiesClaim.stream()
																				  .map(SimpleGrantedAuthority::new)
																				  .collect(Collectors.toList());
				
				logger.info("Resource access scopes determined, setting security context");
				UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, null, grantedAuthorities);
				SecurityContextHolder.getContext().setAuthentication(auth);
			}

		} catch (Exception e) {
			
			logger.warn("Error parsing resource access token for claim information");
			SecurityContextHolder.clearContext();
			
		}

		chain.doFilter(req, resp);
	}

}