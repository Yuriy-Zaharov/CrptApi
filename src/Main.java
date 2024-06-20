import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {

        Thread writer1 = new Thread(() -> SendPosts(200, 400));
        Thread writer2 = new Thread(() -> SendPosts(400, 600));

        writer1.start();
        writer2.start();
        SendPosts(0, 200);

        writer1.join();
        writer2.join();
    }

    private static void SendPosts(int start, int end) {
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES,25);

        for(int i = start; i < end; i++) {
            Document document = new Document(
                    new Document.Description("1234567890"), // Description object
                    Integer.toString(i),
                    "APPROVED",
                    "LP_INTRODUCE_GOODS",
                    true,
                    "1234567890",
                    "9876543210",
                    "0123456789",
                    LocalDate.of(2020, 1, 23),
                    "IMPORT",
                    List.of(
                            new Document.Product(
                                    "certificate.pdf",
                                    LocalDate.of(2020, 1, 23),
                                    "1234567890",
                                    "1234567890",
                                    "0123456789",
                                    LocalDate.of(2020, 1, 23),
                                    "1234567890",
                                    "9876543210",
                                    "0123456789"
                            )
                    ),
                    LocalDate.of(2020, 1, 23),
                    "ABC12345"
            );
            try {
                System.out.println(crptApi.sendPostCreateDocument(document));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
