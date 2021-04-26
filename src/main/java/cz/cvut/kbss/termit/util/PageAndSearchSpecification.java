package cz.cvut.kbss.termit.util;

import org.springframework.data.domain.Pageable;

import java.util.Objects;
import java.util.Optional;

/**
 * Specification of data retrieval paging and search string.
 */
public class PageAndSearchSpecification {

    private final Pageable pageSpec;

    private final String searchString;

    public PageAndSearchSpecification(Pageable pageSpec, String searchString) {
        this.pageSpec = Objects.requireNonNull(pageSpec);
        this.searchString = searchString;
    }

    public Pageable getPageSpec() {
        return pageSpec;
    }

    public Optional<String> getSearchString() {
        return Optional.ofNullable(searchString);
    }

    @Override
    public String toString() {
        return "PageAndSearchSpecification{" +
                "pageSpec=" + pageSpec +
                ", searchString='" + searchString + '\'' +
                '}';
    }
}
