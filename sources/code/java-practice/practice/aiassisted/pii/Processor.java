package practice.aiassisted.pii;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Processes an input using a set of @code{IRedactor}s to
 * mask/redact PII information
*/
public class Processor {
    private final Set<IRedactor> redactors;

    public Processor(IRedactor... redactors) {
        this.redactors = new HashSet<>();
        for (IRedactor r : redactors) {
            this.redactors.add(r);
        }
    }

    public List<String> process(List<String> input) {
        List<String> output = new ArrayList<>();
        for (String line : input) {
            // go over each line, and use each redactor to get
            // redactions. Wire all redactions across all redactors
            // into apply method to get the output
            List<int[]> redactions = new ArrayList<>();
            redactors.forEach(r -> redactions.addAll(r.redact(line)));
            output.add(apply(line, redactions));
        }

        return output;
    }

    private String apply(String line, List<int[]> redactions) {
        // TODO: Implement
        return line;
    }
}
