// A try can have several catches; Java uses the first one whose type matches the exception.
// Put the more specific types first. Watch which catch runs for each input.
public class MultipleCatches {
    static String parse(String text, int[] slots) {
        try {
            int n = Integer.parseInt(text);
            slots[n] = 1;
            return "stored at " + n;
        } catch (NumberFormatException e) {
            return "not a number: " + text;     // moment: {text} isn't a number, so the first catch takes it
        } catch (ArrayIndexOutOfBoundsException e) {
            return "no slot " + text;           // moment: {text} is a number, but slots has no index that big
        }
    }

    public static void main(String[] args) {
        int[] slots = new int[3];
        System.out.println(parse("2", slots));
        System.out.println(parse("two", slots));
        System.out.println(parse("7", slots));
    }
}
