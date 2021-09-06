public class Main {
    public static void main(String[] args) {
        long l = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            System.out.println(CheckerCorporate.isActive("9710083390")); // true активна

        }
        System.out.println(System.currentTimeMillis() - l); // 300 раз = 2857 ms

        System.out.println(CheckerCorporate.isActive("9710083391")); // false не существует
        System.out.println(CheckerCorporate.isActive("7604147344")); // false ликвидирована

    }
}
