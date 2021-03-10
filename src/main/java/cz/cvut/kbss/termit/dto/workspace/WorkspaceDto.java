package cz.cvut.kbss.termit.dto.workspace;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/**
 * DTO for transferring basic metadata about workspaces to clients.
 */
@OWLClass(iri = Vocabulary.s_c_metadatovy_kontext)  // Have OWLClass here to force serialization into JSON-LD
public class WorkspaceDto extends Workspace {

    /**
     * Vocabularies edited in this workspace.
     */
    @OWLObjectProperty(iri = Vocabulary.s_p_obsahuje_slovnik)
    private Set<URI> vocabularies;

    public WorkspaceDto() {
    }

    public WorkspaceDto(Workspace ws) {
        Objects.requireNonNull(ws);
        setUri(ws.getUri());
        setLabel(ws.getLabel());
        setDescription(ws.getDescription());
    }

    public Set<URI> getVocabularies() {
        return vocabularies;
    }

    public void setVocabularies(Set<URI> vocabularies) {
        this.vocabularies = vocabularies;
    }
}
