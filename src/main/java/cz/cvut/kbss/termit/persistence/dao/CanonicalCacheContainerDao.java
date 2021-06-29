package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class CanonicalCacheContainerDao {

    private final EntityManager em;

    private final Configuration config;

    public CanonicalCacheContainerDao(EntityManager em, Configuration config) {
        this.em = em;
        this.config = config;
    }

    /**
     * Retrieves unique vocabulary contexts referenced by the canonical cache container.
     * <p>
     * The vocabulary contexts are unique in the sense that their working versions are not referenced by the specified workspace.
     *
     * @param workspace Workspace containing working versions of vocabularies which should be filtered out from the result.
     * @return Set of vocabulary context identifiers
     */
    public Set<URI> findUniqueCanonicalCacheContexts(Workspace workspace) {
        return em.createNativeQuery("SELECT ?ctx WHERE {" +
                "?canonicalCache ?referencesContext ?ctx ." +
                "FILTER NOT EXISTS {" +
                "?workspace ?referencesContext ?wsCtx ." +
                "?wsCtx ?versionOf ?ctx ." +
                "}}", URI.class)
                .setParameter("canonicalCache", URI.create(config.getRepository().getCanonicalContainer()))
                .setParameter("referencesContext", URI.create(Vocabulary.s_p_odkazuje_na_kontext))
                .setParameter("workspace", workspace)
                .setParameter("versionOf", URI.create(Vocabulary.s_p_vychazi_z_verze)).getResultStream().collect(Collectors.toSet());
    }
}
