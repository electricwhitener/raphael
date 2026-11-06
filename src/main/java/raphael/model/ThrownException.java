package raphael.model;

import java.util.List;

// An exception the student's program threw and then caught: what it was, the line that threw it,
// the line of the catch that caught it, and the methods it left on the way (each one stopped
// in the middle, from the one that threw outwards). It's kept on the first step of the catch.
// (An exception nothing catches ends the run instead; see Snapshot.getException().)
// OOP: a record, a small unchangeable data class; List.copyOf keeps it unchangeable.
public record ThrownException(String type, String message, int throwLine, int catchLine, List<String> leftMethods) {

    public ThrownException {
        leftMethods = List.copyOf(leftMethods);
    }

    // "NumberFormatException: For input string: \"abc\"", or just the type with no message.
    public String text() {
        return message == null ? type : type + ": " + message;
    }
}
