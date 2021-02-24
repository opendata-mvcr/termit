/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.persistence;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.metamodel.FieldSpecification;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DescriptorFactoryTest extends BaseDaoTestRunner {

    private final Vocabulary vocabulary = Generator.generateVocabularyWithId();

    private Term term;

    private FieldSpecification<?, ?> parentFieldSpec;

    @Autowired
    private DescriptorFactory sut;

    @Autowired
    private EntityManager em;

    @Autowired
    private Configuration config;

    @Autowired
    private PersistenceUtils persistenceUtils;

    @Autowired
    private WorkspaceMetadataProvider workspaceMetadataProvider;

    @BeforeEach
    void setUp() {
        this.term = Generator.generateTermWithId();
        term.setVocabulary(vocabulary.getUri());
        this.parentFieldSpec = mock(FieldSpecification.class);
        when(parentFieldSpec.getJavaField()).thenReturn(Term.getParentTermsField());
    }

    @Test
    void termDescriptorCreatesSimpleTermDescriptorWhenNoParentsAreProvided() {
        final Descriptor result = sut.termDescriptor(term);
        assertEquals(Collections.singleton(persistenceUtils.resolveVocabularyContext(vocabulary.getUri())),
                result.getContexts());
        assertEquals(Collections.singleton(persistenceUtils.resolveVocabularyContext(vocabulary.getUri())),
                result.getAttributeContexts(parentFieldSpec));
    }

    @Test
    void termDescriptorCreatesSimpleTermDescriptorWhenParentsAreInSameVocabulary() {
        final Term parent = Generator.generateTermWithId();
        parent.setVocabulary(vocabulary.getUri());
        term.addParentTerm(parent);
        final Descriptor result = sut.termDescriptor(term);
        assertEquals(Collections.singleton(persistenceUtils.resolveVocabularyContext(vocabulary.getUri())),
                result.getContexts());
        assertEquals(Collections.singleton(persistenceUtils.resolveVocabularyContext(vocabulary.getUri())),
                result.getAttributeContexts(parentFieldSpec));
    }

    @Test
    void termDescriptorCreatesDescriptorWithParentTermContextsCorrespondingToContextsOfVocabulariesInWorkspace() {
        final Set<URI> vocabUris =
                IntStream.range(0, 5).mapToObj(i -> Generator.generateUri()).collect(Collectors.toSet());
        final WorkspaceMetadata wsMetadata = workspaceMetadataProvider.getCurrentWorkspaceMetadata();
        doReturn(vocabUris).when(wsMetadata).getVocabularyContexts();
        final Descriptor result = sut.termDescriptor(term);
        assertEquals(Collections.singleton(persistenceUtils.resolveVocabularyContext(vocabulary.getUri())),
                result.getContexts());
        assertEquals(vocabUris, result.getAttributeDescriptor(parentFieldSpec).getContexts());
    }

    @Test
    void fileDescriptorContainsAlsoDescriptorForDocument() {
        final File file = Generator.generateFileWithId("test.html");
        final Document doc = Generator.generateDocumentWithId();
        doc.addFile(file);
        file.setDocument(doc);
        doc.setVocabulary(Generator.generateUri());
        final Descriptor result = sut.fileDescriptor(doc.getVocabulary());
        final FieldSpecification<?, ?> docFieldSpec = mock(FieldSpecification.class);
        when(docFieldSpec.getJavaField()).thenReturn(File.getDocumentField());
        final Descriptor docDescriptor = result.getAttributeDescriptor(docFieldSpec);
        assertNotNull(docDescriptor);
    }

    @Test
    void termDescriptorCreatesDescriptorWithVocabularyAndParentTermsVocabularyFieldInDefaultContext() throws Exception {
        final Set<URI> vocabUris =
                IntStream.range(0, 5).mapToObj(i -> Generator.generateUri()).collect(Collectors.toSet());
        final WorkspaceMetadata wsMetadata = workspaceMetadataProvider.getCurrentWorkspaceMetadata();
        doReturn(vocabUris).when(wsMetadata).getVocabularyContexts();
        final Descriptor result = sut.termDescriptor(term);
        final FieldSpecification<?, ?> vocSpec = mock(FieldSpecification.class);
        when(vocSpec.getJavaField()).thenReturn(Term.class.getDeclaredField("vocabulary"));
        assertTrue(result.getAttributeContexts(vocSpec).isEmpty());
        final Descriptor parentDescriptor = result.getAttributeDescriptor(parentFieldSpec);
        assertTrue(parentDescriptor.getAttributeContexts(vocSpec).isEmpty());
    }

    @Test
    void termDescriptorCreatesDescriptorWithParentTermContextsContainingCanonicalCacheContainerContexts() {
        final Set<URI> workspaceVocabularies = IntStream.range(0, 3).mapToObj(i -> Generator.generateUri()).collect(Collectors.toSet());
        final WorkspaceMetadata wsMetadata = workspaceMetadataProvider.getCurrentWorkspaceMetadata();
        doReturn(workspaceVocabularies).when(wsMetadata).getVocabularyContexts();
        final Set<URI> canonicalVocabularies = generateCanonicalContainer();
        final Descriptor result = sut.termDescriptor(term);
        final Set<URI> parentContexts = result.getAttributeDescriptor(parentFieldSpec).getContexts();
        assertThat(parentContexts, hasItems(workspaceVocabularies.toArray(new URI[]{})));
        assertThat(parentContexts, hasItems(canonicalVocabularies.toArray(new URI[]{})));
    }

    private Set<URI> generateCanonicalContainer() {
        final Collection<Statement> statements = WorkspaceGenerator.generateCanonicalCacheContainer(config.get(ConfigParam.CANONICAL_CACHE_CONTAINER_IRI));
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                conn.begin();
                conn.add(statements);
                conn.commit();
            }
        });
        return statements.stream().map(s -> URI.create(s.getObject().stringValue())).collect(Collectors.toSet());
    }
}
