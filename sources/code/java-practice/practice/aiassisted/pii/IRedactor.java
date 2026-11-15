package practice.aiassisted.pii;

import java.util.List;

public interface IRedactor {
    // returns a list of index pairs to redact in the given
    // string input each of the items in the list shoudl be
    // redacted in range [item[0], item[1]), i.e. start
    // inclusive and end exclusive.
    List<int[]> redact(String input);
}
