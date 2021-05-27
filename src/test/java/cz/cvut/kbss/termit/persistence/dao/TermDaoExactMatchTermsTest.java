package cz.cvut.kbss.termit.persistence.dao;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TermDaoExactMatchTermsTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private WorkspaceMetadataProvider wsMetadataProvider;

    @Autowired
    private TermDao sut;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(Generator.generateUri());
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
        final WorkspaceMetadata wsMetadata = wsMetadataProvider.getCurrentWorkspaceMetadata();
        doReturn(new VocabularyInfo(vocabulary.getUri(), vocabulary.getUri(), vocabulary.getUri())).when(wsMetadata)
            .getVocabularyInfo(
                vocabulary
                    .getUri());
        when(wsMetadata.getVocabularyContexts()).thenReturn(Collections.singleton(vocabulary.getUri()));
        doReturn(Collections.singleton(vocabulary.getUri())).when(wsMetadata).getChangeTrackingContexts();
    }

    @Test
    void findLoadsInferredInverseExactMatchTerms() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.setGlossary(vocabulary.getGlossary().getUri());
        final List<Term> exactMatchTerms = Arrays.asList(Generator.generateTermWithId(Generator.generateUri()), Generator.generateTermWithId(Generator.generateUri()));
        final WorkspaceMetadata wsMetadata = wsMetadataProvider.getCurrentWorkspaceMetadata();
        when(wsMetadata.getVocabularyContexts()).thenReturn(exactMatchTerms.stream().map(AbstractTerm::getVocabulary).collect(Collectors.toSet()));
        exactMatchTerms.forEach(t -> doReturn(new VocabularyInfo(t.getVocabulary(), t.getVocabulary(), t.getVocabulary())).when(wsMetadata).getVocabularyInfo(t.getVocabulary()));
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            exactMatchTerms.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateRelationships(term, exactMatchTerms, SKOS.EXACT_MATCH);
        });

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(exactMatchTerms.size(), result.get().getInverseExactMatchTerms().size());
        exactMatchTerms.forEach(rt -> assertThat(result.get().getInverseExactMatchTerms(), hasItem(new TermInfo(rt))));
    }

    private void generateRelationships(Term term, Collection<Term> related, String relationship) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            conn.begin();
            for (Term r : related) {
                conn.add(vf.createIRI(r.getUri().toString()), vf.createIRI(relationship), vf.createIRI(term.getUri().toString()), vf.createIRI(r.getVocabulary().toString()));
            }
            conn.commit();
        }
    }

    @Test
    void loadingInferredInverseExactMatchExcludesExactMatchAssertedFromSubject() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.setGlossary(vocabulary.getGlossary().getUri());
        final List<Term> exactMatchTerms = Arrays.asList(Generator.generateTermWithId(Generator.generateUri()), Generator.generateTermWithId(Generator.generateUri()));
        final List<Term> inverseExactMatchTerms = new ArrayList<>(Collections.singletonList(Generator.generateTermWithId(Generator.generateUri())));
        inverseExactMatchTerms.addAll(exactMatchTerms);
        final WorkspaceMetadata wsMetadata = wsMetadataProvider.getCurrentWorkspaceMetadata();
        when(wsMetadata.getVocabularyContexts()).thenReturn(inverseExactMatchTerms.stream().map(AbstractTerm::getVocabulary).collect(Collectors.toSet()));
        inverseExactMatchTerms.forEach(t -> doReturn(new VocabularyInfo(t.getVocabulary(), t.getVocabulary(), t.getVocabulary())).when(wsMetadata).getVocabularyInfo(t.getVocabulary()));
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            inverseExactMatchTerms.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t.getVocabulary()));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateRelationships(term, inverseExactMatchTerms, SKOS.EXACT_MATCH);
        });
        term.setExactMatchTerms(exactMatchTerms.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptor(term)));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertFalse(result.get().getInverseExactMatchTerms().isEmpty());
        exactMatchTerms.forEach(r -> assertThat(result.get().getInverseExactMatchTerms(), not(hasItem(new TermInfo(r)))));
    }
}
