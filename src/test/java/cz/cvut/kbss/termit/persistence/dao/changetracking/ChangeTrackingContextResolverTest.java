package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.environment.config.TestPersistenceAspectsConfig;
import cz.cvut.kbss.termit.environment.config.TestPersistenceConfig;
import cz.cvut.kbss.termit.environment.config.WorkspaceTestConfig;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.util.Collections;
import java.util.Optional;

import static cz.cvut.kbss.termit.environment.config.WorkspaceTestConfig.DEFAULT_CHANGE_TRACKING_CONTEXT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@EnableConfigurationProperties({Configuration.class})
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestPersistenceConfig.class,
                                 TestPersistenceAspectsConfig.class,
                                 WorkspaceTestConfig.class},
                      initializers = {ConfigDataApplicationContextInitializer.class})
class ChangeTrackingContextResolverTest {

    public static final URI CHANGE_TRACKING_CTX = Generator.generateUri();

    private WorkspaceMetadata metadata;

    @Autowired
    private Configuration configuration;

    @Mock
    private WorkspaceMetadataProvider workspaceMetadataProvider;

    @Mock
    private VocabularyDao vocabularyDao;

    private ChangeTrackingContextResolver sut;

    @BeforeEach
    void setUp() {
        final Workspace ws = WorkspaceGenerator.generateWorkspace();
        this.metadata = new WorkspaceMetadata(ws);
        this.sut = new ChangeTrackingContextResolver(workspaceMetadataProvider,vocabularyDao,configuration);
    }

    @Test
    void resolveChangeTrackingContextReturnsWorkspaceVocabularyChangeTrackingContextForVocabulary() {
        when(workspaceMetadataProvider.getCurrentWorkspaceMetadata()).thenReturn(metadata);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        metadata.setVocabularies(Collections.singletonMap(vocabulary.getUri(),
                new VocabularyInfo(vocabulary.getUri(), vocabulary.getUri(), CHANGE_TRACKING_CTX)));
        final URI result = sut.resolveChangeTrackingContext(vocabulary);
        assertNotNull(result);
        assertEquals(CHANGE_TRACKING_CTX, result);
    }

    @Test
    void resolveChangeTrackingContextReturnsWorkspaceVocabularyChangeTrackingContextForTerm() {
        when(workspaceMetadataProvider.getCurrentWorkspaceMetadata()).thenReturn(metadata);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(vocabularyDao.findVocabularyOfGlossary(vocabulary.getGlossary().getUri()))
                .thenReturn(Optional.of(vocabulary));
        metadata.setVocabularies(Collections.singletonMap(vocabulary.getUri(),
                new VocabularyInfo(vocabulary.getUri(), vocabulary.getUri(), CHANGE_TRACKING_CTX)));
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final URI result = sut.resolveChangeTrackingContext(term);
        assertNotNull(result);
        assertEquals(CHANGE_TRACKING_CTX, result);
    }

    @Test
    void resolveChangeTrackingContextThrowsNotFoundExceptionWhenTermVocabularyIsNotFound() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(vocabularyDao.findVocabularyOfGlossary(any())).thenReturn(Optional.empty());
        metadata.setVocabularies(Collections.singletonMap(vocabulary.getUri(),
                new VocabularyInfo(vocabulary.getUri(), vocabulary.getUri(), CHANGE_TRACKING_CTX)));
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        assertThrows(NotFoundException.class, () -> sut.resolveChangeTrackingContext(term));
    }

    @Test
    void resolveChangeTrackingContextReturnsResourceIdentifierWithTrackingExtensionForResource() {
        final Resource resource = Generator.generateResourceWithId();
        final URI result = sut.resolveChangeTrackingContext(resource);
        assertNotNull(result);
        assertEquals(resource.getUri().toString().concat(DEFAULT_CHANGE_TRACKING_CONTEXT), result.toString());
    }
}
