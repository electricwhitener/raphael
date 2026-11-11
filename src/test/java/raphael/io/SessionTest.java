package raphael.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SessionTest {

    // What's written is what's read back: each tab in order, and which was in front.
    @Test
    void tabsComeBackInOrder() {
        List<Session.Tab> tabs = List.of(new Session.Tab(Session.Kind.FILE, "/home/me/Sum.java"),
                new Session.Tab(Session.Kind.FOLDER, "/home/me/shapes"), new Session.Tab(Session.Kind.EXAMPLE, "lists/InsertAtHead"));
        Session.Saved saved = Session.read(Session.write(tabs, 2));
        assertEquals(tabs, saved.tabs());
        assertEquals(2, saved.front());
    }

    // A damaged or older value is read as far as it makes sense, never as an error.
    @Test
    void anythingElseIsSkipped() {
        Session.Saved saved = Session.read("front\tseven\nfile\t\nnonsense\nexample\tsorting/BubbleSort\nwindow\tbig\n");
        assertEquals(List.of(new Session.Tab(Session.Kind.EXAMPLE, "sorting/BubbleSort")), saved.tabs());
        assertEquals(-1, saved.front());
        assertEquals(List.of(), Session.read("").tabs());
        assertEquals(-1, Session.read("front\t5\nfile\t/a.java\n").front(), "a front past the last tab is none");
    }
}
