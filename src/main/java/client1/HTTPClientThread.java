package client1;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class HTTPClientThread implements Callable {
  private static final HttpClient client = HttpClient.newHttpClient();
  private final String baseUrl;
  private final AtomicInteger successfulRequests;
  private final AtomicInteger failedRequests;
  private final Integer requestPerThread;
  private final CountDownLatch latch; // For initial 32 threads
  private final String LOG_FILE = "request_logs.csv";

  public HTTPClientThread(String baseUrl, AtomicInteger successfulRequests, AtomicInteger failedRequests, Integer requestPerThread, CountDownLatch latch) {
    this.baseUrl = baseUrl;
    this.successfulRequests = successfulRequests;
    this.failedRequests = failedRequests;
    this.requestPerThread = requestPerThread;
    this.latch = latch;
  }

  @Override
  public Void call() {
    try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
      for (int i = 0; i < requestPerThread; i++) {
        SkierLiftRideEvent event = EventGeneratorThread.getEvent();

        String eventUrl = String.format(
            "%s/skiers/%d/seasons/%d/days/%d/skiers/%d",
            baseUrl, event.getResortID(), event.getSeasonID(), event.getDayID(), event.getSkierID()
        );

        Gson gson = new Gson();
        String json = gson.toJson(event);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(eventUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        int retries = 0;
        while (retries < 5) {
          long startTime = System.currentTimeMillis();
          try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long endTime = System.currentTimeMillis();
            long latency = endTime - startTime;
            double throughput = 1000.0 / latency;

            synchronized (HTTPClientThread.class) {
              writer.printf("%d,POST,%d,%d,%.2f%n", startTime, latency, response.statusCode(), throughput);
              writer.flush();
            }

            if (response.statusCode() == 201) {
              successfulRequests.incrementAndGet();
              break;
            } else {
              System.out.println(response.body());
              retries++;
            }
          } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long latency = endTime - startTime;
            double throughput = 1000.0 / latency;

            synchronized (HTTPClientThread.class) {
              writer.printf("%d,POST,%d,500,%.2f%n", startTime, latency, throughput);
              writer.flush();
            }

            retries++;
            if (retries >= 5) {
              failedRequests.incrementAndGet();
              break;
            }
            TimeUnit.SECONDS.sleep(1);
          }
        }
      }
    }  catch (InterruptedException | IOException e) {
      throw new RuntimeException(e);
    } finally {
      if (latch != null) {
        latch.countDown(); // Decrement latch count after the initial 32 threads complete
      }
    }
    return null;
  }
}
