import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

    private final int timesPerInterval;
    private final long timeInterval;
    private static final Queue<Long> timeQueue = new LinkedBlockingQueue<>();
    private static final Queue<Request> requestQueue= new LinkedBlockingQueue<>();
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Gson gson;

    public CrptApi(TimeUnit timeUnit, int timesPerInterval) {
        if (timesPerInterval < 1) {
            throw new RuntimeException("timesPerInterval can't be less 1.");
        }
        this.timeInterval = timeUnit.toMillis(1);
        this.timesPerInterval = timesPerInterval;
        this.gson = new GsonBuilder().create();
    }

    public String sendPostCreateDocument(Document documentObj) throws InterruptedException {
        String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
        return sendPost(apiUrl, documentObj);
    }

    private String sendPost(String apiUrl, Object requestBody) throws InterruptedException {
        Request request = new Request(apiUrl, requestBody);

        lock.lock(); // Acquire lock for the critical section
        requestQueue.offer(request);
        try {

            long currentTime = System.currentTimeMillis();
            while (timeQueue.size() >= timesPerInterval) {
                long oldestRequestTime = timeQueue.peek();

                if (currentTime - oldestRequestTime >= timeInterval) {
                    timeQueue.poll(); // Remove old request time
                } else {
                    // Wait for a slot to open
                    lock.unlock(); // Release the lock while waiting

                    //noinspection BusyWait
                    Thread.sleep(currentTime - oldestRequestTime + 1);
                    lock.lock(); // Re-acquire the lock before proceeding
                    // Recheck queue size after waiting (as it could have changed)
                    currentTime = System.currentTimeMillis(); // Update currentTime
                }
            }
            timeQueue.offer(currentTime);
            Request requestToPost = requestQueue.peek();
            requestQueue.poll();

            // Critical Section is complete -> unlock
            lock.unlock();

            return httpPost(requestToPost);

        } finally {
            if (lock.isLocked()){
                lock.unlock();
            }
        }
    }

    private String httpPost(Request request) {
        try {
            // Create HTTP POST request
            HttpPost httpPost = new HttpPost(request.apiUrl());
            httpPost.setHeader("Content-Type", "application/json");

            // Convert request body to JSON string
            String requestJson = gson.toJson(request.requestBody());
            StringEntity requestEntity = new StringEntity(requestJson);
            httpPost.setEntity(requestEntity);

            // Send request and get response
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(httpPost);

            // Process response
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
                StringBuilder responseString = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseString.append(line);
                }
                reader.close();

                return responseString.toString();
            } else {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Empty response from API.");
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted: " + e.getMessage());
        }
    }
}

record Request(String apiUrl, Object requestBody) {
}

record Document(
        Description description,
        String docId,
        String docStatus,
        String docType,
        boolean importRequest,
        String ownerInn,
        String participantInn,
        String producerInn,
        LocalDate productionDate,
        String productionType,
        List<Product> products,
        LocalDate regDate,
        String regNumber
) implements Serializable {

    public record Description(
            String participantInn
    ) implements Serializable {
    }

    public record Product(
            String certificateDocument,
            LocalDate certificateDocumentDate,
            String certificateDocumentNumber,
            String ownerInn,
            String producerInn,
            LocalDate productionDate,
            String tnvedCode,
            String uitCode,
            String uituCode
    ) implements Serializable {
    }
}
