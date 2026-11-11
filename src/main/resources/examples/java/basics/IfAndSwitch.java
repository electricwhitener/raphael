// if / else if / else picks the first branch whose condition is true; switch jumps straight to
// the matching case. Watch which lines run for each mark and each day.
public class IfAndSwitch {
    static String grade(int mark) {
        if (mark >= 90) {
            return "A";
        } else if (mark >= 75) {                 // moment: {mark} isn't 90 or more, so the next condition is tried
            return "B";
        } else {
            return "C";
        }
    }

    static String dayType(int day) {
        switch (day) {
            case 6:
            case 7:
                return "weekend";                // moment: no break between case 6 and case 7, so both land here
            default:
                return "weekday";
        }
    }

    public static void main(String[] args) {
        System.out.println(grade(95) + grade(80) + grade(40));
        System.out.println(dayType(3) + " " + dayType(6));
    }
}
