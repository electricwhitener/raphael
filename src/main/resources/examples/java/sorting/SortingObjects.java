// Sorting objects: Java can't know how to order Students by itself. compareTo() (from the
// Comparable interface) gives the natural order; a Comparator gives any other order.
import java.util.Arrays;
import java.util.Comparator;

public class SortingObjects {
    static class Student implements Comparable<Student> {
        String name;
        int marks;

        Student(String name, int marks) {
            this.name = name;
            this.marks = marks;
        }

        @Override
        public int compareTo(Student other) {
            return this.name.compareTo(other.name);    // by name
        }

        @Override
        public String toString() {
            return name + " " + marks;
        }
    }

    public static void main(String[] args) {
        Student[] students = {new Student("Zoe", 71), new Student("Adi", 88), new Student("Max", 64)};
        Arrays.sort(students);                         // uses compareTo
        System.out.println(Arrays.toString(students));
        Arrays.sort(students, Comparator.comparingInt(s -> -s.marks));   // highest marks first
        System.out.println(Arrays.toString(students));
    }
}
