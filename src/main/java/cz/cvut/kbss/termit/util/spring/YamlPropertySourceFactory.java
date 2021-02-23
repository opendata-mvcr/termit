package cz.cvut.kbss.termit.util.spring;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.lang.Nullable;

import java.util.Properties;

/**
 * Spring property source factory for reading YAML configuration.
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(@Nullable String s, EncodedResource encodedResource) {
        assert encodedResource.getResource().getFilename() != null;
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(encodedResource.getResource());

        Properties properties = factory.getObject();
        assert properties != null;

        return new PropertiesPropertySource(encodedResource.getResource().getFilename(), properties);
    }
}
