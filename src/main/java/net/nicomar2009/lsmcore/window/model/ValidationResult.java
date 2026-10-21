package net.nicomar2009.lsmcore.window.model;

import java.util.List;

/** Stable error keys, localized only at presentation boundaries. */
public record ValidationResult(List<String> errors) {
    public ValidationResult { errors = List.copyOf(errors); }
    public boolean valid() { return errors.isEmpty(); }
    public void requireValid() {
        if (!valid()) throw new IllegalArgumentException(String.join(", ", errors));
    }
}
