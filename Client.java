import java.io.*;
import java.net.*;

public class Client {
  public static void main(String[] args) throws Exception {
    String sentence;
    String modifiedSentence;
    BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
    Socket clientSocket = new Socket("localhost", 9876);
    DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
    BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

    while (true) {
      System.out.print("Enter a sentence: ");
      sentence = inFromUser.readLine();

      if(sentence.equalsIgnoreCase("fim")) {
        break;
      }

      outToServer.writeBytes(sentence + "\n");
      outToServer.flush();
      modifiedSentence = inFromServer.readLine();
      System.out.println("FROM SERVER: " + modifiedSentence);
    }
    clientSocket.close();
  }
}
