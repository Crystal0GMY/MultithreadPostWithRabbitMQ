package server;

import com.google.gson.JsonObject;
import java.io.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import com.google.gson.Gson;

// the servlet without the use of RabbitMQ, simpler version for Assignment 1
@WebServlet(name = "server.SkiersServlet", urlPatterns = "/oldskiers/*")
public class SkiersServlet extends HttpServlet {
  private final Gson gson = new Gson();
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
      System.out.println("time" + time);
      System.out.println("liftID" + liftID);

      if (time <= 0 || liftID <= 0) {
        sendErrorResponse(response, "Invalid values: time and liftID must be positive integers.");
        return;
      }

      response.setStatus(HttpServletResponse.SC_CREATED);
      response.getWriter().write(gson.toJson(new SuccessResponse("Skier ride recorded")));

    } catch (NumberFormatException e){
      sendErrorResponse(response, "Invalid numeric parameter(s). resortID and skierID must be integers.");
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
