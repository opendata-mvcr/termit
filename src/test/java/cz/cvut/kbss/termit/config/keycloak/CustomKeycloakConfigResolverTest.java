package cz.cvut.kbss.termit.config.keycloak;

import cz.cvut.kbss.termit.exception.ConfigurationException;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.spi.HttpFacade;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomKeycloakConfigResolverTest {

    @Mock
    private Configuration configuration;

    @Mock
    private HttpFacade.Request facade;

    @Test
    void resolvesBasicKeycloakConfigurationFromConfigFile() {
        final CustomKeycloakConfigResolver sut = new CustomKeycloakConfigResolver(configuration);
        final KeycloakDeployment deployment = sut.resolve(facade);
        assertNotNull(deployment);
        assertEquals("termit-test", deployment.getResourceName());
        assertEquals("5c6ffe21-54dd-44dc-8d8b-f20f92891e79", deployment.getResourceCredentials().get("secret"));
    }

    @Test
    void resolvesCustomKeycloakConfigurationFromTermItConfig() {
        final String serverUrl = "http://localhost:9091/keycloak/auth";
        final String realm = "asgard";
        when(configuration.contains(ConfigParam.AUTH_SERVER_URL)).thenReturn(true);
        when(configuration.get(ConfigParam.AUTH_SERVER_URL))
                .thenReturn(serverUrl + CustomKeycloakConfigResolver.REALM_DELIMITER + realm);
        final CustomKeycloakConfigResolver sut = new CustomKeycloakConfigResolver(configuration);
        final KeycloakDeployment deployment = sut.resolve(facade);
        assertEquals(serverUrl, deployment.getAuthServerBaseUrl());
        assertEquals(realm, deployment.getRealm());
    }

    @Test
    void throwsConfigurationExceptionWhenAuthorizationServiceUrlCannotBeParsedForKeycloakConfig() {
        when(configuration.contains(ConfigParam.AUTH_SERVER_URL)).thenReturn(true);
        when(configuration.get(ConfigParam.AUTH_SERVER_URL)).thenReturn("http://localhost:8080/auth");
        final ConfigurationException ex = assertThrows(ConfigurationException.class,
                () -> new CustomKeycloakConfigResolver(configuration));
        assertThat(ex.getMessage(), containsString("not a valid Keycloak URL"));
    }
}
