package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalCacheContainerDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private Configuration config;

    @Autowired
    private CanonicalCacheContainerDao sut;

    @Test
    void findUniqueCanonicalCacheContextsRetrievesContextsReferencedByCanonicalCacheContainer() {
        final Set<URI> canonicalVocabularies = generateCanonicalContainer();
        final Collection<URI> result = sut.findUniqueCanonicalCacheContexts(WorkspaceGenerator.generateWorkspace());
        assertEquals(canonicalVocabularies.size(), result.size());
        assertTrue(canonicalVocabularies.containsAll(result));
    }

    private Set<URI> generateCanonicalContainer() {
        final Set<URI> canonicalVocabularies = IntStream.range(0, 10).mapToObj(i -> Generator.generateUri()).collect(
                Collectors.toSet());
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                final ValueFactory vf = conn.getValueFactory();
                conn.begin();
                canonicalVocabularies.forEach(v -> conn
                        .add(vf.createIRI(config.get(ConfigParam.CANONICAL_CACHE_CONTAINER_IRI)),
                                vf.createIRI(Vocabulary.s_p_odkazuje_na_kontext), vf.createIRI(v.toString()),
                                vf.createIRI(config.get(ConfigParam.CANONICAL_CACHE_CONTAINER_IRI))));
                conn.commit();
            }
        });
        return canonicalVocabularies;
    }

    @Test
    void findUniqueCanonicalCacheContextsRetrievesContextsMinusContextsReferencedInSpecifiedWorkspace() {
        final Set<URI> canonicalVocabularies = generateCanonicalContainer();
        final Set<URI> withWorkingVersions = canonicalVocabularies.stream().filter(v -> Generator.randomBoolean()).collect(Collectors.toSet());
        final Workspace ws = WorkspaceGenerator.generateWorkspace();
        generateWorkspaceVersions(withWorkingVersions, ws);
        final Set<URI> matching = new HashSet<>(canonicalVocabularies);
        matching.removeAll(withWorkingVersions);

        final Collection<URI> result = sut.findUniqueCanonicalCacheContexts(ws);
        assertEquals(matching.size(), result.size());
        assertTrue(matching.containsAll(result));
    }

    private void generateWorkspaceVersions(Set<URI> canonical, Workspace ws) {
        transactional(() -> {
            em.persist(ws, new EntityDescriptor(ws.getUri()));
            final Repository repository = em.unwrap(Repository.class);
            try (final RepositoryConnection connection = repository.getConnection()) {
                final ValueFactory vf = connection.getValueFactory();
                final IRI wsIri = vf.createIRI(ws.getUri().toString());
                connection.begin();
                canonical.forEach(vocUri -> {
                    final IRI workingVersion = vf.createIRI(Generator.generateUri().toString());
                    connection.add(wsIri, vf.createIRI(Vocabulary.s_p_odkazuje_na_kontext), workingVersion, wsIri);
                    connection.add(workingVersion, vf.createIRI(Vocabulary.s_p_vychazi_z_verze), vf.createIRI(vocUri.toString()), wsIri);
                });
                connection.commit();
            }
        });
    }
}
