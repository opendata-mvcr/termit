package cz.cvut.kbss.termit.service.config;

import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationProviderTest {

    @Mock
    private Configuration config;

    @InjectMocks
    private ConfigurationProvider sut;

    @Test
    void getConfigurationReturnsConfigurationDtoWithRelevantConfigurationValues() {
        final String lang = "cs";
        when(config.get(ConfigParam.LANGUAGE)).thenReturn(lang);
        final ConfigurationDto result = sut.getConfiguration();
        assertNotNull(result);
        assertEquals(lang, result.getLanguage());
        verify(config).get(ConfigParam.LANGUAGE);
    }
}
