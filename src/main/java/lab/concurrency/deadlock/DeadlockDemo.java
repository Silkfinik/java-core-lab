package lab.concurrency.deadlock;

/**
 * Два потока бесконечно переводят деньги навстречу друг другу и быстро попадают во взаимную блокировку.
 * Программа сама не завершится: снимите дамп через jcmd и остановите её вручную.
 */
public class DeadlockDemo {
    public static void main(String[] args) {
        Bank bank = new Bank();
        Account a = new Account(1_000);
        Account b = new Account(1_000);

        System.out.println("pid: " + ProcessHandle.current().pid());

        startTransfers("transfer-a-to-b", bank, a, b);
        startTransfers("transfer-b-to-a", bank, b, a);
    }

    private static void startTransfers(String name, Bank bank, Account from, Account to) {
        Thread.ofPlatform().name(name).start(() -> {
            while (true) {
                bank.transfer(from, to, 1);
            }
        });
    }
}
