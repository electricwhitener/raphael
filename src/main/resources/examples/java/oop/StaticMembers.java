// A static field belongs to the class: there's one, shared by every object.
// An instance field belongs to each object separately.
public class StaticMembers {
    static class Student {
        static int count = 0;      // shared: how many students exist
        final int id;              // each student's own
        String name;

        Student(String name) {
            count++;
            this.id = count;
            this.name = name;
        }
    }

    public static void main(String[] args) {
        Student a = new Student("Asha");
        Student b = new Student("Ben");
        Student c = new Student("Chen");
        System.out.println(c.name + " is student " + c.id + " of " + Student.count);
    }
}
