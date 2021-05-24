package cz.cvut.kbss.termit.persistence.dao.skos;

import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * The tool to import plain SKOS thesauri.
 * <p>
 * It takes the thesauri as a TermIt glossary and 1) creates the necessary metadata (vocabulary, model) 2) generates the
 * necessary hasTopConcept relationships based on the broader/narrower hierarchy.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SKOSImporter {


    @Autowired
    public SKOSImporter() {
    }

    public Vocabulary importVocabulary(String vocabularyIri, String mediaType,
                                       InputStream... inputStreams) {
        throw new UnsupportedOperationException("SKOS import into new vocabulary is not supported by TermIt.");
    }
}
