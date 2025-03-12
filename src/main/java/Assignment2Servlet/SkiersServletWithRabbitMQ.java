package Assignment2Servlet;

import com.google.gson.JsonObject;
import java.io.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@WebServlet(name = "SkiersServletWithRabbitMQ", urlPatterns = "/skiers/*")
public class SkiersServletWithRabbitMQ extends HttpServlet {
  private final Gson gson = new Gson();

  private static final String QUEUE_NAME = "skiers_queue";
  private static final String RABBITMQ_HOST = "54.203.75.33";
  private static final int POOL_SIZE = 100;
  private ConnectionFactory factory;
  private Connection connection;
  private BlockingQueue<Channel> channelPool;

  @Override
  public void init() throws ServletException {
    super.init();
    try {
      factory = new ConnectionFactory();
      factory.setHost(RABBITMQ_HOST);
      factory.setUsername("myuser");
      factory.setPassword("mypassword");

      // Initialize connection
      connection = factory.newConnection();

      // Initialize a pool of channels
      channelPool = new ArrayBlockingQueue<>(POOL_SIZE);
      for (int i = 0; i < POOL_SIZE; i++) {
        Channel channel = connection.createChannel();
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        channelPool.offer(channel);
      }

    } catch (Exception e) {
      throw new ServletException("Failed to initialize RabbitMQ connection or channel pool", e);
    }
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("text/html");
    response.setStatus(HttpServletResponse.SC_OK);
    PrintWriter out = response.getWriter();
    out.println("<h1>It works! :)</h1>");
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.setContentType("application/json");
    String[] pathParts = request.getPathInfo() != null ? request.getPathInfo().split("/") : new String[0];

    if (pathParts.length != 8) {
      sendErrorResponse(response, "Invalid URL format. Expected: /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}");
      return;
    }
    try {
      int resortID = Integer.parseInt(pathParts[1]);
      String seasonID = pathParts[3];
      String dayID = pathParts[5];
      int skierID = Integer.parseInt(pathParts[7]);
      if (!dayID.matches("^([1-9]|[1-9][0-9]|[12][0-9][0-9]|3[0-5][0-9]|36[0-6])$")) {
        sendErrorResponse(response, "Invalid dayID. Must be a string between \"1\" and \"366\".");
        return;
      }

      StringBuilder jsonBody = new StringBuilder();
      try (BufferedReader reader = request.getReader()) {
        String line;
        while ((line = reader.readLine()) != null) {
          jsonBody.append(line);
        }
      }
      JsonObject jsonObject = gson.fromJson(jsonBody.toString(), JsonObject.class);
      if (!jsonObject.has("time") || !jsonObject.has("liftID") || jsonObject.get("time").isJsonNull() || jsonObject.get("liftID").isJsonNull()) {
        sendErrorResponse(response, "Missing required fields in request body: time, liftID");
        return;
      }

      int time = jsonObject.get("time").getAsInt();
      int liftID = jsonObject.get("liftID").getAsInt();

      if (time <= 0 || liftID <= 0) {
        sendErrorResponse(response, "Invalid values: time and liftID must be positive integers.");
        return;
      }

      JsonObject message = new JsonObject();
      message.addProperty("resortID", resortID);
      message.addProperty("seasonID", seasonID);
      message.addProperty("dayID", dayID);
      message.addProperty("skierID", skierID);
      message.addProperty("time", time);
      message.addProperty("liftID", liftID);


      // Borrow a channel from the pool and publish the message
      Channel channel = channelPool.take();
      sendMessageToQueue(channel, message.toString());

      // Return the channel to the pool
      channelPool.offer(channel);

      response.setStatus(HttpServletResponse.SC_CREATED);
      response.getWriter().write(gson.toJson(new SuccessResponse("Skier ride recorded")));

    } catch (NumberFormatException e){
      sendErrorResponse(response, "Invalid numeric parameter(s). resortID and skierID must be integers.");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void sendMessageToQueue(Channel channel,String message) {
    try {
      channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
      System.out.println("✅ Sent to RabbitMQ: " + message);
    } catch (Exception e) {
      System.err.println("❌ Failed to send message to RabbitMQ: " + e.getMessage());
    }
  }

  private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.getWriter().write(gson.toJson(new ErrorResponse(message)));
  }
  static class ErrorResponse {
    String message;
    ErrorResponse(String message) { this.message = message; }
  }

  static class SuccessResponse {
    String status = "success";
    String message;
    SuccessResponse(String message) { this.message = message; }
  }
}

