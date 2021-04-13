package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.model.descriptors.FieldDescriptor;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermDto;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

public class TermDaoWorkspacesTest extends BaseDaoTestRunner {

    private static final String LABEL_IN_DIFFERENT_WORKSPACE = "Different label";

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private WorkspaceMetadataProvider wsMetadataCache;

    @Autowired
    private Configuration config;

    @Autowired
    private TermDao sut;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabularyWithId();
        saveVocabulary(vocabulary);
    }

    private void saveVocabulary(Vocabulary vocabulary) {
        final WorkspaceMetadata wsMetadata = wsMetadataCache.getCurrentWorkspaceMetadata();
        doReturn(new VocabularyInfo(vocabulary.getUri(), vocabulary.getUri(), vocabulary.getUri())).when(wsMetadata)
                .getVocabularyInfo(vocabulary.getUri());
        doReturn(Collections.singleton(vocabulary.getUri())).when(wsMetadata).getVocabularyContexts();
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            try (final RepositoryConnection conn = em.unwrap(Repository.class).getConnection()) {
                conn.begin();
                conn.add(Generator.generateWorkspaceReferences(Collections.singleton(vocabulary), wsMetadata.getWorkspace()));
                conn.commit();
            }
        });
    }

    @Test
    void findAllReturnsTermsFromVocabularyInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final List<Term> result = sut.findAll(vocabulary);
        assertEquals(1, result.size());
        assertEquals(term.getLabel(), result.get(0).getLabel());
    }

    private URI addTermToVocabularyInAnotherWorkspace(Term term) {
        final Vocabulary anotherWorkspaceVocabulary = Generator.generateVocabulary();
        anotherWorkspaceVocabulary.setUri(vocabulary.getUri());
        final URI anotherWorkspaceCtx = Generator.generateUri();
        final Term copy = new Term();
        copy.setUri(term.getUri());
        copy.setLabel(MultilingualString.create(LABEL_IN_DIFFERENT_WORKSPACE, Constants.DEFAULT_LANGUAGE));

        transactional(() -> {
            em.persist(anotherWorkspaceVocabulary, new EntityDescriptor(anotherWorkspaceCtx));
            copy.setGlossary(term.getGlossary());
            final EntityDescriptor termDescriptor = new EntityDescriptor(anotherWorkspaceCtx);
            termDescriptor.addAttributeContext(descriptorFactory.fieldSpec(Term.class, "parentTerms"), null);
            termDescriptor.addAttributeDescriptor(descriptorFactory.fieldSpec(Term.class, "vocabulary"),
                    new FieldDescriptor((URI) null, descriptorFactory.fieldSpec(Term.class, "vocabulary")));
            em.persist(copy, termDescriptor);
        });
        return anotherWorkspaceCtx;
    }

    @Test
    void findAllRootsReturnsTermsFromVocabularyInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        assertEquals(term.getLabel(), result.get(0).getLabel());
    }

    @Test
    void subTermLoadingRetrievesSubTermsDeclaredInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term child = Generator.generateTermWithId();
        child.addParentTerm(term);
        child.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(child, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(child, vocabulary.getUri(), em);
            insertNarrowerStatements(child,
                    wsMetadataCache.getCurrentWorkspaceMetadata().getVocabularyInfo(vocabulary.getUri()).getContext());
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        final TermDto resultParent = result.get(0);
        assertEquals(1, resultParent.getSubTerms().size());
        assertEquals(child.getUri(), resultParent.getSubTerms().iterator().next().getUri());
    }

    /**
     * Simulate the inverse of skos:broader and skos:narrower
     *
     * @param child Term whose parents need skos:narrower relationships to them
     */
    private void insertNarrowerStatements(Term child, URI context) {
        final Repository repo = em.unwrap(Repository.class);
        final ValueFactory vf = repo.getValueFactory();
        try (final RepositoryConnection conn = repo.getConnection()) {
            conn.begin();
            final IRI narrower = vf.createIRI(SKOS.NARROWER);
            for (Term parent : child.getParentTerms()) {
                conn.add(vf.createStatement(vf.createIRI(parent.getUri().toString()), narrower,
                        vf.createIRI(child.getUri().toString()), vf.createIRI(context.toString())));
            }
            conn.commit();
        }
    }

    @Test
    void subTermLoadingDoesNotRetrieveSubTermsDeclaredInDifferentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term child = Generator.generateTermWithId();
        child.addParentTerm(term);
        child.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        final URI ctx = addTermToVocabularyInAnotherWorkspace(term);
        transactional(() -> {
            final EntityDescriptor termDescriptor = new EntityDescriptor(ctx);
            termDescriptor.addAttributeContext(descriptorFactory.fieldSpec(Term.class, "parentTerms"), null);
            termDescriptor.addAttributeDescriptor(descriptorFactory.fieldSpec(Term.class, "vocabulary"),
                    new FieldDescriptor((URI) null, descriptorFactory.fieldSpec(Term.class, "vocabulary")));
            em.persist(child, termDescriptor);
            insertNarrowerStatements(child, ctx);
            Generator.addTermInVocabularyRelationship(child, vocabulary.getUri(), em);
        });

        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        final TermDto resultParent = result.get(0);
        assertThat(resultParent.getSubTerms(), anyOf(nullValue(), empty()));
    }

    @Test
    void findRetrievesTermInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(term.getLabel(), result.get().getLabel());
    }

    @Test
    void findAllBySearchStringRetrievesTermsInCurrentVocabulary() {
        final Term term = Generator.generateTermWithId();
        term.getLabel().set(Constants.DEFAULT_LANGUAGE, "searched label");
        term.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final List<TermDto> result = sut.findAll("searched", vocabulary);
        assertEquals(1, result.size());
        assertEquals(term.getLabel(), result.get(0).getLabel());
    }

    @Test
    void findTermHandlesParentTermWhichExistsInTwoWorkspacesWithDifferentLabels() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabulary.getGlossary().getUri());
        term.addParentTerm(parent);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            vocabulary.getGlossary().addRootTerm(parent);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(parent, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(parent);

        em.getEntityManagerFactory().getCache().evictAll();
        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertThat(result.get().getParentTerms(), hasItem(parent));
        assertEquals(parent.getLabel(), result.get().getParentTerms().iterator().next().getLabel());
    }

    @Test
    void existsInVocabularyReturnsFalseWhenLabelExistsInDifferentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        assertFalse(sut.existsInVocabulary(LABEL_IN_DIFFERENT_WORKSPACE, vocabulary, Constants.DEFAULT_LANGUAGE));
    }

    @Test
    void getReferenceUsesWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        transactional(() -> {
            final Optional<Term> t = sut.getReference(term.getUri());
            assertTrue(t.isPresent());
            assertEquals(term.getLabel(), t.get().getLabel());
        });
    }

    @Test
    void findAllRootsInWorkspaceRetrievesRootTermsInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabulary.getGlossary().getUri());
        term.addParentTerm(parent);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            vocabulary.getGlossary().addRootTerm(parent);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(parent, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(parent);

        final List<TermDto> result = sut.findAllRoots(Constants.DEFAULT_PAGE_SPEC);
        assertEquals(Collections.singletonList(new TermDto(parent)), result);
    }

    @Test
    void findAllInWorkspaceRetrievesAllTermsInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabulary.getGlossary().getUri());
        term.addParentTerm(parent);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            vocabulary.getGlossary().addRootTerm(parent);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(parent, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(parent);

        final List<TermDto> result = sut.findAll(Constants.DEFAULT_PAGE_SPEC);
        assertEquals(2, result.size());
        assertThat(result, hasItems(new TermDto(parent), new TermDto(term)));
    }

    @Test
    void findAllBySearchStringRetrievesMatchingTermsInCurrentWorkspace() {
        final String searchString = "match";
        final Term term = Generator.generateTermWithId();
        term.getLabel().set(Constants.DEFAULT_LANGUAGE, "matching label");
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabulary.getGlossary().getUri());
        term.addParentTerm(parent);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            vocabulary.getGlossary().addRootTerm(parent);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(parent, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final List<TermDto> result = sut.findAll(searchString);
        assertEquals(Collections.singletonList(new TermDto(term)), result);
    }

    @Test
    void findAllBySearchStringRetrievesMatchingTermsInCurrentWorkspaceAndCanonicalContainer() {
        final String searchString = "match";
        final Term term = Generator.generateTermWithId();
        term.getLabel().set(Constants.DEFAULT_LANGUAGE, "matching label");
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term canonical = Generator.generateTermWithId();
        canonical.getLabel().set(Constants.DEFAULT_LANGUAGE, "matching label as well");
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        persistTermIntoCanonicalContainer(canonical);
        addTermToVocabularyInAnotherWorkspace(term);
        final List<TermDto> result = sut.findAll(searchString);
        assertEquals(2, result.size());
        assertEquals(Arrays.asList(new TermDto(term), new TermDto(canonical)), result);
    }

    @Test
    void persistSupportsTermWithParentInCanonicalContainer() {
        final Term term = Generator.generateTermWithId();
        final Term parent = Generator.generateTermWithId();
        term.addParentTerm(parent);
        persistTermIntoCanonicalContainer(parent);

        transactional(() -> sut.persist(term, vocabulary));
        final Term result = em.find(Term.class, term.getUri());
        assertEquals(term, result);
    }

    private URI persistTermIntoCanonicalContainer(Term term) {
        final Collection<Statement> canonical = WorkspaceGenerator
                .generateCanonicalCacheContainer(config.get(ConfigParam.CANONICAL_CACHE_CONTAINER_IRI));
        final List<String> ids = canonical.stream().map(s -> s.getObject().stringValue()).sorted().collect(Collectors.toList());
        final URI selectedVocabulary = URI.create(ids.get(0));
        transactional(() -> {
            em.persist(term, new EntityDescriptor(selectedVocabulary));
            Generator.addTermInVocabularyRelationship(term, selectedVocabulary, em);
            final URI glossary = Generator.generateUri();
            term.setGlossary(glossary);
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                final ValueFactory vf = conn.getValueFactory();
                conn.begin();
                conn.add(canonical);
                conn.add(vf.createIRI(selectedVocabulary.toString()),
                        vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_glosar),
                        vf.createIRI(glossary.toString()), vf.createIRI(selectedVocabulary.toString()));
                conn.add(vf.createIRI(glossary.toString()), vf.createIRI(SKOS.HAS_TOP_CONCEPT),
                        vf.createIRI(term.getUri().toString()), vf.createIRI(selectedVocabulary.toString()));
                conn.commit();
            }
        });
        return selectedVocabulary;
    }

    @Test
    void findSupportsTermWithParentInCanonicalContainer() {
        final Term term = Generator.generateTermWithId();
        final Term parent = Generator.generateTermWithId();
        term.addParentTerm(parent);
        term.setGlossary(vocabulary.getGlossary().getUri());
        persistTermIntoCanonicalContainer(parent);
        final EntityDescriptor termDescriptor = new EntityDescriptor(vocabulary.getUri());
        termDescriptor.addAttributeContext(em.getMetamodel().entity(Term.class).getAttribute("parentTerms"), null);
        transactional(() -> {
            em.persist(term, termDescriptor);
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        em.getEntityManagerFactory().getCache().evictAll();

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(term, result.get());
        assertThat(result.get().getParentTerms(), hasItem(parent));
    }

    @Test
    void updateSupportsTermWithParentInCanonicalContainer() {
        final Term term = Generator.generateTermWithId();
        final Term parent = Generator.generateTermWithId();
        persistTermIntoCanonicalContainer(parent);
        final EntityDescriptor termDescriptor = new EntityDescriptor(vocabulary.getUri());
        termDescriptor.addAttributeContext(em.getMetamodel().entity(Term.class).getAttribute("parentTerms"), null);
        transactional(() -> {
            em.persist(term, termDescriptor);
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });

        term.addParentTerm(parent);
        // This is normally inferred
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> sut.update(term));
        final Term result = em.find(Term.class, term.getUri());
        assertEquals(term, result);
        assertEquals(term, result);
        assertThat(result.getParentTerms(), hasItem(parent));
    }

    @Test
    void findSupportsTermWithParentInCanonicalContainerAndWorkingVersionInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term parent = Generator.generateTermWithId();
        final Term parentCopy = new Term();
        parentCopy.setUri(parent.getUri());
        parentCopy.setLabel(MultilingualString.create("different parent label", Constants.DEFAULT_LANGUAGE));
        parentCopy.setDefinition(parent.getDefinition());
        parentCopy.setDescription(parent.getDescription());
        term.addParentTerm(parentCopy);
        final URI canonicalVocabularyUri = persistTermIntoCanonicalContainer(parent);
        final Vocabulary wsVocabulary = Generator.generateVocabularyWithId();
        saveVocabulary(wsVocabulary);
        transactional(() -> {
            final EntityDescriptor parentDescriptor = new EntityDescriptor(wsVocabulary.getUri());
            parentDescriptor
                    .addAttributeContext(em.getMetamodel().entity(Term.class).getAttribute("parentTerms"), null);
            parentCopy.setGlossary(wsVocabulary.getGlossary().getUri());
            em.persist(parentCopy, parentDescriptor);
            connectWorkspaceVocabularyWithCanonicalOne(wsVocabulary.getUri(), canonicalVocabularyUri);
            Generator.addTermInVocabularyRelationship(parentCopy, canonicalVocabularyUri, em);
            final EntityDescriptor termDescriptor = new EntityDescriptor(vocabulary.getUri());
            termDescriptor.addAttributeContext(em.getMetamodel().entity(Term.class).getAttribute("parentTerms"), null);
            em.persist(term, termDescriptor);
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertNotNull(result.get().getParentTerms());
        assertEquals(1, result.get().getParentTerms().size());
        assertThat(result.get().getParentTerms(), hasItem(parentCopy));
        assertEquals(parentCopy.getLabel(), result.get().getParentTerms().iterator().next().getLabel());
    }

    private void connectWorkspaceVocabularyWithCanonicalOne(URI wsVocabulary, URI canonicalVocabulary) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            conn.add(vf.createIRI(wsVocabulary.toString()),
                    vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_vychazi_z_verze),
                    vf.createIRI(canonicalVocabulary.toString()), vf.createIRI(wsVocabulary.toString()));
        }
    }

    @Test
    void findAllRootsRetrievesRootTermsFromCurrentWorkspaceAndCanonicalContainer() {
        final Term term = Generator.generateTermWithId();
        final Term canonical = Generator.generateTermWithId();
        persistTermIntoCanonicalContainer(canonical);
        final EntityDescriptor termDescriptor = new EntityDescriptor(vocabulary.getUri());
        transactional(() -> {
            em.persist(term, termDescriptor);
            vocabulary.getGlossary().addRootTerm(term);
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });

        final List<TermDto> result = sut.findAllRoots(Constants.DEFAULT_PAGE_SPEC);
        assertEquals(2, result.size());
        assertThat(result, hasItems(new TermDto(term), new TermDto(canonical)));
    }

    @Test
    void findAllRetrievesTermsFromCurrentWorkspaceAndCanonicalContainer() {
        final Term term = Generator.generateTermWithId();
        final Term parent = Generator.generateTermWithId();
        term.addParentTerm(parent);
        persistTermIntoCanonicalContainer(parent);
        final EntityDescriptor termDescriptor = new EntityDescriptor(vocabulary.getUri());
        termDescriptor.addAttributeContext(em.getMetamodel().entity(Term.class).getAttribute("parentTerms"), null);
        transactional(() -> {
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(term, termDescriptor);
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });

        final List<TermDto> result = sut.findAll(Constants.DEFAULT_PAGE_SPEC);
        assertEquals(2, result.size());
        assertThat(result, hasItems(new TermDto(term), new TermDto(parent)));
        final Optional<TermDto> termResult = result.stream().filter(t -> t.getUri().equals(term.getUri())).findFirst();
        assert termResult.isPresent();
        assertThat(termResult.get().getParentTerms(), hasItem(new TermDto(parent)));
    }

    @Test
    void findAllBySearchStringRetrievesMatchingTermsFromCurrentWorkspaceAndCanonicalContainer() {
        final String searchString = "search";
        final Term term = Generator.generateTermWithId();
        term.getLabel().set(Constants.DEFAULT_LANGUAGE, searchString + " string label");
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term canonical = Generator.generateTermWithId();
        canonical.getLabel().set(Constants.DEFAULT_LANGUAGE, searchString + " canonical label");
        persistTermIntoCanonicalContainer(canonical);
        final Term anotherCanonical = Generator.generateTermWithId();
        persistTermIntoCanonicalContainer(anotherCanonical);
        final EntityDescriptor termDescriptor = new EntityDescriptor(vocabulary.getUri());
        transactional(() -> {
            em.persist(term, termDescriptor);
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });

        final List<TermDto> result = sut.findAll(searchString);
        assertEquals(2, result.size());
        assertThat(result, hasItems(new TermDto(term), new TermDto(canonical)));
    }

    @Test
    void findAllRootsLoadsSubTermsInCanonicalContainer() {
        final Term canonical = Generator.generateTermWithId();
        final URI canonicalVocUri = persistTermIntoCanonicalContainer(canonical);
        final Term canonicalChild = Generator.generateTermWithId();
        canonicalChild.addParentTerm(canonical);
        transactional(() -> {
            final EntityDescriptor termDescriptor = new EntityDescriptor(canonicalVocUri);
            em.persist(canonicalChild, termDescriptor);
            Generator.addTermInVocabularyRelationship(canonicalChild, canonicalVocUri, em);
        });

        final List<TermDto> result = sut.findAllRoots(Constants.DEFAULT_PAGE_SPEC);
        assertEquals(1, result.size());
        assertThat(result.get(0).getSubTerms(), hasItem(new TermInfo(canonicalChild)));
    }

    @Test
    void findAllSubTermsLoadsSubTermsOfTermInCanonicalContainer() {
        final Term canonical = Generator.generateTermWithId();
        final URI canonicalVocUri = persistTermIntoCanonicalContainer(canonical);
        canonical.setVocabulary(canonicalVocUri);
        final Term canonicalChild = Generator.generateTermWithId();
        canonicalChild.addParentTerm(canonical);
        canonicalChild.setGlossary(canonical.getGlossary());
        transactional(() -> {
            final EntityDescriptor termDescriptor = new EntityDescriptor(canonicalVocUri);
            em.persist(canonicalChild, termDescriptor);
            Generator.addTermInVocabularyRelationship(canonicalChild, canonicalVocUri, em);
        });

        final List<Term> result = sut.findAllSubTerms(canonical);
        assertEquals(Collections.singletonList(canonicalChild), result);
    }

    @Test
    void termLoadResolvesPublishedStatus() {
        final Term publishedTerm = Generator.generateTermWithId();
        publishedTerm.setGlossary(vocabulary.getGlossary().getUri());
        final Term notPublishedTerm = Generator.generateTermWithId();
        notPublishedTerm.setGlossary(vocabulary.getGlossary().getUri());
        // This will simulate the canonical version of the current vocabulary
        final Vocabulary canonical = Generator.generateVocabularyWithId();
        canonical.getGlossary().setUri(vocabulary.getGlossary().getUri());
        transactional(() -> {
            canonical.getGlossary().addRootTerm(publishedTerm);
            em.persist(canonical, new EntityDescriptor(canonical.getUri()));
            em.persist(publishedTerm, new EntityDescriptor(canonical.getUri()));
        });
        transactional(() -> {
            final EntityDescriptor termDescriptor = new EntityDescriptor(vocabulary.getUri());
            em.persist(publishedTerm, termDescriptor);
            em.persist(notPublishedTerm, termDescriptor);
            vocabulary.getGlossary().addRootTerm(publishedTerm);
            vocabulary.getGlossary().addRootTerm(notPublishedTerm);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(publishedTerm, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(notPublishedTerm, vocabulary.getUri(), em);
        });

        final Optional<Term> publishedResult = sut.find(publishedTerm.getUri());
        assertTrue(publishedResult.isPresent());
        assertTrue(publishedResult.get().isPublished());
        final Optional<Term> notPublishedResult = sut.find(notPublishedTerm.getUri());
        assertTrue(notPublishedResult.isPresent());
        assertFalse(notPublishedResult.get().isPublished());
    }

    @Test
    void removeHandlesWorkspacesAndCanonicalContainer() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Collection<Statement> canonical = WorkspaceGenerator
                .generateCanonicalCacheContainer(config.get(ConfigParam.CANONICAL_CACHE_CONTAINER_IRI));
        transactional(() -> {
            em.persist(term, new EntityDescriptor(vocabulary.getUri()));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                conn.add(canonical);
            }
        });

        term.setVocabulary(vocabulary.getUri());    // This is normally inferred
        transactional(() -> sut.remove(term));
        assertFalse(sut.exists(term.getUri()));
    }
}
