// Reading input with a Scanner: nextInt() reads a number but leaves the end of its line behind,
// so a nextLine() straight after it reads nothing. Open the Files view and watch the input
// being read word by word. (The stdin line below is typed for you in the terminal; change it, or delete it and type your own.)
// stdin: 3 | Asha 19 | Ben 21 | Chen 20
import java.util.Scanner;

public class ReadingInput {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int count = sc.nextInt();
        String rest = sc.nextLine();             // moment: nextInt() read {count} but not the end of the line: this reads only that
        int total = 0;
        for (int i = 0; i < count; i++) {
            String name = sc.next();
            int age = sc.nextInt();
            total += age;
        }
        System.out.println("average age: " + (total / count));
    }
}
