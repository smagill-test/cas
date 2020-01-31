package org.apereo.cas.config;

import org.apereo.cas.authentication.AuthenticationServiceSelectionStrategy;
import org.apereo.cas.authentication.AuthenticationServiceSelectionStrategyConfigurer;
import org.apereo.cas.authentication.SecurityTokenServiceClientBuilder;
import org.apereo.cas.authentication.SecurityTokenServiceTokenFetcher;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.ServiceRegistry;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.services.ServicesManagerExecutionPlan;
import org.apereo.cas.services.ServicesManagerExecutionPlanConfigurer;
import org.apereo.cas.ticket.SecurityTokenTicketFactory;
import org.apereo.cas.ticket.registry.TicketRegistry;
import org.apereo.cas.ticket.registry.TicketRegistrySupport;
import org.apereo.cas.util.crypto.CipherExecutor;
import org.apereo.cas.util.http.HttpClient;
import org.apereo.cas.web.cookie.CasCookieBuilder;
import org.apereo.cas.ws.idp.WSFederationConstants;
import org.apereo.cas.ws.idp.authentication.WSFederationAuthenticationServiceSelectionStrategy;
import org.apereo.cas.ws.idp.metadata.WSFederationMetadataController;
import org.apereo.cas.ws.idp.services.DefaultRelyingPartyTokenProducer;
import org.apereo.cas.ws.idp.services.WSFederationRelyingPartyTokenProducer;
import org.apereo.cas.ws.idp.services.WsFederationServicesManager;
import org.apereo.cas.ws.idp.web.WSFederationRequestConfigurationContext;
import org.apereo.cas.ws.idp.web.WSFederationValidateRequestCallbackController;
import org.apereo.cas.ws.idp.web.WSFederationValidateRequestController;

import lombok.val;
import org.jasig.cas.client.validation.AbstractUrlBasedTicketValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * This is {@link CoreWsSecurityIdentityProviderConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Configuration("coreWsSecurityIdentityProviderConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
@ImportResource(locations = "classpath:META-INF/cxf/cxf.xml")
public class CoreWsSecurityIdentityProviderConfiguration {

    @Autowired
    @Qualifier("casClientTicketValidator")
    private ObjectProvider<AbstractUrlBasedTicketValidator> casClientTicketValidator;

    @Autowired
    @Qualifier("ticketGrantingTicketCookieGenerator")
    private ObjectProvider<CasCookieBuilder> ticketGrantingTicketCookieGenerator;

    @Autowired
    @Qualifier("noRedirectHttpClient")
    private ObjectProvider<HttpClient> httpClient;

    @Autowired
    @Qualifier("defaultTicketRegistrySupport")
    private ObjectProvider<TicketRegistrySupport> ticketRegistrySupport;

    @Autowired
    @Qualifier("servicesManager")
    private ObjectProvider<ServicesManager> servicesManager;

    @Autowired
    @Qualifier("wsFedApplicationServiceFactory")
    private ObjectProvider<ServiceFactory> webApplicationServiceFactory;

    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier("securityTokenTicketFactory")
    private ObjectProvider<SecurityTokenTicketFactory> securityTokenTicketFactory;

    @Autowired
    @Qualifier("ticketRegistry")
    private ObjectProvider<TicketRegistry> ticketRegistry;

    @Autowired
    @Qualifier("securityTokenServiceTokenFetcher")
    private ObjectProvider<SecurityTokenServiceTokenFetcher> securityTokenServiceTokenFetcher;

    @Autowired
    @Qualifier("serviceRegistry")
    private ObjectProvider<ServiceRegistry> serviceRegistry;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private Environment environment;


    @ConditionalOnMissingBean(name = "federationValidateRequestController")
    @Bean
    public WSFederationValidateRequestController federationValidateRequestController() {
        return new WSFederationValidateRequestController(getConfigurationContext().build());
    }

    @Autowired
    @Bean
    public WSFederationValidateRequestCallbackController federationValidateRequestCallbackController(
        @Qualifier("wsFederationRelyingPartyTokenProducer") final WSFederationRelyingPartyTokenProducer wsFederationRelyingPartyTokenProducer) {
        val context = getConfigurationContext()
            .relyingPartyTokenProducer(wsFederationRelyingPartyTokenProducer)
            .build();
        return new WSFederationValidateRequestCallbackController(context);
    }

    @Bean
    public Service wsFederationCallbackService() {
        return webApplicationServiceFactory.getObject().createService(WSFederationConstants.ENDPOINT_FEDERATION_REQUEST_CALLBACK);
    }

    @Bean
    @RefreshScope
    public WSFederationMetadataController wsFederationMetadataController() {
        return new WSFederationMetadataController(casProperties);
    }

    @Autowired
    @Bean
    @RefreshScope
    @ConditionalOnMissingBean(name = "wsFederationRelyingPartyTokenProducer")
    public WSFederationRelyingPartyTokenProducer wsFederationRelyingPartyTokenProducer(
        @Qualifier("securityTokenServiceCredentialCipherExecutor") final CipherExecutor securityTokenServiceCredentialCipherExecutor,
        @Qualifier("securityTokenServiceClientBuilder") final SecurityTokenServiceClientBuilder securityTokenServiceClientBuilder) {
        return new DefaultRelyingPartyTokenProducer(securityTokenServiceClientBuilder,
            securityTokenServiceCredentialCipherExecutor,
            new HashSet<>(casProperties.getAuthn().getWsfedIdp().getSts().getCustomClaims()));
    }

    @Bean
    @RefreshScope
    @ConditionalOnMissingBean(name = "wsFederationAuthenticationServiceSelectionStrategy")
    public AuthenticationServiceSelectionStrategy wsFederationAuthenticationServiceSelectionStrategy() {
        return new WSFederationAuthenticationServiceSelectionStrategy(webApplicationServiceFactory.getObject());
    }

    @Bean
    public AuthenticationServiceSelectionStrategyConfigurer wsFederationAuthenticationServiceSelectionStrategyConfigurer() {
        return plan -> plan.registerStrategy(wsFederationAuthenticationServiceSelectionStrategy());
    }

    @Bean(name = "samlServicesManager")
    @RefreshScope
    @ConditionalOnMissingBean(name = "samlServicesManager")
    public ServicesManager samlServicesManager() {
        val activeProfiles = Arrays.stream(environment.getActiveProfiles()).collect(Collectors.toSet());
        return new WsFederationServicesManager(serviceRegistry.getIfAvailable(), eventPublisher, activeProfiles);
    }

    @Bean
    @RefreshScope
    public ServicesManagerExecutionPlanConfigurer samlServicesManagerExecutionPlanConfigurer() {
        return new ServicesManagerExecutionPlanConfigurer() {
            @Override
            public void configureServicesManager(final ServicesManagerExecutionPlan plan) {
                plan.registerServicesManager(samlServicesManager());
            }
        };
    }

    private WSFederationRequestConfigurationContext.WSFederationRequestConfigurationContextBuilder getConfigurationContext() {
        return WSFederationRequestConfigurationContext.builder()
            .servicesManager(servicesManager.getObject())
            .webApplicationServiceFactory(webApplicationServiceFactory.getObject())
            .casProperties(casProperties)
            .ticketValidator(casClientTicketValidator.getObject())
            .securityTokenServiceTokenFetcher(securityTokenServiceTokenFetcher.getObject())
            .serviceSelectionStrategy(wsFederationAuthenticationServiceSelectionStrategy())
            .httpClient(httpClient.getObject())
            .securityTokenTicketFactory(securityTokenTicketFactory.getObject())
            .ticketGrantingTicketCookieGenerator(ticketGrantingTicketCookieGenerator.getObject())
            .ticketRegistry(ticketRegistry.getObject())
            .ticketRegistrySupport(ticketRegistrySupport.getObject())
            .callbackService(wsFederationCallbackService());
    }
}
