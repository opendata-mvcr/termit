package cz.cvut.kbss.termit.persistence.dao.workspace;

import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.event.InvalidateCachesEvent;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches workspace metadata (e.g., contexts of vocabularies in workspace) so that they don't have to be resolved from
 * vocabulary for every request.
 */
@Component
@Profile("!" + Constants.NO_CACHE_PROFILE)
public class CachingWorkspaceMetadataProvider extends WorkspaceMetadataProvider {

    private static final Logger LOG = LoggerFactory.getLogger(CachingWorkspaceMetadataProvider.class);

    /**
     * Workspace identifier -> workspace metadata
     */
    private final Map<URI, WorkspaceMetadata> workspaces = new ConcurrentHashMap<>();

    @Autowired
    public CachingWorkspaceMetadataProvider(WorkspaceStore workspaceStore, WorkspaceDao workspaceDao) {
        super(workspaceStore, workspaceDao);
    }

    /**
     * For testing purposes.
     */
    void initWorkspaceMetadata(WorkspaceMetadata metadata) {
        workspaces.put(metadata.getWorkspace().getUri(), metadata);
    }

    @Override
    public Workspace getWorkspace(URI workspaceUri) {
        return getWorkspaceMetadata(workspaceUri).getWorkspace();
    }

    @Override
    public WorkspaceMetadata getWorkspaceMetadata(URI workspaceUri) {
        if (!workspaces.containsKey(workspaceUri)) {
            final Workspace ws = workspaceDao.find(workspaceUri).orElseThrow(
                    () -> NotFoundException.create(Workspace.class.getSimpleName(), workspaceUri));
            LOG.trace("Loading metadata for workspace {} and storing them in cache.", ws);
            loadWorkspace(ws);
        }
        return workspaces.get(workspaceUri);
    }

    @Override
    public void loadWorkspace(Workspace workspace) {
        Objects.requireNonNull(workspace);
        workspaces.put(workspace.getUri(), loadWorkspaceMetadata(workspace));
    }

    @EventListener
    public void invalidateCache(InvalidateCachesEvent e) {
        LOG.info("Evicting workspace metadata cache...");
        workspaces.clear();
    }
}
