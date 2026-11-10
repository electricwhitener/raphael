package raphael.model;

import java.io.Serializable;

// One file of a program: where it is inside the program's folder ("Main.java", or
// "shapes/Circle.java" for a class in a package), and its text. A program typed into the editor
// on its own is one SourceFile.
// OOP: a record (a small unchangeable class), Serializable so a recording can keep the files.
// The path is a String, not a Path, because a recording may only hold java.lang and java.util
// types (see io/RunFile).
public record SourceFile(String path, String text) implements Serializable {
}
