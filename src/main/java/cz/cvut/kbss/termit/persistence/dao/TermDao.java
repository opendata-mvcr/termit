/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.asset.provenance.ModifiesData;
import cz.cvut.kbss.termit.dto.TermDto;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.PersistenceUtils;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceBasedAssetDao;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.PageAndSearchSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
public class TermDao extends WorkspaceBasedAssetDao<Term> {

    private static final URI LABEL_PROP = URI.create(SKOS.PREF_LABEL);


    @Autowired
    public TermDao(EntityManager em, Configuration config, DescriptorFactory descriptorFactory, PersistenceUtils persistenceUtils) {
        super(Term.class, em, config, descriptorFactory, persistenceUtils);
    }

    @Override
    protected URI labelProperty() {
        return LABEL_PROP;
    }

    @Override
    public Optional<Term> find(URI id) {
        try {
            final URI vocabularyIri = resolveVocabularyIri(id);
            final Optional<Term> result = Optional.ofNullable(
                    em.find(Term.class, id, descriptorFactory.termDescriptor(vocabularyIri)));
            result.ifPresent(t -> loadAdditionTermMetadata(t, Collections.singleton(persistenceUtils.resolveVocabularyContext(vocabularyIri))));
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private URI resolveVocabularyIri(URI termIri) {
        return em.createNativeQuery("SELECT ?vocabulary WHERE { ?term ?inVocabulary ?vocabulary . }", URI.class)
                .setParameter("term", termIri)
                .setParameter("inVocabulary", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                .getSingleResult();
    }

    @Override
    public Optional<Term> getReference(URI id) {
        try {
            final Set<URI> graphs = resolveWorkspaceAndCanonicalContexts();
            final Descriptor descriptor = descriptorFactory.termDescriptor((URI) null);
            graphs.forEach(descriptor::addContext);
            return Optional.ofNullable(em.getReference(Term.class, id, descriptor));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Unsupported, use {@link #persist(Term, Vocabulary)}.
     */
    @Override
    public void persist(Term entity) {
        throw new UnsupportedOperationException(
                "Persisting term by itself is not supported. It has to be persisted into a vocabulary.");
    }

    /**
     * Persists the specified term into the specified vocabulary.
     *
     * @param entity     The term to persist
     * @param vocabulary Vocabulary which shall contain the persisted term
     */
    @ModifiesData
    public void persist(Term entity, Vocabulary vocabulary) {
        Objects.requireNonNull(entity);
        Objects.requireNonNull(vocabulary);

        try {
            entity.setGlossary(vocabulary.getGlossary().getUri());
            entity.setVocabulary(null); // This is inferred
            em.persist(entity, descriptorFactory.termDescriptor(vocabulary));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @ModifiesData
    @Override
    public Term update(Term entity) {
        Objects.requireNonNull(entity);
        assert entity.getVocabulary() != null;

        try {
            // Evict possibly cached instance loaded from default context
            em.getEntityManagerFactory().getCache().evict(Term.class, entity.getUri(), null);
            final Term original = em.find(Term.class, entity.getUri(), descriptorFactory.termDescriptor(entity));
            entity.setDefinitionSource(original.getDefinitionSource());
            return em.merge(entity, descriptorFactory.termDescriptor(entity.getVocabulary()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public void remove(Term entity) {
        Objects.requireNonNull(entity);
        assert entity.getVocabulary() != null;

        try {
            final Term toRemove = em.getReference(Term.class, entity.getUri(), descriptorFactory.termDescriptor(entity));
            if (toRemove != null) {
                em.remove(toRemove);
            }
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets all terms.
     * <p>
     * No differences are made between root terms and terms with parents.
     * <p>
     * The result contains first terms from the current workspace and then from the canonical container (if they fit in
     * the page).
     * <p>
     * This method prioritizes workspace versions of terms whose vocabularies are both in the canonical container and in
     * the current workspace.
     *
     * @return Matching terms, ordered by label
     */
    @Override
    public List<Term> findAll() {
        final Set<URI> vocContexts = persistenceUtils.getCurrentWorkspaceVocabularyContexts();
        final Set<Term> terms = new LinkedHashSet<>(findAllFrom(vocContexts, Constants.DEFAULT_PAGE_SPEC, Term.class));
        terms.addAll(findAllFrom(persistenceUtils.getCanonicalContainerContexts(), Constants.DEFAULT_PAGE_SPEC, Term.class));
        return new ArrayList<>(terms);
    }

    /**
     * Gets a page of all root terms.
     * <p>
     * The result contains first terms from the current workspace and then from the canonical container (if they fit in
     * the page).
     * <p>
     * This method prioritizes workspace versions of terms whose vocabularies are both in the canonical container and in
     * the current workspace.
     *
     * @param pageSpec Page specification
     * @return Content of the matching page of root terms
     */
    public List<TermDto> findAllRoots(Pageable pageSpec) {
        Objects.requireNonNull(pageSpec);
        final Set<URI> wsContexts = persistenceUtils.getCurrentWorkspaceVocabularyContexts();
        final int wsCount = countRootTermsIn(wsContexts);
        final int offset = (int) pageSpec.getOffset();
        final Set<TermDto> terms = new LinkedHashSet<>();
        if (wsCount > offset) {
            terms.addAll(findAllRootsFrom(wsContexts, pageSpec));
        }
        if (terms.size() < pageSpec.getPageSize()) {
            terms.addAll(findAllRootsFrom(persistenceUtils.getCanonicalContainerContexts(), determinePageSpecForCanonical(pageSpec, wsCount, terms.size())));
        }
        return new ArrayList<>(terms);
    }

    public List<TermDto> findAllRootsInCurrentWorkspace(Pageable pageSpec, URI excludedVocabulary) {
        Objects.requireNonNull(pageSpec);
        final Set<URI> wsContexts = persistenceUtils.getCurrentWorkspaceVocabularyContexts();
        if (excludedVocabulary != null) {
            wsContexts.remove(persistenceUtils.resolveVocabularyContext(excludedVocabulary));
        }
        return findAllRootsFrom(wsContexts, pageSpec);
    }

    public List<TermDto> findAllRootsInCanonical(Pageable pageSpec) {
        Objects.requireNonNull(pageSpec);
        final Set<URI> contexts = persistenceUtils.getCanonicalContainerContexts();
        return findAllRootsFrom(contexts, pageSpec);
    }

    private int countRootTermsIn(Set<URI> contexts) {
        try {
            return em.createNativeQuery("SELECT (COUNT(?term) as ?count) WHERE {" +
                    "GRAPH ?g {" +
                    "?term a ?type ." +
                    "?vocabulary ?hasGlossary/?hasTerm ?term ." +
                    "} FILTER (?g in (?graphs)) }", Integer.class)
                    .setParameter("type", typeUri)
                    .setParameter("hasGlossary", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_glosar))
                    .setParameter("hasTerm", URI.create(SKOS.HAS_TOP_CONCEPT))
                    .setParameter("graphs", contexts)
                    .getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private List<TermDto> findAllRootsFrom(Set<URI> contexts, Pageable pageSpec) {
        final String from = contexts.stream().map(u -> "FROM <" + u + ">").collect(Collectors.joining(" "));
        try {
            TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term " + from + " WHERE {" +
                    "?term a ?type ;" +
                    "?hasLabel ?label ." +
                    "?vocabulary ?hasGlossary/?hasTerm ?term ." +
                    "FILTER (lang(?label) = ?labelLang)" +
                    "} ORDER BY LCASE(?label)", TermDto.class)
                    .setParameter("labelLang", config.get(ConfigParam.LANGUAGE));
            query = setCommonFindAllRootsQueryParams(query);
            query.setMaxResults(pageSpec.getPageSize()).setFirstResult((int) pageSpec.getOffset());
            final Descriptor descriptor = descriptorFactory.termDescriptor((URI) null);
            resolveWorkspaceAndCanonicalContexts().forEach(descriptor::addContext);
            query.setDescriptor(descriptor);
            return executeQueryAndLoadSubTerms(query, contexts);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private Set<URI> resolveWorkspaceAndCanonicalContexts() {
        final Set<URI> contexts = persistenceUtils.getCanonicalContainerContexts();
        contexts.addAll(persistenceUtils.getCurrentWorkspaceVocabularyContexts());
        return contexts;
    }

    /**
     * Gets a page of all terms.
     * <p>
     * No differences are made between root terms and terms with parents.
     * <p>
     * The result contains first terms from the current workspace and then from the canonical container (if they fit in
     * the page).
     * <p>
     * This method prioritizes workspace versions of terms whose vocabularies are both in the canonical container and in
     * the current workspace.
     *
     * @param pageSpec Page specification
     * @return Matching terms, ordered by label
     */
    public List<TermDto> findAll(Pageable pageSpec) {
        Objects.requireNonNull(pageSpec);
        final Set<URI> wsContexts = persistenceUtils.getCurrentWorkspaceVocabularyContexts();
        final int wsCount = countTermsIn(wsContexts);
        final int offset = (int) pageSpec.getOffset();
        final Set<TermDto> terms = new LinkedHashSet<>();
        if (wsCount > offset) {
            terms.addAll(findAllFrom(wsContexts, pageSpec, TermDto.class));
        }
        if (terms.size() < pageSpec.getPageSize()) {
            terms.addAll(findAllFrom(persistenceUtils.getCanonicalContainerContexts(), determinePageSpecForCanonical(pageSpec, wsCount, terms.size()), TermDto.class));
        }
        return new ArrayList<>(terms);
    }

    private int countTermsIn(Set<URI> contexts) {
        try {
            return em.createNativeQuery("SELECT (COUNT(?term) as ?count) WHERE {" +
                    "GRAPH ?g {" +
                    "?term a ?type ." +
                    "} FILTER (?g in (?graphs)) }", Integer.class)
                    .setParameter("type", typeUri)
                    .setParameter("graphs", contexts)
                    .getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private Pageable determinePageSpecForCanonical(Pageable original, int wsCount, int alreadyHave) {
        if (wsCount == 0) {
            return original;
        }
        final int newOffset = (original.getPageNumber() + 1) * original.getPageSize() - wsCount;
        return PageRequest.of(newOffset / original.getPageSize(), original.getPageSize() - alreadyHave);
    }

    private <T extends AbstractTerm> List<T> findAllFrom(Set<URI> contexts, Pageable pageSpec, Class<T> resultType) {
        final String from = contexts.stream().map(u -> "FROM <" + u + ">").collect(Collectors.joining(" "));
        try {
            TypedQuery<T> query = em.createNativeQuery("SELECT DISTINCT ?term " + from + " WHERE {" +
                    "?term a ?type ;" +
                    "?hasLabel ?label ." +
                    "FILTER (lang(?label) = ?labelLang)" +
                    "} ORDER BY LCASE(?label)", resultType)
                    .setParameter("type", typeUri)
                    .setParameter("hasLabel", LABEL_PROP)
                    .setParameter("labelLang", config.get(ConfigParam.LANGUAGE));
            query.setMaxResults(pageSpec.getPageSize()).setFirstResult((int) pageSpec.getOffset());
            final Descriptor descriptor = descriptorFactory.termDescriptor((URI) null);
            resolveWorkspaceAndCanonicalContexts().forEach(descriptor::addContext);
            query.setDescriptor(descriptor);
            final List<T> result = executeQueryAndLoadSubTerms(query, contexts);
            if (TermDto.class.isAssignableFrom(resultType)) {
                result.forEach(t -> {
                    final TermDto dto = (TermDto) t;
                    initParentTerms(dto);
                    dto.getParentTerms().addAll(loadInferredParentTerms(dto, contexts, dto.getParentTerms()));
                });
            }
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets all terms on the specified vocabulary.
     * <p>
     * No differences are made between root terms and terms with parents.
     *
     * @param vocabulary Vocabulary whose terms should be returned
     * @return Matching terms, ordered by label
     */
    public List<Term> findAll(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try {
            final URI vocabularyCtx = persistenceUtils.resolveVocabularyContext(vocabulary.getUri());
            final TypedQuery<Term> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                    "GRAPH ?g { " +
                    "?term a ?type ;" +
                    "?hasLabel ?label ." +
                    "FILTER (lang(?label) = ?labelLang) ." +
                    "}" +
                    "?term ?inVocabulary ?vocabulary. } ORDER BY LCASE(?label)", Term.class)
                    .setParameter("type", typeUri)
                    .setParameter("vocabulary", vocabulary.getUri())
                    .setParameter("g", vocabularyCtx)
                    .setParameter("hasLabel", LABEL_PROP)
                    .setParameter("inVocabulary",
                            URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                    .setParameter("labelLang", config.get(ConfigParam.LANGUAGE));
            query.setDescriptor(descriptorFactory.termDescriptor(vocabulary));
            return executeQueryAndLoadSubTerms(query, Collections.singleton(vocabularyCtx));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Finds terms whose label contains the specified search string.
     * <p>
     * This method searches in the current workspace and in the canonical container, but workspace results are
     * preferred.
     *
     * @param searchString String the search term labels by
     * @return List of matching terms
     */
    public List<TermDto> findAll(String searchString) {
        Objects.requireNonNull(searchString);
        final Set<URI> vocContexts = persistenceUtils.getCurrentWorkspaceVocabularyContexts();
        final Set<TermDto> terms = new LinkedHashSet<>(findAllFrom(vocContexts, searchString, Constants.DEFAULT_PAGE_SPEC));
        terms.addAll(findAllFrom(persistenceUtils.getCanonicalContainerContexts(), searchString, Constants.DEFAULT_PAGE_SPEC));
        return new ArrayList<>(terms);
    }

    public List<TermDto> findAllInCurrentWorkspace(PageAndSearchSpecification searchSpecification, URI excludedVocabulary) {
        final Set<URI> wsContexts = persistenceUtils.getCurrentWorkspaceVocabularyContexts();
        if (excludedVocabulary != null) {
            wsContexts.remove(persistenceUtils.resolveVocabularyContext(excludedVocabulary));
        }
        return searchSpecification.getSearchString().isPresent() ? findAllFrom(wsContexts, searchSpecification.getSearchString().get(), searchSpecification.getPageSpec()) : findAllFrom(wsContexts, searchSpecification.getPageSpec(), TermDto.class);
    }

    public List<TermDto> findAllInCanonical(PageAndSearchSpecification searchSpecification) {
        final Set<URI> contexts = persistenceUtils.getCanonicalContainerContexts();
        return searchSpecification.getSearchString().isPresent() ? findAllFrom(contexts, searchSpecification.getSearchString().get(), searchSpecification.getPageSpec()) : findAllFrom(contexts, searchSpecification.getPageSpec(), TermDto.class);
    }

    private List<TermDto> findAllFrom(Set<URI> contexts, String searchString, Pageable pageSpec) {
        final String from = contexts.stream().map(u -> "FROM <" + u + ">").collect(Collectors.joining(" "));
        try {
            TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term " + from + " WHERE {" +
                    "?term a ?type ;" +
                    "?hasLabel ?label ." +
                    "FILTER CONTAINS(LCASE(?label), LCASE(?searchString)) ." +
                    "} ORDER BY LCASE(?label)", TermDto.class)
                    .setParameter("type", typeUri)
                    .setParameter("hasLabel", LABEL_PROP)
                    .setParameter("searchString", searchString, config.get(ConfigParam.LANGUAGE))
                    .setFirstResult((int) pageSpec.getOffset())
                    .setMaxResults(pageSpec.getPageSize());
            final Descriptor descriptor = descriptorFactory.termDescriptor((URI) null);
            contexts.forEach(descriptor::addContext);
            query.setDescriptor(descriptor);
            final List<TermDto> result = executeQueryAndLoadSubTerms(query, contexts);
            result.forEach(t -> {
                initParentTerms(t);
                t.getParentTerms().addAll(loadInferredParentTerms(t, contexts, t.getParentTerms()));
            });
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private void initParentTerms(TermDto t) {
        if (t.getParentTerms() == null) {
            t.setParentTerms(new LinkedHashSet<>());
        }
    }

    private List<TermDto> loadInferredParentTerms(TermDto term, Set<URI> graphs, Set<TermDto> exclude) {
        return em.createNativeQuery("SELECT DISTINCT ?parent WHERE {" +
                "GRAPH ?g { " +
                "?parent a ?type ." +
                "}" +
                "?term ?broader ?parent ." +    // Let broader be outside of the graph to include inference
                "FILTER (?g IN (?graphs))" +
                "FILTER (?parent NOT IN (?exclude))" +
                "}", TermDto.class).setParameter("type", typeUri)
                .setParameter("term", term)
                .setParameter("broader", URI.create(SKOS.BROADER))
                .setParameter("graphs", graphs)
                .setParameter("exclude", exclude)
                .getResultList();
    }

    /**
     * Returns true if the vocabulary does not contain any terms.
     *
     * @param vocabulary Vocabulary to check for existence of terms
     * @return true, if the vocabulary contains no terms, false otherwise
     */
    public boolean isEmpty(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try {
            return !em.createNativeQuery("ASK WHERE {" +
                    "GRAPH ?g { " +
                    "?term a ?type ;" +
                    "}" +
                    "?term ?inVocabulary ?vocabulary ." +
                    " }", Boolean.class)
                    .setParameter("type", typeUri)
                    .setParameter("vocabulary", vocabulary.getUri())
                    .setParameter("g", persistenceUtils.resolveVocabularyContext(vocabulary.getUri()))
                    .setParameter("inVocabulary", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                    .getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private <T extends AbstractTerm> List<T> executeQueryAndLoadSubTerms(TypedQuery<T> query, Set<URI> contexts) {
        return query.getResultStream().peek(t -> loadAdditionTermMetadata(t, contexts)).collect(Collectors.toList());
    }

    /**
     * Loads addition term metadata which are not directly loaded with the specified instance.
     */
    private void loadAdditionTermMetadata(AbstractTerm term, Set<URI> graphs) {
        term.setSubTerms(loadSubTermInfo(term, graphs));
        if (term instanceof Term) {
            term.setPublished(isPublished(term));
        }
    }

    /**
     * Loads sub-term info for the specified parent term.
     * <p>
     * The sub-terms are set directly on the specified parent.
     *
     * @param parent Parent term
     */
    private Set<TermInfo> loadSubTermInfo(AbstractTerm parent, Set<URI> graphs) {
        final Stream<TermInfo> subTermsStream = em.createNativeQuery("SELECT ?entity ?label ?vocabulary WHERE {" +
                "GRAPH ?g { " +
                "?entity a ?type ;" +
                "?hasLabel ?label ." +
                "FILTER (lang(?label) = ?labelLang) ." +
                "}" +
                "?entity ?broader ?parent ; " + // Let broader be outside of the graph to allow including inferences
                "?inVocabulary ?vocabulary ." +
                "FILTER (?g in (?graphs))" +
                "} ORDER BY LCASE(?label)", "TermInfo")
                .setParameter("type", typeUri)
                .setParameter("broader", URI.create(SKOS.BROADER))
                .setParameter("parent", parent)
                .setParameter("hasLabel", LABEL_PROP)
                .setParameter("inVocabulary", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                .setParameter("graphs", graphs)
                .setParameter("labelLang", config.get(ConfigParam.LANGUAGE))
                .getResultStream();
        // Use LinkedHashSet to preserve term order
        return subTermsStream.collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isPublished(AbstractTerm term) {
        // Note that this can be moved into Term once SPARQL-based attributes (https://github.com/kbss-cvut/jopa/issues/65) are supported
        return em.createNativeQuery("ASK WHERE {" +
                "    SELECT (count(?g) as ?cnt) WHERE {" +
                "    GRAPH ?g {" +
                "    ?term ?inScheme ?glossary ." +
                "    }" +
                "    } HAVING (?cnt > 1) }", Boolean.class)
                .setParameter("term", term)
                .setParameter("inScheme", URI.create(SKOS.IN_SCHEME))
                .setParameter("glossary", term.getGlossary()).getSingleResult();
    }

    /**
     * Loads a page of root terms (terms without a parent) contained in the specified vocabulary.
     *
     * @param vocabulary   Vocabulary whose root terms should be returned
     * @param pageSpec     Page specification
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching terms, ordered by their label
     */
    public List<TermDto> findAllRoots(Vocabulary vocabulary, Pageable pageSpec, Collection<URI> includeTerms) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(pageSpec);
        return findAllRootsImpl(vocabulary.getUri(), pageSpec, includeTerms);
    }

    private List<TermDto> findAllRootsImpl(URI vocabularyIri, Pageable pageSpec, Collection<URI> includeTerms) {
        TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                "GRAPH ?g { " +
                "?term a ?type ;" +
                "?hasLabel ?label ." +
                "?vocabulary ?hasGlossary/?hasTerm ?term ." +
                "FILTER (lang(?label) = ?labelLang) ." +
                "}} ORDER BY LCASE(?label)", TermDto.class);
        query = setCommonFindAllRootsQueryParams(query);
        query.setDescriptor(descriptorFactory.termDescriptor(vocabularyIri));
        try {
            final URI vocabularyCtx = persistenceUtils.resolveVocabularyContext(vocabularyIri);
            final List<TermDto> result = executeQueryAndLoadSubTerms(query.setParameter("vocabulary", vocabularyIri)
                    .setParameter("g", vocabularyCtx)
                    .setParameter("labelLang", config.get(ConfigParam.LANGUAGE))
                    .setMaxResults(pageSpec.getPageSize())
                    .setFirstResult((int) pageSpec.getOffset()), Collections.singleton(vocabularyCtx));
            result.addAll(loadIncludedTerms(includeTerms));
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private <T> TypedQuery<T> setCommonFindAllRootsQueryParams(TypedQuery<T> query) {
        return query.setParameter("type", typeUri)
                .setParameter("hasLabel", LABEL_PROP)
                .setParameter("hasGlossary", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_glosar))
                .setParameter("hasTerm", URI.create(SKOS.HAS_TOP_CONCEPT));
    }

    private List<TermDto> loadIncludedTerms(Collection<URI> includeTerms) {
        return includeTerms.stream()
                .map(u -> em.find(TermDto.class, u, descriptorFactory.termDescriptor(resolveVocabularyIri(u))))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Finds terms whose label contains the specified search string.
     * <p>
     * This method searches in the specified vocabulary only.
     *
     * @param searchString String the search term labels by
     * @param vocabulary   Vocabulary whose terms should be searched
     * @return List of matching terms
     */
    public List<TermDto> findAll(String searchString, Vocabulary vocabulary) {
        Objects.requireNonNull(searchString);
        Objects.requireNonNull(vocabulary);
        return findAllImpl(searchString, vocabulary.getUri());
    }

    private List<TermDto> findAllImpl(String searchString, URI vocabularyIri) {
        try {
            final URI vocabularyCtx = persistenceUtils.resolveVocabularyContext(vocabularyIri);
            final TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                    "GRAPH ?g { " +
                    "?term a ?type ; " +
                    "      ?hasLabel ?label . " +
                    "FILTER CONTAINS(LCASE(?label), LCASE(?searchString)) ." +
                    "}" +
                    "?term ?inVocabulary ?vocabulary ." +
                    "} ORDER BY LCASE(?label)", TermDto.class)
                    .setParameter("type", typeUri)
                    .setParameter("hasLabel", LABEL_PROP)
                    .setParameter("inVocabulary", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                    .setParameter("vocabulary", vocabularyIri)
                    .setParameter("g", vocabularyCtx)
                    .setParameter("searchString", searchString, config.get(ConfigParam.LANGUAGE));
            query.setDescriptor(descriptorFactory.termDescriptor(vocabularyIri));
            final List<TermDto> terms = executeQueryAndLoadSubTerms(query, Collections.singleton(vocabularyCtx));
            terms.forEach(t -> {
                loadParentSubTerms(t, vocabularyCtx);
                initParentTerms(t);
                t.getParentTerms().addAll(loadInferredParentTerms(t, Collections.singleton(vocabularyCtx), t.getParentTerms()));
            });
            return terms;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private void loadParentSubTerms(TermDto parent, URI vocabularyCtx) {
        loadAdditionTermMetadata(parent, Collections.singleton(vocabularyCtx));
        if (parent.getParentTerms() != null) {
            parent.getParentTerms().forEach(t -> loadParentSubTerms(t, vocabularyCtx));
        }
    }

    /**
     * Checks whether a term with the specified label exists in a vocabulary with the specified URI.
     * <p>
     * Note that this method uses comparison ignoring case, so that two labels differing just in character case are
     * considered same here.
     *
     * @param label      Label to check
     * @param vocabulary Vocabulary in which terms will be searched
     * @return Whether term with {@code label} already exists in vocabulary
     */
    public boolean existsInVocabulary(String label, Vocabulary vocabulary, String languageTag) {
        Objects.requireNonNull(label);
        Objects.requireNonNull(vocabulary);
        try {
            return em.createNativeQuery("ASK { GRAPH ?g {" +
                    "?term a ?type ; " +
                    "?hasLabel ?label ." +
                    "}" +
                    "?term ?inVocabulary ?vocabulary ." +
                    "FILTER (LCASE(?label) = LCASE(?searchString)) . }", Boolean.class)
                    .setParameter("type", typeUri)
                    .setParameter("hasLabel", LABEL_PROP)
                    .setParameter("inVocabulary", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                    .setParameter("vocabulary", vocabulary)
                    .setParameter("g", persistenceUtils.resolveVocabularyContext(vocabulary.getUri()))
                    .setParameter("searchString", label, languageTag != null ? languageTag : config.get(ConfigParam.LANGUAGE))
                    .getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Retrieves sub-terms of the specified parent term.
     *
     * @param parent Parent term
     * @return List of sub-terms or an empty list, if there are none
     */
    public List<Term> findAllSubTerms(Term parent) {
        Objects.requireNonNull(parent);
        final Set<URI> graphs = resolveWorkspaceAndCanonicalContexts();
        final Descriptor descriptor = descriptorFactory.termDescriptor((URI) null);
        graphs.forEach(descriptor::addContext);
        final TypedQuery<Term> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                "GRAPH ?g { " +
                "?term a ?type ." +
                "}" +
                "?term ?broader ?parent ." +    // Let broader be outside of the graph to include inference
                "FILTER (?g in (?graphs))" +
                "}", Term.class).setParameter("type", typeUri)
                .setParameter("broader", URI.create(SKOS.BROADER))
                .setParameter("parent", parent)
                .setParameter("graphs", graphs)
                .setDescriptor(descriptor);
        final List<Term> terms = executeQueryAndLoadSubTerms(query, graphs);
        terms.sort(Comparator.comparing(t -> t.getLabel().get(config.get(ConfigParam.LANGUAGE))));
        return terms;
    }
}
