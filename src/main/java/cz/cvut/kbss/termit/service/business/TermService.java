package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.util.TermStatus;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.service.export.VocabularyExporters;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.PageAndSearchSpecification;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for term-related business logic.
 */
@Service
public class TermService implements RudService<Term>, ChangeRecordProvider<Term> {

    private final VocabularyExporters exporters;

    private final VocabularyService vocabularyService;

    private final TermRepositoryService repositoryService;

    private final TermOccurrenceService termOccurrenceService;

    private final ChangeRecordService changeRecordService;

    private final CommentService commentService;

    @Autowired
    public TermService(VocabularyExporters exporters, VocabularyService vocabularyService,
                       TermRepositoryService repositoryService,
                       TermOccurrenceService termOccurrenceService, ChangeRecordService changeRecordService,
                       CommentService commentService) {
        this.exporters = exporters;
        this.vocabularyService = vocabularyService;
        this.repositoryService = repositoryService;
        this.termOccurrenceService = termOccurrenceService;
        this.changeRecordService = changeRecordService;
        this.commentService = commentService;
    }

    /**
     * Attempts to export glossary terms from the specified vocabulary as the specified media type.
     * <p>
     * If export into the specified media type is not supported, an empty {@link Optional} is returned.
     *
     * @param vocabulary Vocabulary to export
     * @param mediaType  Expected media type of the export
     * @return Exported resource wrapped in an {@code Optional}
     */
    public Optional<TypeAwareResource> exportGlossary(Vocabulary vocabulary, String mediaType) {
        Objects.requireNonNull(vocabulary);
        return exporters.exportVocabularyGlossary(vocabulary, mediaType);
    }

    /**
     * Gets a page of all root terms.
     * <p>
     * That is, terms without parent term.
     * <p>
     * The returned roots are prioritized from the current workspace and then from the canonical container. In case of
     * terms existing both in the current workspace and in the canonical container, the workspace versions take
     * precedence.
     *
     * @param pageSpec Page specification
     * @return Content of matching page of root terms
     */
    public List<TermDto> findAllRoots(Pageable pageSpec) {
        Objects.requireNonNull(pageSpec);
        return repositoryService.findAllRoots(pageSpec);
    }

    /**
     * Gets a page of all terms available in the current workspace, regardless of their position in the SKOS hierarchy.
     * <p>
     * The returned terms are prioritized from the current workspace and then from the canonical container. In case of
     * terms existing both in the current workspace and in the canonical container, the workspace versions take
     * precedence.
     *
     * @param pageSpec Page specification
     * @return Content of matching page of terms
     */
    public List<TermDto> findAll(Pageable pageSpec) {
        Objects.requireNonNull(pageSpec);
        return repositoryService.findAll(pageSpec);
    }

    @Transactional(readOnly = true)
    public List<TermDto> findAllRootsInCurrentWorkspace(Pageable pageSpec, URI excludedVocabulary) {
        Objects.requireNonNull(pageSpec);
        return repositoryService.findAllRootsInCurrentWorkspace(pageSpec, excludedVocabulary);
    }

    @Transactional(readOnly = true)
    public List<TermDto> findAllInCurrentWorkspace(PageAndSearchSpecification searchSpecification, URI excludedVocabulary) {
        Objects.requireNonNull(searchSpecification);
        return repositoryService.findAllInCurrentWorkspace(searchSpecification, excludedVocabulary);
    }

    @Transactional(readOnly = true)
    public List<TermDto> findAllRootsInCanonical(Pageable pageSpec) {
        Objects.requireNonNull(pageSpec);
        return repositoryService.findAllRootsInCanonical(pageSpec);
    }

    @Transactional(readOnly = true)
    public List<TermDto> findAllInCanonical(PageAndSearchSpecification searchSpecification) {
        Objects.requireNonNull(searchSpecification);
        return repositoryService.findAllInCanonical(searchSpecification);
    }

    /**
     * Finds all terms which match the specified search string in the current workspace and in the canonical container.
     * <p>
     * Workspace results are placed before canonical container results. In case of terms existing both in the current
     * workspace and in the canonical container, the workspace versions take precedence.
     *
     * @param searchString Search string
     * @return Matching terms
     */
    public List<TermDto> findAll(String searchString) {
        Objects.requireNonNull(searchString);
        return repositoryService.findAll(searchString);
    }

    /**
     * Retrieves all terms from the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose terms will be returned
     * @return Matching terms
     */
    public List<Term> findAll(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return repositoryService.findAll(vocabulary);
    }

    /**
     * Gets the total number of terms in the specified vocabulary.
     *
     * @param vocabulary Vocabulary reference
     * @return Number of terms in the specified vocabulary
     */
    public Integer getTermCount(Vocabulary vocabulary) {
        return vocabularyService.getTermCount(vocabulary);
    }

    /**
     * Retrieves root terms (terms without parent) from the specified vocabulary.
     * <p>
     * The page specification parameter allows configuration of the number of results and their offset.
     *
     * @param vocabulary   Vocabulary whose terms will be returned
     * @param pageSpec     Paging specification
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching terms
     */
    public List<TermDto> findAllRoots(Vocabulary vocabulary, Pageable pageSpec, Collection<URI> includeTerms) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(pageSpec);
        return repositoryService.findAllRoots(vocabulary, pageSpec, includeTerms);
    }

    /**
     * Finds out whether the given vocabulary contains any terms or not.
     *
     * @param vocabulary vocabulary under consideration
     * @return true if the vocabulary contains no terms, false otherwise
     */
    public boolean isEmpty(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return repositoryService.isEmpty(vocabulary);
    }

    /**
     * Finds all terms which match the specified search string in the specified vocabulary.
     *
     * @param searchString Search string
     * @param vocabulary   Vocabulary whose terms should be returned
     * @return Matching terms
     */
    public List<TermDto> findAll(String searchString, Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(searchString);
        return repositoryService.findAll(searchString, vocabulary);
    }

    /**
     * Gets vocabulary with the specified identifier.
     *
     * @param id Vocabulary identifier
     * @return Matching vocabulary
     * @throws NotFoundException When vocabulary with the specified identifier does not exist
     */
    public Vocabulary findVocabularyRequired(URI id) {
        Objects.requireNonNull(id);
        return vocabularyService.find(id)
                .orElseThrow(() -> NotFoundException.create(Vocabulary.class.getSimpleName(), id));
    }

    /**
     * Gets a reference to the vocabulary with the specified identifier.
     *
     * @param id Vocabulary identifier
     * @return Matching vocabulary reference
     * @throws NotFoundException When vocabulary with the specified identifier does not exist
     */
    public Vocabulary getRequiredVocabularyReference(URI id) {
        return vocabularyService.getRequiredReference(id);
    }

    /**
     * Gets a term with the specified identifier.
     *
     * @param id Term identifier
     * @return Matching term wrapped in an {@code Optional}
     */
    public Optional<Term> find(URI id) {
        return repositoryService.find(id);
    }

    /**
     * Gets a term with the specified identifier.
     *
     * @param id Term identifier
     * @return Matching term
     * @throws NotFoundException When no matching term is found
     */
    public Term findRequired(URI id) {
        return repositoryService.findRequired(id);
    }

    /**
     * Gets a reference to a Term with the specified identifier.
     *
     * @param id Term identifier
     * @return Matching Term reference wrapped in an {@code Optional}
     */
    public Optional<Term> getReference(URI id) {
        return repositoryService.getReference(id);
    }

    /**
     * Gets a reference to a Term with the specified identifier.
     *
     * @param id Term identifier
     * @return Matching term reference
     * @throws NotFoundException When no matching term is found
     */
    public Term getRequiredReference(URI id) {
        return repositoryService.getRequiredReference(id);
    }

    /**
     * Gets child terms of the specified parent term.
     *
     * @param parent Parent term whose children should be loaded
     * @return List of child terms
     */
    public List<Term> findSubTerms(Term parent) {
        Objects.requireNonNull(parent);
        return repositoryService.findSubTerms(parent);
    }

    /**
     * Gets aggregated info about assignments and occurrences of the specified Term.
     *
     * @param term Term whose assignments and occurrences to retrieve
     * @return List of term assignment describing instances
     */
    public List<TermAssignments> getAssignmentInfo(Term term) {
        Objects.requireNonNull(term);
        return repositoryService.getAssignmentsInfo(term);
    }

    /**
     * Checks whether a term with the specified label already exists in the specified vocabulary.
     *
     * @param termLabel  Label to search for
     * @param vocabulary Vocabulary in which to search
     * @param language   Language to check existence in.
     * @return Whether a matching label was found
     */
    public boolean existsInVocabulary(String termLabel, Vocabulary vocabulary, String language) {
        Objects.requireNonNull(termLabel);
        Objects.requireNonNull(vocabulary);
        return repositoryService.existsInVocabulary(termLabel, vocabulary, language);
    }

    /**
     * Persists the specified term as a root term in the specified vocabulary's glossary.
     *
     * @param term  Term to persist
     * @param owner Vocabulary to add the term to
     */
    public void persistRoot(Term term, Vocabulary owner) {
        Objects.requireNonNull(term);
        Objects.requireNonNull(owner);
        repositoryService.addRootTermToVocabulary(term, owner);
    }

    /**
     * Persists the specified term as a child of the specified parent term.
     *
     * @param child  The child to persist
     * @param parent Existing parent term
     */
    public void persistChild(Term child, Term parent) {
        Objects.requireNonNull(child);
        Objects.requireNonNull(parent);
        repositoryService.addChildTerm(child, parent);
    }

    /**
     * Updates the specified term.
     *
     * @param term Term update data
     * @return The updated term
     */
    public Term update(Term term) {
        Objects.requireNonNull(term);
        return repositoryService.update(term);
    }

    /**
     * Removes the specified term.
     *
     * @param term Term to remove
     */
    public void remove(Term term) {
        Objects.requireNonNull(term);
        repositoryService.remove(term);
    }

    /**
     * Sets the definition source of the specified term.
     *
     * @param term             Term whose definition source is being specified
     * @param definitionSource Definition source representation
     */
    @Transactional
    public void setTermDefinitionSource(Term term, TermDefinitionSource definitionSource) {
        Objects.requireNonNull(term);
        Objects.requireNonNull(definitionSource);
        definitionSource.setTerm(term.getUri());
        final Term existingTerm = repositoryService.findRequired(term.getUri());
        if (existingTerm.getDefinitionSource() != null) {
            termOccurrenceService.removeOccurrence(existingTerm.getDefinitionSource());
        }
        termOccurrenceService.persistOccurrence(definitionSource);
    }

    /**
     * Updates the specified term's status to the specified value.
     *
     * @param term   Term whose status to update
     * @param status The new status
     */
    @Transactional
    public void setStatus(Term term, TermStatus status) {
        Objects.requireNonNull(term);
        Objects.requireNonNull(status);
        final Term toUpdate = repositoryService.findRequired(term.getUri());
        toUpdate.setDraft(status == TermStatus.DRAFT);
        repositoryService.update(toUpdate);
    }

    /**
     * Gets a reference to a Term occurrence with the specified identifier.
     *
     * @param id Term occurrence identifier
     * @return Matching Term occurrence reference
     */
    public TermOccurrence getRequiredOccurrenceReference(URI id) {
        return termOccurrenceService.getRequiredReference(id);
    }

    public void approveOccurrence(URI identifier) {
        termOccurrenceService.approveOccurrence(identifier);
    }

    public void removeOccurrence(TermOccurrence occurrence) {
        termOccurrenceService.removeOccurrence(occurrence);
    }

    /**
     * Gets unused terms (in annotations/occurrences).
     *
     * @return List of terms
     */
    public List<URI> getUnusedTermsInVocabulary(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return repositoryService.getUnusedTermsInVocabulary(vocabulary);
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Term term) {
        Objects.requireNonNull(term);
        return changeRecordService.getChanges(term);
    }

    /**
     * Gets comments related to the specified term.
     *
     * @param term Term to get comments for
     * @return List of comments
     */
    public List<Comment> getComments(Term term) {
        return commentService.findAll(term);
    }

    /**
     * Adds the specified comment to the specified target term.
     *
     * @param comment Comment to add (create)
     * @param target  Term to which the comment pertains
     */
    public void addComment(Comment comment, Term target) {
        Objects.requireNonNull(comment);
        Objects.requireNonNull(target);
        commentService.addToAsset(comment, target);
    }
}
