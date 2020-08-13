package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/comments")
public class CommentController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(CommentController.class);

    private final CommentService commentService;

    @Autowired
    public CommentController(IdentifierResolver idResolver, Configuration config, CommentService commentService) {
        super(idResolver, config);
        this.commentService = commentService;
    }

    @GetMapping(value = "/{idFragment}", produces = {JsonLd.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public Comment getById(@PathVariable String idFragment,
                           @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        return commentService.findRequired(idResolver.resolveIdentifier(namespace, idFragment));
    }

    @PutMapping(value = "/{idFragment}", consumes = {JsonLd.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String idFragment,
                       @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace,
                       @RequestBody Comment update) {
        verifyRequestAndEntityIdentifier(update, idResolver.resolveIdentifier(namespace, idFragment));
        commentService.update(update);
        LOG.debug("Comment {} successfully updated.", update);
    }

    @DeleteMapping("/{idFragment}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String idFragment,
                       @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final Comment toRemove = getById(idFragment, namespace);
        commentService.remove(toRemove);
        LOG.debug("Comment {} successfully removed.", toRemove);
    }

    @PostMapping(value = "/{idFragment}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeComment(@PathVariable String idFragment,
                            @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace,
                            Principal principal) {
        final Comment comment = getById(idFragment, namespace);
        commentService.likeComment(comment);
        LOG.trace("Comment {} liked by user {}.", comment, principal);
    }

    @DeleteMapping(value = "/{idFragment}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCommentLike(@PathVariable String idFragment,
                                  @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace,
                                  Principal principal) {
        final Comment comment = getById(idFragment, namespace);
        commentService.removeMyReactionTo(comment);
        LOG.trace("Like on comment {} removed by user {}.", comment, principal);
    }

    @PostMapping(value = "/{idFragment}/dislikes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dislikeComment(@PathVariable String idFragment,
                            @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace,
                            Principal principal) {
        final Comment comment = getById(idFragment, namespace);
        commentService.dislikeComment(comment);
        LOG.trace("Comment {} disliked by user {}.", comment, principal);
    }

    @DeleteMapping(value = "/{idFragment}/dislikes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCommentDislike(@PathVariable String idFragment,
                                  @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace,
                                  Principal principal) {
        final Comment comment = getById(idFragment, namespace);
        commentService.removeMyReactionTo(comment);
        LOG.trace("Dislike on to comment {} removed by user {}.", comment, principal);
    }
}
