package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class TermDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private WorkspaceMetadataProvider wsMetadataProvider;

    @Autowired
    private TermDao sut;

    @Autowired
    private Configuration configuration;

    private Vocabulary vocabulary;

    private final Map<URI, URI> glossaryToVocabulary = new HashMap<>();

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(Generator.generateUri());
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
        glossaryToVocabulary.put(vocabulary.getGlossary().getUri(), vocabulary.getUri());

        final WorkspaceMetadata wsMetadata = wsMetadataProvider.getCurrentWorkspaceMetadata();
        doReturn(new VocabularyInfo(vocabulary.getUri(), vocabulary.getUri(), vocabulary.getUri())).when(wsMetadata)
                .getVocabularyInfo(
                        vocabulary
                                .getUri());
        when(wsMetadata.getVocabularyContexts()).thenReturn(Collections.singleton(vocabulary.getUri()));
        doReturn(Collections.singleton(vocabulary.getUri())).when(wsMetadata).getChangeTrackingContexts();
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            try (final RepositoryConnection conn = em.unwrap(Repository.class).getConnection()) {
                conn.begin();
                conn.add(Generator
                        .generateWorkspaceReferences(Collections.singleton(vocabulary),
                                wsMetadataProvider.getCurrentWorkspace()));
                conn.commit();
            }
        });
    }

    @Test
    void findAllRootsWithDefaultPageSpecReturnsAllTerms() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(toDtos(terms), result);
    }

    private static List<TermDto> toDtos(List<Term> terms) {
        return Environment.termsToDtos(terms);
    }

    private void addTermsAndSave(Collection<Term> terms, Vocabulary vocabulary) {
        vocabulary.getGlossary().setRootTerms(terms.stream().map(Asset::getUri).collect(Collectors.toSet()));
        transactional(() -> {
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            terms.forEach(t -> {
                t.setGlossary(vocabulary.getGlossary().getUri());
                em.persist(t, descriptorFactory.termDescriptor(vocabulary));
                addTermInVocabularyRelationship(t, vocabulary.getUri());
            });
        });
    }

    private List<Term> generateTerms(int count) {
        return IntStream.range(0, count).mapToObj(i -> Generator.generateTermWithId())
                        .sorted(Comparator.comparing((Term t) -> t.getLabel().get(Environment.LANGUAGE)))
                        .collect(Collectors.toList());
    }

    private void addTermInVocabularyRelationship(Term term, URI vocabularyIri) {
        Generator.addTermInVocabularyRelationship(term, vocabularyIri, em);
    }

    @Test
    void findAllRootsReturnsMatchingPageWithTerms() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        // Paging starts at 0
        final List<TermDto> result = sut
                .findAllRoots(vocabulary, PageRequest.of(1, terms.size() / 2), Collections.emptyList());
        final List<Term> subList = terms.subList(terms.size() / 2, terms.size());
        assertEquals(toDtos(subList), result);
    }

    @Test
    void findAllRootsReturnsOnlyTermsInSpecifiedVocabulary() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);
        final Vocabulary another = Generator.generateVocabulary();
        another.setUri(Generator.generateUri());
        another.getGlossary().setRootTerms(generateTerms(4).stream().map(Asset::getUri).collect(Collectors.toSet()));
        transactional(() -> em.persist(another));

        final List<TermDto> result = sut
                .findAllRoots(vocabulary, PageRequest.of(0, terms.size() / 2), Collections.emptyList());
        assertEquals(terms.size() / 2, result.size());
        assertTrue(toDtos(terms).containsAll(result));
    }

    @Test
    void findAllRootsReturnsOnlyRootTerms() {
        final List<Term> rootTerms = generateTerms(10);
        addTermsAndSave(new HashSet<>(rootTerms), vocabulary);
        transactional(() -> rootTerms.forEach(t -> {
            final Term child = Generator.generateTermWithId();
            child.setParentTerms(Collections.singleton(t));
            child.setVocabulary(vocabulary.getUri());
            em.persist(child, descriptorFactory.termDescriptor(vocabulary));
        }));


        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(toDtos(rootTerms), result);
    }

    @Test
    void findAllBySearchStringReturnsTermsWithMatchingLabel() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final List<TermDto> result = sut.findAll(terms.get(0).getLabel().get(Environment.LANGUAGE), vocabulary);
        assertEquals(1, result.size());
        assertTrue(toDtos(terms).contains(result.get(0)));
    }

    @Test
    void findAllBySearchStringReturnsTermsWithMatchingLabelWhichAreNotRoots() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);
        final Term root = terms.get(Generator.randomIndex(terms));
        final Term child = Generator.generateTermWithId();
        child.setPrimaryLabel("test");
        child.setParentTerms(Collections.singleton(root));
        child.setGlossary(vocabulary.getGlossary().getUri());
        final Term matchingDesc = Generator.generateTermWithId();
        matchingDesc.setPrimaryLabel("Metropolitan plan");
        matchingDesc.setParentTerms(Collections.singleton(child));
        matchingDesc.setVocabulary(vocabulary.getUri());
        matchingDesc.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            em.persist(child, descriptorFactory.termDescriptor(vocabulary));
            em.persist(matchingDesc, descriptorFactory.termDescriptor(vocabulary));
            addTermInVocabularyRelationship(child, vocabulary.getUri());
            addTermInVocabularyRelationship(matchingDesc, vocabulary.getUri());
            insertNarrowerStatements(matchingDesc, child);
        });

        final List<TermDto> result = sut.findAll("plan", vocabulary);
        assertEquals(1, result.size());
        assertEquals(new TermDto(matchingDesc), result.get(0));
    }

    /**
     * Simulate the inverse of skos:broader and skos:narrower
     *
     * @param children Terms whose parents need skos:narrower relationships to them
     */
    private void insertNarrowerStatements(Term... children) {
        final Repository repo = em.unwrap(Repository.class);
        final ValueFactory vf = repo.getValueFactory();
        try (final RepositoryConnection conn = repo.getConnection()) {
            conn.begin();
            final IRI narrower = vf.createIRI(SKOS.NARROWER);
            for (Term t : children) {
                for (Term parent : t.getParentTerms()) {
                    conn.add(vf.createStatement(vf.createIRI(parent.getUri().toString()), narrower,
                            vf.createIRI(t.getUri().toString()),
                            vf.createIRI(
                                    descriptorFactory.termDescriptor(glossaryToVocabulary.get(parent.getGlossary()))
                                            .getSingleContext().get().toString())));
                }
            }
            conn.commit();
        }
    }

    @Test
    void existsInVocabularyReturnsTrueForLabelExistingInVocabulary() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final String label = terms.get(0).getLabel().get(Environment.LANGUAGE);
        assertTrue(sut.existsInVocabulary(label, vocabulary, Environment.LANGUAGE));
    }

    @Test
    void existsInVocabularyReturnsFalseForUnknownLabel() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        assertFalse(sut.existsInVocabulary("unknown label", vocabulary, Environment.LANGUAGE));
    }

    @Test
    void existsInVocabularyReturnsTrueWhenLabelDiffersOnlyInCase() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);

        final String label = terms.get(0).getLabel().get(Environment.LANGUAGE).toLowerCase();
        assertTrue(sut.existsInVocabulary(label, vocabulary, Environment.LANGUAGE));
    }

    @Test
    void existsInVocabularyReturnsFalseForLabelExistingInAnotherLanguageInTheVocabulary() {
        final Term term = Generator.generateMultiLingualTerm("en", "cs");
        final List<Term> terms = Collections.singletonList(term);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final String label = terms.get(0).getLabel().get("en");
        assertTrue(sut.existsInVocabulary(label, vocabulary, "en"));
        assertFalse(sut.existsInVocabulary(label, vocabulary, "cs"));
    }


    @Test
    void findAllGetsAllTermsInVocabulary() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);

        final List<Term> result = sut.findAll(vocabulary);
        assertEquals(terms.size(), result.size());
        assertTrue(terms.containsAll(result));
    }

    @Test
    void isEmptyReturnsTrueForEmptyVocabulary() {
        assertTrue(sut.isEmpty(vocabulary));
    }

    @Test
    void isEmptyReturnsFalseForNonemptyVocabulary() {
        final List<Term> terms = generateTerms(1);
        addTermsAndSave(terms, vocabulary);
        assertFalse(sut.isEmpty(vocabulary));
    }

    @Test
    void findAllReturnsAllTermsFromVocabularyOrderedByLabel() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);

        final List<Term> result = sut.findAll(vocabulary);
        terms.sort(Comparator.comparing(Term::getPrimaryLabel));
        assertEquals(terms, result);
    }

    @Test
    void persistSavesTermIntoVocabularyContext() {
        final Term term = Generator.generateTermWithId();
        transactional(() -> sut.persist(term, vocabulary));

        final Term result = em.find(Term.class, term.getUri(), descriptorFactory.termDescriptor(vocabulary));
        assertNotNull(result);
        assertEquals(term, result);
    }

    @Test
    void persistEnsuresVocabularyAttributeIsEmptySoThatItCanBeInferred() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> sut.persist(term, vocabulary));
        assertNull(term.getVocabulary());

        final Term result = em.find(Term.class, term.getUri(), descriptorFactory.termDescriptor(vocabulary));
        assertNotNull(result);
        // Term vocabulary should be null here, as we have inference disabled
        assertNull(result.getVocabulary());
    }

    @Test
    void updateUpdatesTermInVocabularyContext() {
        final Term term = Generator.generateTermWithId();
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            addTermInVocabularyRelationship(term, vocabulary.getUri());
        });

        term.setVocabulary(vocabulary.getUri());
        final String updatedLabel = "Updated label";
        final String oldLabel = term.getLabel().get(Environment.LANGUAGE);
        term.setPrimaryLabel(updatedLabel);
        em.getEntityManagerFactory().getCache().evictAll();
        transactional(() -> sut.update(term));

        final Term result = em.find(Term.class, term.getUri(), descriptorFactory.termDescriptor(vocabulary));
        assertEquals(updatedLabel, result.getLabel().get(Environment.LANGUAGE));
        assertFalse(em.createNativeQuery("ASK WHERE { ?x ?hasLabel ?label }", Boolean.class)
                      .setParameter("hasLabel", URI.create(SKOS.PREF_LABEL))
                      .setParameter("label", oldLabel, Environment.LANGUAGE).getSingleResult());
    }

    @Test
    void findAllRootsReturnsOnlyTermsWithMatchingLabelLanguage() {
        final List<Term> terms = generateTerms(5);
        final Term foreignLabelTerm = Generator.generateTermWithId();
        final List<Term> allTerms = new ArrayList<>(terms);
        allTerms.add(foreignLabelTerm);
        addTermsAndSave(allTerms, vocabulary);
        transactional(() -> insertForeignLabel(foreignLabelTerm));

        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(toDtos(terms), result);
    }

    private void insertForeignLabel(Term term) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            conn.remove(vf.createIRI(term.getUri().toString()), org.eclipse.rdf4j.model.vocabulary.SKOS.PREF_LABEL,
                    null);
            conn.add(vf.createIRI(term.getUri().toString()), org.eclipse.rdf4j.model.vocabulary.SKOS.PREF_LABEL,
                    vf.createLiteral("Adios", "es"));
        }
    }

    @Test
    void findAllReturnsOnlyTermsWithMatchingLanguageLabel() {
        final List<Term> terms = generateTerms(5);
        final Term foreignLabelTerm = Generator.generateTermWithId();
        final List<Term> allTerms = new ArrayList<>(terms);
        allTerms.add(foreignLabelTerm);
        addTermsAndSave(allTerms, vocabulary);
        transactional(() -> insertForeignLabel(foreignLabelTerm));

        final List<Term> result = sut.findAll(vocabulary);
        assertEquals(terms, result);
    }

    @Test
    void persistSupportsReferencingParentTermInSameVocabulary() {
        final Term term = Generator.generateTermWithId();
        final Term parent = Generator.generateTermWithId();
        transactional(() -> {
            parent.setGlossary(vocabulary.getGlossary().getUri());
            vocabulary.getGlossary().addRootTerm(parent);
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });
        term.addParentTerm(parent);

        transactional(() -> sut.persist(term, vocabulary));

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parent), result.getParentTerms());
    }

    @Test
    void persistSupportsReferencingParentTermInDifferentVocabulary() {
        final Term term = Generator.generateTermWithId();
        final Term parent = Generator.generateTermWithId();
        final Vocabulary parentVoc = Generator.generateVocabularyWithId();
        initVocabularyWorkspaceMetadata(parentVoc);
        transactional(() -> {
            parentVoc.getGlossary().addRootTerm(parent);
            em.persist(parentVoc, descriptorFactory.vocabularyDescriptor(parentVoc));
            parent.setGlossary(parentVoc.getGlossary().getUri());
            em.persist(parent, descriptorFactory.termDescriptor(parentVoc));
            Generator.addTermInVocabularyRelationship(parent, parentVoc.getUri(), em);
        });

        term.setGlossary(vocabulary.getGlossary().getUri());
        term.addParentTerm(parent);

        transactional(() -> sut.persist(term, vocabulary));

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parent), result.getParentTerms());
        final TypedQuery<Boolean> query = em.createNativeQuery("ASK {GRAPH ?g {?t ?hasParent ?p .}}", Boolean.class)
                .setParameter("g",
                        descriptorFactory.vocabularyDescriptor(vocabulary)
                                .getSingleContext().get())
                .setParameter("t", term.getUri())
                .setParameter("hasParent", URI.create(SKOS.BROADER))
                .setParameter("p", parent.getUri());
        assertTrue(query.getSingleResult());
    }

    private void initVocabularyWorkspaceMetadata(Vocabulary... vocabularies) {
        final WorkspaceMetadata wsMetadata = wsMetadataProvider.getCurrentWorkspaceMetadata();
        final Set<URI> uris = Arrays.stream(vocabularies).map(Vocabulary::getUri).collect(Collectors.toSet());
        doReturn(uris).when(wsMetadata).getVocabularyContexts();
        uris.forEach(u -> doReturn(new VocabularyInfo(u, u, u)).when(wsMetadata).getVocabularyInfo(u));
    }

    @Test
    void updateSupportsReferencingParentTermInDifferentVocabulary() {
        final Term term = Generator.generateTermWithId();
        final Term parent = Generator.generateTermWithId();
        final Vocabulary parentVoc = Generator.generateVocabularyWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.addParentTerm(parent);
        initVocabularyWorkspaceMetadata(parentVoc);
        transactional(() -> {
            parentVoc.getGlossary().addRootTerm(parent);
            em.persist(parentVoc, descriptorFactory.vocabularyDescriptor(parentVoc));
            glossaryToVocabulary.put(parentVoc.getGlossary().getUri(), parentVoc.getUri());
            parent.setGlossary(parentVoc.getGlossary().getUri());
            em.persist(parent, descriptorFactory.termDescriptor(parentVoc));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            addTermInVocabularyRelationship(term, vocabulary.getUri());
            addTermInVocabularyRelationship(parent, parentVoc.getUri());
        });

        final Term toUpdate = sut.find(term.getUri()).get();
        assertEquals(Collections.singleton(parent), toUpdate.getParentTerms());
        final MultilingualString newDefinition = MultilingualString
                .create("Updated definition", Environment.LANGUAGE);
        toUpdate.setDefinition(newDefinition);
        transactional(() -> sut.update(toUpdate));

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parent), result.getParentTerms());
        assertEquals(newDefinition, result.getDefinition());
    }

    @Test
    void updateSupportsSettingNewParentFromAnotherDifferentVocabulary() {
        final Term term = Generator.generateTermWithId();
        final Term parentOne = Generator.generateTermWithId();
        final Vocabulary parentOneVoc = Generator.generateVocabularyWithId();
        final Term parentTwo = Generator.generateTermWithId();
        final Vocabulary parentTwoVoc = Generator.generateVocabularyWithId();
        term.addParentTerm(parentOne);
        term.setGlossary(vocabulary.getGlossary().getUri());
        initVocabularyWorkspaceMetadata(parentOneVoc, parentTwoVoc);
        transactional(() -> {
            parentOneVoc.getGlossary().addRootTerm(parentOne);
            em.persist(parentOneVoc, descriptorFactory.vocabularyDescriptor(parentOneVoc));
            parentOne.setGlossary(parentOneVoc.getGlossary().getUri());
            em.persist(parentOne, descriptorFactory.termDescriptor(parentOneVoc));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(parentTwoVoc, descriptorFactory.vocabularyDescriptor(parentTwoVoc));
            parentTwo.setGlossary(parentTwoVoc.getGlossary().getUri());
            em.persist(parentTwo, descriptorFactory.termDescriptor(parentTwoVoc));
            addTermInVocabularyRelationship(term, vocabulary.getUri());
            addTermInVocabularyRelationship(parentOne, parentOneVoc.getUri());
            addTermInVocabularyRelationship(parentTwo, parentTwoVoc.getUri());
        });

        em.getEntityManagerFactory().getCache().evictAll();
        parentTwo.setVocabulary(parentTwoVoc.getUri());
        final Term toUpdate = sut.find(term.getUri()).get();
        assertEquals(Collections.singleton(parentOne), toUpdate.getParentTerms());
        toUpdate.setParentTerms(Collections.singleton(parentTwo));
        transactional(() -> sut.update(toUpdate));

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parentTwo), result.getParentTerms());
    }

    @Test
    void findAllLoadsSubTermsForResults() {
        final Term parent = persistParentWithChild();

        final List<Term> result = sut.findAll(vocabulary);
        assertEquals(2, result.size());
        final Optional<Term> parentResult = result.stream().filter(t -> t.equals(parent)).findFirst();
        assertTrue(parentResult.isPresent());
        assertEquals(parent.getSubTerms(), parentResult.get().getSubTerms());
    }

    private Term persistParentWithChild() {
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabulary.getGlossary().getUri());
        final Term child = Generator.generateTermWithId();
        child.setGlossary(vocabulary.getGlossary().getUri());
        child.setParentTerms(Collections.singleton(parent));
        parent.setSubTerms(Collections.singleton(new TermInfo(child)));
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.persist(child, descriptorFactory.termDescriptor(vocabulary));
            insertNarrowerStatements(child);
            addTermInVocabularyRelationship(parent, vocabulary.getUri());
            addTermInVocabularyRelationship(child, vocabulary.getUri());
        });
        return parent;
    }

    private void persistTerms(String lang, String... labels) {
        transactional(() -> {
            Arrays.stream(labels).forEach(label -> {
                final Term parent = Generator.generateTermWithId();
                parent.getLabel().set(lang, label);
                parent.setGlossary(vocabulary.getGlossary().getUri());
                vocabulary.getGlossary().addRootTerm(parent);
                em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
                em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
                addTermInVocabularyRelationship(parent, vocabulary.getUri());
            });
        });
    }

    @Test
    void findAllRootsOrdersResultsInLexicographicOrderForCzech() {
        configuration.getPersistence().setLanguage("cs");
        persistTerms("cs", "Německo", "Čína", "Španělsko", "Sýrie");
        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(4, result.size());
        assertEquals(Arrays
                .asList("Čína", "Německo", "Sýrie", "Španělsko"), result.stream().map(r -> r.getLabel().get("cs")).collect(Collectors.toList()));
    }

    @Test
    void findAllRootsLoadsSubTermsForResults() {
        final Term parent = persistParentWithChild();
        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        assertEquals(new TermDto(parent), result.get(0));
        assertEquals(parent.getSubTerms(), result.get(0).getSubTerms());
    }

    @Test
    void findAllBySearchStringLoadsSubTermsForResults() {
        final Term parent = persistParentWithChild();
        final String searchString = parent.getPrimaryLabel();
        final List<TermDto> result = sut.findAll(searchString, vocabulary);
        assertEquals(1, result.size());
        assertEquals(new TermDto(parent), result.get(0));
        assertEquals(parent.getSubTerms(), result.get(0).getSubTerms());
    }

    @Test
    void findLoadsSubTermsForResult() {

        final Term parent = persistParentWithChild();
        final Optional<Term> result = sut.find(parent.getUri());
        assertTrue(result.isPresent());
        assertEquals(parent.getSubTerms(), result.get().getSubTerms());
    }

    @Test
    void termSupportsSimpleLiteralSources() {
        final Term term = Generator.generateTermWithId();
        final Set<String> sources = new HashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "mpp/navrh/c-3/h-0/p-36/o-2"));
        term.setSources(sources);
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> sut.persist(term, vocabulary));

        transactional(() -> verifyTermSourceStatements(term));

        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();
        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(sources, result.getSources());
    }

    private void verifyTermSourceStatements(Term term) {
        final Repository repository = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repository.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            final IRI subject = vf.createIRI(term.getUri().toString());
            final IRI hasSource = vf.createIRI(DC.Terms.SOURCE);
            final List<Statement> sourceStatements = Iterations.asList(conn.getStatements(subject, hasSource, null));
            assertEquals(term.getSources().size(), sourceStatements.size());
            sourceStatements.forEach(ss -> {
                assertTrue(term.getSources().contains(ss.getObject().stringValue()));
                if (ss.getObject() instanceof Literal) {
                    final Literal litSource = (Literal) ss.getObject();
                    assertFalse(litSource.getLanguage().isPresent());
                    assertEquals(XSD.STRING, litSource.getDatatype());
                } else {
                    assertTrue(ss.getObject() instanceof IRI);
                }
            });
        }
    }

    @Test
    void findLastEditedLoadsVocabularyForTerms() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final PersistChangeRecord persistRecord = Generator.generatePersistChange(term);
        persistRecord.setAuthor(Generator.generateUserWithId());
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(persistRecord.getAuthor());
            em.persist(persistRecord, new EntityDescriptor(vocabulary.getUri()).addAttributeContext(
                    descriptorFactory.fieldSpec(PersistChangeRecord.class, "author"), null));
        });

        final List<RecentlyModifiedAsset> result = sut.findLastEdited(1);
        assertFalse(result.isEmpty());
        assertEquals(term.getUri(), result.get(0).getUri());
        assertEquals(term.getVocabulary(), result.get(0).getVocabulary());
    }

    @Test
    void updateSupportsUpdatingPluralMultilingualAltLabels() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final MultilingualString altOne = MultilingualString.create("Budova", "cs");
        term.setAltLabels(new HashSet<>(Collections.singleton(altOne)));
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> em.persist(term, descriptorFactory.termDescriptor(vocabulary)));

        altOne.set("en", "Building");
        final MultilingualString altTwo = MultilingualString.create("Construction", "en");
        altTwo.set("cs", "Stavba");
        term.getAltLabels().add(altTwo);
        transactional(() -> sut.update(term));

        final Term result = em.find(Term.class, term.getUri(), descriptorFactory.termDescriptor(vocabulary));
        assertNotNull(result);
        // We have to check it this way because the loaded multilingual labels might be a different combination of the
        // translations
        final Map<String, Set<String>> allLabels = new HashMap<>();
        result.getAltLabels().forEach(alt -> alt.getValue().forEach((lang, val) -> {
            allLabels.putIfAbsent(lang, new HashSet<>());
            allLabels.get(lang).add(val);
        }));
        term.getAltLabels().forEach(alt -> alt.getValue().forEach((lang, val) -> {
            assertThat(allLabels, hasKey(lang));
            assertThat(allLabels.get(lang), hasItem(val));
        }));
    }

    // Bug #1459
    @Test
    void updateHandlesChangesToTermsWithInferredDefinitionSource() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final File file = Generator.generateFileWithId("test.html");
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> {
            em.persist(file);
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
        });
        transactional(() -> {
            final TermDefinitionSource source = saveDefinitionSource(term, file);
            // This is normally inferred
            term.setDefinitionSource(source);
        });
        final String newDefinition = "new definition";
        term.getDefinition().set(Environment.LANGUAGE, newDefinition);
        transactional(() -> sut.update(term));

        final Term result = em.find(Term.class, term.getUri());
        assertEquals(newDefinition, result.getDefinition().get(Environment.LANGUAGE));
        assertNotNull(result.getDefinitionSource());
    }

    private TermDefinitionSource saveDefinitionSource(Term term, File file) {
        final TermDefinitionSource source = new TermDefinitionSource(term.getUri(), new FileOccurrenceTarget(file));
        source.getTarget().setSelectors(Collections.singleton(new TextQuoteSelector("test")));
        final Repository repo = em.unwrap(Repository.class);
        em.persist(source);
        em.persist(source.getTarget());
        try (final RepositoryConnection connection = repo.getConnection()) {
            // Simulates inference
            final ValueFactory vf = connection.getValueFactory();
            connection.add(vf.createIRI(term.getUri().toString()),
                    vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_zdroj_definice_termu),
                    vf.createIRI(source.getUri().toString()));
        }
        return source;
    }

    @Test
    void subTermLoadingSortsThemByLabel() {
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabulary.getGlossary().getUri());
        final List<Term> children = IntStream.range(0, 5).mapToObj(i -> {
            final Term child = Generator.generateTermWithId();
            child.setParentTerms(Collections.singleton(parent));
            return child;
        }).collect(Collectors.toList());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            children.forEach(child -> em.persist(child, descriptorFactory.termDescriptor(vocabulary)));
            insertNarrowerStatements(children.toArray(new Term[]{}));
            addTermInVocabularyRelationship(parent, vocabulary.getUri());
            children.forEach(child -> addTermInVocabularyRelationship(child, vocabulary.getUri()));
        });
        children.sort(Comparator.comparing(child -> child.getLabel().get(Environment.LANGUAGE)));

        final Optional<Term> result = sut.find(parent.getUri());
        assertTrue(result.isPresent());
        assertEquals(children.size(), result.get().getSubTerms().size());
        final Iterator<TermInfo> it = result.get().getSubTerms().iterator();
        for (Term child : children) {
            assertTrue(it.hasNext());
            final TermInfo next = it.next();
            assertEquals(child.getUri(), next.getUri());
        }
    }

    @Test
    void findAllBySearchStringAndVocabularyLoadsInferredParentTerms() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final String searchString = "test";
        term.getLabel().set(Environment.LANGUAGE, searchString + " value");
        final Term parent = Generator.generateTermWithId(vocabulary.getUri());
        vocabulary.getGlossary().addRootTerm(parent);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            addTermInVocabularyRelationship(term, vocabulary.getUri());
            addTermInVocabularyRelationship(parent, vocabulary.getUri());
            insertInferredBroaderRelationship(term, parent, em);
        });

        final List<TermDto> result = sut.findAll(searchString, vocabulary);
        assertEquals(1, result.size());
        assertThat(result.get(0).getParentTerms(), hasItem(new TermDto(parent)));
    }

    static void insertInferredBroaderRelationship(Term child, Term parent, EntityManager em) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            conn.add(vf.createIRI(child.getUri().toString()), vf.createIRI(SKOS.BROADER), vf.createIRI(parent.getUri().toString()));
        }
    }

    /**
     * Bug #1576
     */
    @Test
    void updateClearsPossiblyStaleTermDtoFromCache() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final String originalLabel = "Uppercase Test";
        term.getLabel().set(Environment.LANGUAGE, originalLabel);
        term.setGlossary(vocabulary.getGlossary().getUri());
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            addTermInVocabularyRelationship(term, vocabulary.getUri());
        });
        final List<TermDto> dto = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, dto.size());
        assertEquals(originalLabel, dto.get(0).getLabel().get(Environment.LANGUAGE));
        final String newLabel = originalLabel.toLowerCase();
        term.setLabel(MultilingualString.create(newLabel, Environment.LANGUAGE));
        transactional(() -> sut.update(term));
        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        assertEquals(newLabel, result.get(0).getLabel().get(Environment.LANGUAGE));
    }
}
