package cz.cvut.kbss.termit.util.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {YamlPropertySourceFactoryTest.LocalSpringConfiguration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class YamlPropertySourceFactoryTest {

    @Autowired
    private Environment environment;

    @Configuration
    @PropertySource(value = "classpath:components.yaml", factory = YamlPropertySourceFactory.class)
    public static class LocalSpringConfiguration {
    }

    @Test
    void supportsLoadingYamlConfiguration() {
        assertTrue(environment.containsProperty("auth.name"));
        final String authName = environment.getProperty("auth.name");
        assertEquals("auth-service", authName);
        assertTrue(environment.containsProperty("auth.url"));
        final String authUrl = environment.getProperty("auth.url");
        assertEquals("http://localhost:8080/auth/realms/kodi", authUrl);
    }
}