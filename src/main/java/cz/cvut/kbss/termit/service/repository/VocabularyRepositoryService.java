package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.exception.VocabularyImportException;
import cz.cvut.kbss.termit.exception.VocabularyRemovalException;
import cz.cvut.kbss.termit.model.*;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.dao.AssetDao;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSImporter;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.service.business.WorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Validator;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@CacheConfig(cacheNames = "vocabularies")
@Service
public class VocabularyRepositoryService extends BaseAssetRepositoryService<Vocabulary> implements VocabularyService {

    private final IdentifierResolver idResolver;

    private final VocabularyDao vocabularyDao;

    private final TermService termService;

    private final ChangeRecordService changeRecordService;

    private final ResourceRepositoryService resourceService;

    private final WorkspaceService workspaceService;

    private final Configuration.Namespace config;

    private final ApplicationContext context;

    @Autowired
    public VocabularyRepositoryService(ApplicationContext context, VocabularyDao vocabularyDao, IdentifierResolver idResolver,
                                       Validator validator, ChangeRecordService changeRecordService,
                                       @Lazy TermService termService,
                                       @Lazy ResourceRepositoryService resourceService,
                                       final Configuration config,
                                       final WorkspaceService workspaceService) {
        super(validator);
        this.context = context;
        this.vocabularyDao = vocabularyDao;
        this.idResolver = idResolver;
        this.termService = termService;
        this.changeRecordService = changeRecordService;
        this.resourceService = resourceService;
        this.workspaceService = workspaceService;
        this.config = config.getNamespace();
    }

    /**
     * This method ensures new instances of the prototype-scoped bean are returned on every call.
     */
    private SKOSImporter getSKOSImporter() {
        return context.getBean(SKOSImporter.class);
    }

    @Override
    protected AssetDao<Vocabulary> getPrimaryDao() {
        return vocabularyDao;
    }

    @CacheEvict(allEntries = true)
    @Override
    public void persist(Vocabulary instance) {
        super.persist(instance);
    }

    @Cacheable
    @Override
    public List<Vocabulary> findAll() {
        final List<Vocabulary> loaded = vocabularyDao.findAll(workspaceService.getCurrentWorkspace());
        return loaded.stream().map(this::postLoad).collect(Collectors.toList());
    }

    @Override
    protected void prePersist(@NonNull Vocabulary instance) {
        super.prePersist(instance);
        if (instance.getUri() == null) {
            instance.setUri(idResolver.generateIdentifier(config.getVocabulary(),
                instance.getLabel()));
        }
        verifyIdentifierUnique(instance);
        if (instance.getGlossary() == null) {
            instance.setGlossary(new Glossary());
        }
        if (instance.getModel() == null) {
            instance.setModel(new Model());
        }
        if (instance.getDocument() != null) {
            instance.getDocument().setVocabulary(null);
        }
    }

    @Override
    @Transactional
    public Vocabulary update(Vocabulary vNew) {
        final Vocabulary vOriginal = super.findRequired(vNew.getUri());
        resourceService.rewireDocumentsOnVocabularyUpdate(vOriginal, vNew);
        super.update(vNew);
        return vNew;
    }

    @Override
    public Collection<URI> getTransitiveDependencies(Vocabulary entity) {
        return vocabularyDao.getTransitiveDependencies(entity);
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Vocabulary asset) {
        return changeRecordService.getChanges(asset);
    }

    @Override
    public List<AbstractChangeRecord> getChangesOfContent(Vocabulary asset) {
        return vocabularyDao.getChangesOfContent(asset);
    }

    @CacheEvict(allEntries = true)
    @Transactional
    @Override
    public Vocabulary importVocabulary(boolean rename, URI vocabularyIri, MultipartFile file) {
        Objects.requireNonNull(file);
        try {
            final Vocabulary vocabulary = getSKOSImporter().importVocabulary(rename,
                    vocabularyIri,
                    file.getContentType(),
                    (v) -> this.persist(v),
                    file.getInputStream()
            );
            return vocabulary;
        } catch (VocabularyImportException e) {
            throw e;
        } catch (Exception e) {
            throw new VocabularyImportException("Unable to import vocabulary, because of: " + e.getMessage());
        }
    }

    @Override
    public long getLastModified() {
        return vocabularyDao.getLastModified();
    }

    @CacheEvict(allEntries = true)
    @Override
    public void remove(Vocabulary instance) {
        if (instance.getDocument() != null) {
            throw new VocabularyRemovalException(
                    "Removal of document vocabularies is not supported yet.");
        }

        final List<Vocabulary> vocabularies = vocabularyDao.getDependentVocabularies(instance);
        if (!vocabularies.isEmpty()) {
            throw new VocabularyRemovalException(
                    "Vocabulary cannot be removed. It is referenced from other vocabularies: "
                            + vocabularies.stream().map(Vocabulary::getLabel).collect(
                            Collectors.joining(", ")));
        }

        if (!termService.isEmpty(instance)) {
            throw new VocabularyRemovalException("Vocabulary cannot be removed. It contains terms.");
        }

        super.remove(instance);
    }

    @Transactional
    @Override
    public List<ValidationResult> validateContents(Vocabulary instance) {
        return vocabularyDao.validateContents(instance, workspaceService.getCurrentWorkspace());
    }

    @Override
    public Integer getTermCount(Vocabulary vocabulary) {
        return vocabularyDao.getTermCount(vocabulary);
    }
}
