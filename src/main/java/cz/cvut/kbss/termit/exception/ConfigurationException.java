package cz.cvut.kbss.termit.exception;

/**
 * Indicates that a configuration issue (loading or processing of TermIt configuration) has occurred.
 */
public class ConfigurationException extends TermItException {

    public ConfigurationException(String message) {
        super(message);
    }
}
