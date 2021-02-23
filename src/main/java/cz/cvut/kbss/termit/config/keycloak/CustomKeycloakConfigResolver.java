package cz.cvut.kbss.termit.config.keycloak;

import cz.cvut.kbss.termit.exception.ConfigurationException;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class CustomKeycloakConfigResolver implements KeycloakConfigResolver {

    private static final Logger LOG = LoggerFactory.getLogger(CustomKeycloakConfigResolver.class);

    private static final String KEYCLOAK_CONFIG_FILE = "keycloak.json";

    // Package-private for testing purposes
    static final String REALM_DELIMITER = "/realms/";

    private final KeycloakDeployment deployment;

    public CustomKeycloakConfigResolver(Configuration config) {
        this.deployment = configureKeycloakDeployment(config);
    }

    private KeycloakDeployment configureKeycloakDeployment(Configuration config) {
        LOG.trace("Loading basic Keycloak configuration from {}.", KEYCLOAK_CONFIG_FILE);
        final AdapterConfig adapterConfig = KeycloakDeploymentBuilder.loadAdapterConfig(getBasicKeycloakConfig());
        if (config.contains(ConfigParam.AUTH_SERVER_URL)) {
            final String fullUrl = config.get(ConfigParam.AUTH_SERVER_URL);
            LOG.trace("Extending Keycloak configuration with auth server URL {}.", fullUrl);
            // {SERVER_URL}/realms/{REALM_NAME}
            final String[] parts = fullUrl.split(REALM_DELIMITER);
            if (parts.length != 2) {
                throw new ConfigurationException(
                        "Authorization service URL is not a valid Keycloak URL. Expected a pattern {SERVER_URL}/realms/{REALM_NAME}.");
            }
            adapterConfig.setAuthServerUrl(parts[0]);
            adapterConfig.setRealm(parts[1]);
        }
        return KeycloakDeploymentBuilder.build(adapterConfig);
    }

    private InputStream getBasicKeycloakConfig() {
        final InputStream is = CustomKeycloakConfigResolver.class.getClassLoader()
                                                                 .getResourceAsStream(KEYCLOAK_CONFIG_FILE);
        if (is == null) {
            throw new ConfigurationException(
                    "Unable to load basic Keycloak configuration. Keycloak configuration file " + KEYCLOAK_CONFIG_FILE +
                            " not found.");
        }
        return is;
    }

    @Override
    public KeycloakDeployment resolve(HttpFacade.Request facade) {
        return deployment;
    }
}
