package org.apereo.cas.config;

import org.apereo.cas.authentication.AuthenticationServiceSelectionStrategy;
import org.apereo.cas.authentication.AuthenticationServiceSelectionStrategyConfigurer;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.support.saml.authentication.principal.SamlIdpApplicationServiceFactory;
import org.apereo.cas.support.saml.services.SamlIdPEntityIdAuthenticationServiceSelectionStrategy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This is {@link SamlIdPAuthenticationServiceSelectionStrategyConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Configuration("samlIdPAuthenticationServiceSelectionStrategyConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
public class SamlIdPAuthenticationServiceSelectionStrategyConfiguration {

    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier("samlIdpApplicationServiceFactory")
    private ObjectProvider<SamlIdpApplicationServiceFactory> samlIdpApplicationServiceFactory;

    @ConditionalOnMissingBean(name = "samlIdPEntityIdValidationServiceSelectionStrategy")
    @Bean
    public AuthenticationServiceSelectionStrategy samlIdPEntityIdValidationServiceSelectionStrategy() {
        return new SamlIdPEntityIdAuthenticationServiceSelectionStrategy(samlIdpApplicationServiceFactory.getIfAvailable(),
            casProperties.getServer().getPrefix());
    }

    @Bean
    public AuthenticationServiceSelectionStrategyConfigurer samlIdPAuthenticationServiceSelectionStrategyConfigurer() {
        return plan -> plan.registerStrategy(samlIdPEntityIdValidationServiceSelectionStrategy());
    }
}
