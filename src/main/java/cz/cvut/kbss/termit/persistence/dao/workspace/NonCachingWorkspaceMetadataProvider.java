package cz.cvut.kbss.termit.persistence.dao.workspace;

import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Provides workspace metadata without caching.
 *
 * That is, data are retrieved from the repository on every request.
 */
@Component
@Profile(Constants.NO_CACHE_PROFILE)
public class NonCachingWorkspaceMetadataProvider extends WorkspaceMetadataProvider {

    protected NonCachingWorkspaceMetadataProvider(WorkspaceStore workspaceStore, WorkspaceDao workspaceDao) {
        super(workspaceStore, workspaceDao);
    }

    @Override
    public Workspace getWorkspace(URI workspaceUri) {
        return workspaceDao.find(workspaceUri)
                           .orElseThrow(() -> NotFoundException.create(Workspace.class.getSimpleName(), workspaceUri));
    }

    @Override
    public WorkspaceMetadata getWorkspaceMetadata(URI workspaceUri) {
        final Workspace ws = getWorkspace(workspaceUri);
        return loadWorkspaceMetadata(ws);
    }
}
