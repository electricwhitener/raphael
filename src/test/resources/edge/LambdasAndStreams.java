// expect: FINISHED
// output: [2, 4, 6]
// output: sum 12
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(1, 2, 3);
        Function<Integer, Integer> twice = x -> x * 2;
        List<Integer> doubled = numbers.stream().map(twice).collect(Collectors.toList());
        int sum = doubled.stream().mapToInt(Integer::intValue).sum();
        System.out.println(doubled);
        System.out.println("sum " + sum);
    }
}
