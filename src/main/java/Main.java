import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        System.out.println(CheckerCorporate.isActive("9710083390")); // true активна
        System.out.println(CheckerCorporate.isActive("9710083391")); // false не существует
        System.out.println(CheckerCorporate.isActive("7604147344")); // false ликвидирована

    }
}
