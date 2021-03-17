package cz.cvut.kbss.termit.environment;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Workspace;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generator of workspace-related test data.
 */
public class WorkspaceGenerator {

    private WorkspaceGenerator() {
        throw new AssertionError();
    }

    public static Collection<Statement> generateWorkspaceReferences(Collection<Vocabulary> vocabularies,
                                                                    Workspace workspace) {
        final List<Statement> statements = new ArrayList<>();
        final ValueFactory vf = SimpleValueFactory.getInstance();
        final IRI ws = vf.createIRI(workspace.getUri().toString());
        statements.add(vf.createStatement(ws, RDF.TYPE,
                vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_c_metadatovy_kontext), ws));
        final IRI hasContext = vf
                .createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_odkazuje_na_kontext);
        final IRI vocContext = vf
                .createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnikovy_kontext);
        vocabularies.forEach(v -> {
            final IRI vocCtx = vf.createIRI(v.getUri().toString());
            statements.add(vf.createStatement(ws, hasContext, vocCtx, ws));
            statements.add(vf.createStatement(vocCtx, RDF.TYPE, vocContext, ws));
        });
        return statements;
    }

    public static Workspace generateWorkspace() {
        final Workspace ws = new Workspace();
        ws.setUri(Generator.generateUri());
        ws.setLabel("Workspace " + Generator.randomInt(0, 10000));
        ws.setDescription("Description of workspace " + ws.getLabel());
        return ws;
    }

    public static Collection<Statement> generateCanonicalCacheContainer(String containerIri) {
        final ValueFactory vf = SimpleValueFactory.getInstance();
        final Set<URI> canonicalVocabularies = IntStream.range(0, 10).mapToObj(i -> Generator.generateUri()).collect(
                Collectors.toSet());
        return canonicalVocabularies.stream().map(v -> vf.createStatement(
                vf.createIRI(containerIri),
                vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_odkazuje_na_kontext), vf.createIRI(v.toString()),
                vf.createIRI(containerIri))).collect(Collectors.toList());
    }
}
