package raphael.model;

import java.io.Serializable;

// A line the user typed into the terminal while the program ran: the step it came at (the
// program was running step - 1's line, waiting for input), where it goes in the output (just
// after the prompt printed before it), and the text with its line end. Playing the run back, the
// terminal shows it from that step on, where a real terminal would have echoed it.
// OOP: a record (a tiny unchangeable data holder); Serializable, so it's saved with the run.
public record TypedLine(int step, int at, String text) implements Serializable {
}
