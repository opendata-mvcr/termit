package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.workspace.WorkspaceDto;
import cz.cvut.kbss.termit.exception.workspace.WorkspaceNotSetException;
import cz.cvut.kbss.termit.model.Workspace;

import java.net.URI;

public interface WorkspaceService {

    /**
     * Loads workspace with the specified identifier and stores it is the user's session.
     *
     * @param id Workspace identifier
     * @return The loaded workspace with vocabulary metadata
     */
    WorkspaceDto loadWorkspace(URI id);

    /**
     * Gets the current user's loaded workspace.
     *
     * @return Current user's workspace
     * @throws WorkspaceNotSetException Indicates that no workspace is currently loaded
     */
    Workspace getCurrentWorkspace();

    /**
     * Gets the current user's loaded workspace with basic metadata about vocabularies it contains.
     *
     * @return Current user's workspace with vocabulary metadata
     * @throws WorkspaceNotSetException Indicates that no workspace is currently loaded
     */
    WorkspaceDto getCurrentWorkspaceWithMetadata();
}
