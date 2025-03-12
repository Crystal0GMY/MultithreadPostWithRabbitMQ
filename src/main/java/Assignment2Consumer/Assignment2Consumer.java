//package Assignment2Consumer;
//
//import com.google.gson.JsonObject;
//import com.google.gson.JsonParser;
//import java.nio.charset.StandardCharsets;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.concurrent.*;
//import com.rabbitmq.client.*;
//
//public class Assignment2Consumer {
//  private static final String QUEUE_NAME = "skiers_queue";
//  private static final String RABBITMQ_HOST = "54.203.75.33";
//  private static final ConcurrentHashMap<Integer, List<Integer>> skierLiftMap = new ConcurrentHashMap<>();
//
//  public static void main(String[] args) {
//    int numThreads = Runtime.getRuntime().availableProcessors(); // Use CPU cores for parallelism
//    ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
//
//    for (int i = 0; i < numThreads; i++) {
//      executorService.submit(new ConsumerWorker());
//    }
//
//    // Graceful shutdown hook
//    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//      System.out.println("Shutting down consumer...");
//      executorService.shutdown();
//      try {
//        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
//          executorService.shutdownNow();
//        }
//      } catch (InterruptedException e) {
//        executorService.shutdownNow();
//      }
//    }));
//  }
//
//  static class ConsumerWorker implements Runnable {
//    @Override
//    public void run() {
//      try {
//        ConnectionFactory factory = new ConnectionFactory();
//        factory.setHost(RABBITMQ_HOST);
//        factory.setUsername("myuser");
//        factory.setPassword("mypassword");
//        factory.setAutomaticRecoveryEnabled(true);
//        factory.setRequestedHeartbeat(30);
//
//        try (Connection connection = factory.newConnection();
//            Channel channel = connection.createChannel()) {
//
//          channel.queueDeclare(QUEUE_NAME, true, false, false, null);
//          channel.basicQos(5);
//
//          DeliverCallback deliverCallback = (consumerTag, delivery) -> {
//            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
//            processMessage(message);
//            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
//          };
//
//          System.out.println(Thread.currentThread().getName() + " waiting for messages...");
//
//          channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {});
//
//          // Keep thread alive to listen for messages
//          synchronized (this) {
//            wait();
//          }
//        }
//      } catch (Exception e) {
//        System.err.println("Error in consumer thread: " + e.getMessage());
//        e.printStackTrace();
//      }
//    }
//
//    private void processMessage(String message) {
//      try {
//        JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();
//        int skierId = jsonObject.get("skierID").getAsInt();
//        int liftId = jsonObject.get("liftID").getAsInt();
//
//        // Store all lift rides for each skier
//        skierLiftMap.computeIfAbsent(skierId, k -> Collections.synchronizedList(new ArrayList<>())).add(liftId);
//      } catch (Exception e) {
//        System.err.println("Invalid JSON message: " + message);
//        e.printStackTrace();
//      }
//    }
//  }
//}


package Assignment2Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import com.rabbitmq.client.*;

public class Assignment2Consumer {
  private static final String QUEUE_NAME = "skiers_queue";
  private static final String RABBITMQ_HOST = "54.203.75.33";
  private static final ConcurrentHashMap<Integer, List<Integer>> skierLiftMap = new ConcurrentHashMap<>();

  private static final int NUM_OF_CONSUMERS = 5;

  private static final ConnectionFactory factory = new ConnectionFactory();
  private static Connection connection;
  private static final ExecutorService consumerExecutor = Executors.newCachedThreadPool();
  private static final ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor();

  public static void main(String[] args) {
    try {
      factory.setHost(RABBITMQ_HOST);
      factory.setUsername("myuser");
      factory.setPassword("mypassword");
      factory.setAutomaticRecoveryEnabled(true);
      factory.setRequestedHeartbeat(30);

      connection = factory.newConnection();

      // Start with the initial consumer count
      for (int i = 0; i < NUM_OF_CONSUMERS; i++) {
        consumerExecutor.submit(new ConsumerWorker());
      }

      // Graceful shutdown hook
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("Shutting down consumers...");
        monitorExecutor.shutdown();
        consumerExecutor.shutdown();
        try {
          if (!consumerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            consumerExecutor.shutdownNow();
          }
          connection.close();
        } catch (Exception ignored) {}
      }));

    } catch (Exception e) {
      System.err.println("Error initializing consumer: " + e.getMessage());
    }
  }

  static class ConsumerWorker implements Runnable {
    @Override
    public void run() {
      try (Channel channel = connection.createChannel()) {
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        channel.basicQos(10); // Prevents one consumer from taking too many messages

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
          String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
          processMessage(message);
          channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        };

        System.out.println(Thread.currentThread().getName() + " waiting for messages...");
        channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {});

        // Keep thread alive
        synchronized (this) { wait(); }

      } catch (Exception e) {
        System.err.println("Consumer error: " + e.getMessage());
      }
    }

    private void processMessage(String message) {
      try {
        JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();
        int skierId = jsonObject.get("skierID").getAsInt();
        int liftId = jsonObject.get("liftID").getAsInt();

        // Store lift rides for each skier
        skierLiftMap.computeIfAbsent(skierId, k -> Collections.synchronizedList(new ArrayList<>())).add(liftId);
      } catch (Exception e) {
        System.err.println("Invalid JSON message: " + message);
      }
    }
  }
}
