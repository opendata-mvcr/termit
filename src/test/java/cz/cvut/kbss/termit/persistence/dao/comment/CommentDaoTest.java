package cz.cvut.kbss.termit.persistence.dao.comment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class CommentDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private Configuration configuration;

    @Autowired
    private CommentDao sut;

    private User author;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
    }

    @Test
    void persistGeneratesDateOfCreation() {
        final Comment comment = generateComment(Generator.generateUri());
        transactional(() -> sut.persist(comment));

        final Comment result = em.find(Comment.class, comment.getUri());
        assertNotNull(result);
        assertNotNull(result.getCreated());
    }

    @Test
    void persistSavesSpecifiedCommentIntoCommentContext() {
        final Comment comment = generateComment(Generator.generateUri());
        transactional(() -> sut.persist(comment));

        final EntityDescriptor descriptor = createDescriptor();
        final Comment result = em.find(Comment.class, comment.getUri(), descriptor);
        assertNotNull(result);
    }

    private Comment generateComment(URI assetIri) {
        final Comment comment = new Comment();
        comment.setContent("Comment to an asset.");
        comment.setAuthor(author);
        comment.setAsset(assetIri);
        return comment;
    }

    private EntityDescriptor createDescriptor() {
        final EntityDescriptor descriptor = new EntityDescriptor(
                URI.create(configuration.get(ConfigParam.COMMENTS_CONTEXT)));
        descriptor.addAttributeDescriptor(Comment.getAuthorField(), new EntityDescriptor(null));
        return descriptor;
    }

    @Test
    void updateSetsLastModifiedValue() {
        final Comment comment = generateComment(Generator.generateUri());
        transactional(() -> em.persist(comment, createDescriptor()));

        comment.setContent("Updated content.");
        transactional(() -> sut.update(comment));

        em.getEntityManagerFactory().getCache().evictAll();
        final Comment result = em.find(Comment.class, comment.getUri());
        assertNotNull(result);
        assertNotNull(result.getModified());
        assertNotEquals(result.getCreated(), result.getModified());
    }

    @Test
    void updateMakesChangesInCommentContext() {
        final Comment comment = generateComment(Generator.generateUri());
        transactional(() -> em.persist(comment, createDescriptor()));

        final String newContent = "Updated content.";
        comment.setContent(newContent);
        transactional(() -> sut.update(comment));

        em.getEntityManagerFactory().getCache().evictAll();
        final Comment result = em.find(Comment.class, comment.getUri(), createDescriptor());
        assertNotNull(result);
        assertEquals(newContent, result.getContent());
    }

    @Test
    void removeDeletesSpecifiedComment() {
        final Comment comment = generateComment(Generator.generateUri());
        transactional(() -> em.persist(comment, createDescriptor()));

        transactional(() -> sut.remove(comment));

        assertNull(em.find(Comment.class, comment.getUri()));
    }

    @Test
    void findAllByAssetRetrievesAllCommentsForSpecifiedAsset() {
        final Term term = Generator.generateTermWithId();

        final List<Comment> comments = IntStream.range(0, 10).mapToObj(i -> generateComment(term.getUri())).collect(
                Collectors.toList());
        final EntityDescriptor descriptor = createDescriptor();
        transactional(() -> comments.forEach(c -> em.persist(c, descriptor)));
        final Comment anotherComment = generateComment(Generator.generateUri());
        transactional(() -> em.persist(anotherComment, descriptor));

        final List<Comment> result = sut.findAll(term);
        assertNotNull(result);
        assertEquals(comments.size(), result.size());
        assertTrue(comments.containsAll(result));
    }
}
