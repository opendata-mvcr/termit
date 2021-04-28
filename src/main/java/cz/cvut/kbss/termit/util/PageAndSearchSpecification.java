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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PageAndSearchSpecification that = (PageAndSearchSpecification) o;
        return getPageSpec().equals(that.getPageSpec()) && Objects.equals(getSearchString(), that.getSearchString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPageSpec(), getSearchString());
    }

    @Override
    public String toString() {
        return "PageAndSearchSpecification{" +
                "pageSpec=" + pageSpec +
                ", searchString='" + searchString + '\'' +
                '}';
    }
}
