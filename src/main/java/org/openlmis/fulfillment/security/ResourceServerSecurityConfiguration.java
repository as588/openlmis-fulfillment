package org.openlmis.fulfillment.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.authentication.BearerTokenExtractor;
import org.springframework.security.oauth2.provider.authentication.TokenExtractor;
import org.springframework.security.oauth2.provider.token.AccessTokenConverter;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;
import org.springframework.security.oauth2.provider.token.UserAuthenticationConverter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@Import({ MethodSecurityConfiguration.class })
@EnableResourceServer
public class ResourceServerSecurityConfiguration implements ResourceServerConfigurer {

  private TokenExtractor tokenExtractor = new BearerTokenExtractor();

  @Value("${auth.resourceId}")
  private String resourceId;

  @Override
  public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
    resources.resourceId(resourceId);
  }

  @Override
  public void configure(HttpSecurity http) throws Exception {
    http.addFilterAfter(new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(HttpServletRequest request,
                                      HttpServletResponse response, FilterChain filterChain)
          throws ServletException, IOException {
        // We don't want to allow access to a resource with no token so clear
        // the security context in case it is actually an OAuth2Authentication
        if (tokenExtractor.extract(request) == null) {
          SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
      }
    }, AbstractPreAuthenticatedProcessingFilter.class);
    http.csrf().disable();

    http
        .authorizeRequests()
        .antMatchers(
            "/",
            "/webjars/**",
            "/docs/**"
        ).permitAll()
        .antMatchers("/**").fullyAuthenticated();
  }

  /**
   * AccessTokenConverter bean initializer that utilizes custom UserTokenConverter.
   * @return token converter
   */
  @Bean
  public AccessTokenConverter accessTokenConverter() {
    DefaultAccessTokenConverter accessTokenConverter = new DefaultAccessTokenConverter();
    accessTokenConverter.setUserTokenConverter(userAuthenticationConverter());
    return accessTokenConverter;
  }

  @Bean
  public UserAuthenticationConverter userAuthenticationConverter() {
    return new UserTokenConverter();
  }

  /**
   * RemoteTokenServices bean initializer.
   * @param checkTokenUrl url to check tokens against
   * @param clientId client's id
   * @param clientSecret client's secret
   * @return token services
   */
  @Bean
  @Autowired
  public RemoteTokenServices remoteTokenServices(@Value("${auth.server.url}") String checkTokenUrl,
                                                 @Value("${auth.server.clientId}") String clientId,
                                                 @Value("${auth.server.clientSecret}")
                                                     String clientSecret) {
    final RemoteTokenServices remoteTokenServices = new RemoteTokenServices();
    remoteTokenServices.setCheckTokenEndpointUrl(checkTokenUrl);
    remoteTokenServices.setClientId(clientId);
    remoteTokenServices.setClientSecret(clientSecret);
    remoteTokenServices.setAccessTokenConverter(accessTokenConverter());
    return remoteTokenServices;
  }
}
