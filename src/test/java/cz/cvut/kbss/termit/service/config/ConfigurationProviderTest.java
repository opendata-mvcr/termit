package cz.cvut.kbss.termit.service.config;

import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
@EnableConfigurationProperties({Configuration.class})
class ConfigurationProviderTest {

    @Autowired
    private Configuration config;

    private ConfigurationProvider sut;

    @BeforeEach
    void setUp() {
        this.sut = new ConfigurationProvider(config);
    }

    @Test
    void getConfigurationReturnsConfigurationDtoWithRelevantConfigurationValues() {
        final ConfigurationDto result = sut.getConfiguration();
        assertNotNull(result);
        assertEquals(config.getPersistence().getLanguage(), result.getLanguage());
    }
}
