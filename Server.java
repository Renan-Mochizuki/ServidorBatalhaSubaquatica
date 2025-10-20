import java.io.*;
import java.net.*;

class Posicao {
  int x;
  int y;

  Posicao(int x, int y) {
    this.x = x;
    this.y = y;
  }

  int getX() {
    return this.x;
  }

  int getY() {
    return this.y;
  }

  Boolean detectar(Posicao posicao, int alcance) {
    int distanciaX = Math.abs(this.x - posicao.x);
    int distanciaY = Math.abs(this.y - posicao.y);
    int distanciaTotal = distanciaX + distanciaY;
    return alcance >= distanciaTotal;
  }
}

class Jogador {
  String nome;
  int numDispositivo;
  Posicao posicao;

  Jogador(String nome, int x, int y) {
    this.nome = nome;
    this.numDispositivo = 3;
    this.posicao = new Posicao(x, y);
  }

  String getNome() {
    return this.nome;
  }

  Posicao getPosicao() {
    return this.posicao;
  }

  Boolean mover(int movimentoX, int movimentoY) {
    int novoX = this.posicao.x + movimentoX;
    int novoY = this.posicao.y + movimentoY;
    if (novoX < 0 || novoX >= 10 || novoY < 0 || novoY >= 10) {
      return false;
    }
    this.posicao.x += movimentoX;
    this.posicao.y += movimentoY;
    return true;
  }
}

class Dispositivo {
  Posicao posicao;
  int alcance;

  Dispositivo(int x, int y, int alcance) {
    this.posicao = new Posicao(x, y);
    this.alcance = alcance;
  }

  Posicao getPosicao() {
    return this.posicao;
  }

  int getAlcance() {
    return this.alcance;
  }

  Boolean detectar(Posicao posicao) {
    return this.posicao.detectar(posicao, this.alcance);
  }
}

public class Server {

  static Jogador jogador1;
  static Dispositivo dispositivo1;

  // Declarando metódo como synchronized para evitar condições de corrida
  static synchronized void Imprimir(Jogador jogador1, Dispositivo dispositivo1) {
    System.out.println("  0 1 2 3 4 5 6 7 8 9");
    for (int i = 0; i < 10; i++) {
      System.out.print(i + " ");
      for (int j = 0; j < 10; j++) {
        if (j == dispositivo1.getPosicao().getX() && i == dispositivo1.getPosicao().getY()) {
          System.out.print("X ");
        } else if (j == jogador1.getPosicao().getX() && i == jogador1.getPosicao().getY()) {
          System.out.print("J ");
        } else {
          System.out.print(". ");
        }
      }
      System.out.println("");
    }
  }

  static class ClientThread extends Thread {
    private Socket connectionSocket;

    public ClientThread(Socket socket) {
      this.connectionSocket = socket;
    }

    public void run() {
      try {
        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
        String sentence;

        while ((sentence = inFromClient.readLine()) != null) {
          // Declara a classe Server como uma região crítica dessa parte do código
          synchronized (Server.class) {
            System.out.println("RECEIVED: " + sentence);
            int[] ataque = new int[] { -1, -1 };
            if (sentence.contains(" ")) {
              String[] partes = sentence.split(" ");
              ataque[0] = Integer.parseInt(partes[0]);
              ataque[1] = Integer.parseInt(partes[1]);
              dispositivo1 = new Dispositivo(ataque[1], ataque[0], 2);
            }
            switch (sentence) {
              case "w":
                jogador1.mover(0, -1);
                break;
              case "a":
                jogador1.mover(-1, 0);
                break;
              case "s":
                jogador1.mover(0, 1);
                break;
              case "d":
                jogador1.mover(1, 0);
                break;
            }
            Imprimir(jogador1, dispositivo1);
            if (dispositivo1.detectar(jogador1.getPosicao())) {
              System.out.println("Jogador detectado pelo dispositivo!");
            }
          }
        }
        connectionSocket.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) throws Exception {
    ServerSocket welcomeSocket = new ServerSocket(9876);

    jogador1 = new Jogador("Alice", 0, 0);
    dispositivo1 = new Dispositivo(-10, -10, 2);
    Imprimir(jogador1, dispositivo1);

    System.out.println("Servidor iniciado na porta 9876...");

    while (true) {
      Socket connectionSocket = welcomeSocket.accept();
      ClientThread ClientThread = new ClientThread(connectionSocket);
      ClientThread.start();
    }
  }
}
