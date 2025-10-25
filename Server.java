import java.io.*;
import java.net.*;
import java.util.*;

// Declarando uma classe Constants para armazenar constantes do jogo
// (Java não suporta declarações de constantes fora de classes)
class Constants {
  static final int PORTA_SERVIDOR = 9876;
  static final int TAMANHO_TABULEIRO = 16;
  static final int MODO_DETECCAO = 1; // 0 para detecção em formato quadrado, 1 para formato de losango
  static final int ALCANCE_DETECCAO = 2;
  static final int NUMERO_JOGADORES = 2;
  static final int NUMERO_MAX_DISPOSITIVOS_JOGADOR = 4;
}

class Cliente {
  private String nome;
  private String token;

  public Cliente(String nome, String token) {
    this.nome = nome;
    this.token = token;
  }

  public String getNome() {
    return this.nome;
  }

  public Boolean validarToken(String token) {
    return this.token.equals(token);
  }
}

// Classe para representar uma posição no tabuleiro
class Posicao {
  private int x;
  private int y;

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

  Boolean setX(int x) {
    // Verifica se o valor está dentro dos limites do tabuleiro
    if (x < 0 || x >= Constants.TAMANHO_TABULEIRO) {
      return false;
    }
    this.x = x;
    return true;
  }

  Boolean setY(int y) {
    // Verifica se o valor está dentro dos limites do tabuleiro
    if (y < 0 || y >= Constants.TAMANHO_TABULEIRO) {
      return false;
    }
    this.y = y;
    return true;
  }
}

class Jogador extends Cliente {
  private Posicao posicao;
  private int numDispositivos;
  private int numMaxDispositivos;

  public Jogador(String nome, String token, int x, int y, int numMaxDispositivos) {
    super(nome, token);
    this.posicao = new Posicao(x, y);
    this.numDispositivos = 0;
    this.numMaxDispositivos = numMaxDispositivos;
  }

  public Posicao getPosicao() {
    return this.posicao;
  }

  // Método para mover o jogador de acordo com um deslocamento em X e Y
  public Boolean mover(int deslocamentoX, int deslocamentoY) {
    int novoX = posicao.getX() + deslocamentoX;
    int novoY = posicao.getY() + deslocamentoY;
    if (novoX < 0 || novoX >= Constants.TAMANHO_TABULEIRO || novoY < 0 || novoY >= Constants.TAMANHO_TABULEIRO) {
      return false;
    }
    this.posicao.setX(novoX);
    this.posicao.setY(novoY);
    return true;
  }

  // Método para adicionar dispositivo
  public Boolean adicionarDispositivo() {
    if (this.numDispositivos < this.numMaxDispositivos) {
      this.numDispositivos++;
      return true;
    }
    return false;
  }

  // Método para remover dispositivo
  public Boolean removerDispositivo() {
    if (this.numDispositivos > 0) {
      this.numDispositivos--;
      return true;
    }
    return false;
  }
}

class DispositivoProximidade {
  private Posicao posicao;
  private int alcance;
  private Jogador jogadorDono;

  public DispositivoProximidade(int x, int y, int alcance, Jogador jogadorDono) {
    this.posicao = new Posicao(x, y);
    this.alcance = alcance;
    this.jogadorDono = jogadorDono;
  }

  public Posicao getPosicao() {
    return this.posicao;
  }

  public int getAlcance() {
    return this.alcance;
  }

  // Método que recebe um jogador e calcula a distância, se
  // estiver dentro do alcance determinado retorna true
  public Boolean detectarJogador(Jogador jogador) {
    // Não detecta o jogador dono do dispositivo
    if (jogador == this.jogadorDono) {
      return false;
    }

    int distanciaX = Math.abs(this.posicao.getX() - jogador.getPosicao().getX());
    int distanciaY = Math.abs(this.posicao.getY() - jogador.getPosicao().getY());

    // Modo de detecção em formato de quadrado
    if (Constants.MODO_DETECCAO == 0) {
      return distanciaX <= alcance && distanciaY <= alcance;
    }
    // Modo de detecção em formato de losango
    int distanciaTotal = distanciaX + distanciaY;
    return distanciaTotal <= alcance;
  }
}

public class Server {
  // Lista para armazenar os clientes conectados
  // Usando um HashMap para facilitar o acesso por nome ou ID
  // Só permite um cliente por nome
  // ChatGPT sugeriu esse HashMap
  static Map<String, Cliente> listaCliente = new HashMap<>();

  // Método temporário para imprimir o tabuleiro
  static synchronized void TempImprimir(Jogador jogador1, DispositivoProximidade dispositivo1) {
    for (int i = 0; i < Constants.TAMANHO_TABULEIRO; i++) {
      System.out.print(" " + i);
    }
    System.out.println("");
    for (int i = 0; i < Constants.TAMANHO_TABULEIRO; i++) {
      System.out.print(i + " ");
      for (int j = 0; j < Constants.TAMANHO_TABULEIRO; j++) {
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

  // Declarando thread para lidar com cada cliente conectado
  // Como lidar com threads visto em:
  // https://www.geeksforgeeks.org/java/java-multithreading-tutorial
  static class ClientHandler extends Thread {
    private Socket connectionSocket;

    // Salvando o socket de conexão do cliente para podermos utilizar no run()
    public ClientHandler(Socket socket) {
      this.connectionSocket = socket;
    }

    public void run() {
      try {
        // Código tirado dos slides
        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
        String sentence;
        // Variável para controlar o loop, manter o socket aberto até o cliente pedir
        // para sair
        Boolean sair = false;

        while (!sair) {
          while ((sentence = inFromClient.readLine()) != null) {
            // Separando a sentença nas palavras
            String splitedSentence[] = sentence.split(" ");
            // Switch para os comandos enviados pelo cliente
            switch (splitedSentence[0].toUpperCase()) {
              // CADASTRO <nomeCliente>
              case "CADASTRO": {
                // Verifica se foi passado um nome para cliente
                if (splitedSentence.length < 2 || splitedSentence[1].isEmpty()) {
                  outToClient.writeBytes("0 Nome invalido\n");
                  break;
                }

                String nomeCliente = splitedSentence[1];

                // Criando um token para o cliente, o cliente receberá esse token e deve
                // utilizá-lo para garantir sua identidade, poderiamos gerar um hash a partir do
                // nome e um salt mas um randomUUID já garante a unicidade necessária
                String tokenCliente = UUID.randomUUID().toString();
                System.out.println("Cadastrando cliente: " + nomeCliente + " com token: " + tokenCliente);

                Cliente novoCliente = new Cliente(nomeCliente, tokenCliente);

                // Tenta adicionar o cliente na lista, esse método retorna o item anterior
                // daquela chave (nomecliente), ou seja, se não existir item com aquela chave,
                // retorna null
                // ChatGPT sugeriu o HashMap do listaCliente e esse método putIfAbsent
                if (listaCliente.putIfAbsent(nomeCliente, novoCliente) != null) {
                  outToClient.writeBytes("0 Um cliente com esse nome ja existe\n");
                } else {
                  outToClient.writeBytes("1 Cadastrado com sucesso. Token: " + tokenCliente + "\n");
                }
                System.out.println("Clientes cadastrados: " + listaCliente.keySet());
                break;
              }
              case "KEEPALIVE": {

              }
              case "SAIR": {
                // Remove o cliente da lista
                if (splitedSentence.length < 2 || splitedSentence[1].isEmpty()) {
                  outToClient.writeBytes("0 Nome invalido\n");
                  break;
                }
                sair = true;
                break;
              }
              default:
                outToClient.writeBytes("0 Comando desconhecido\n");
                break;
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        try {
          connectionSocket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    // Declarando o socket do servidor
    // Código tirado dos slides
    ServerSocket welcomeSocket = new ServerSocket(Constants.PORTA_SERVIDOR);
    System.out.println("Servidor iniciado na porta " + Constants.PORTA_SERVIDOR);

    try {
      while (true) {
        // Aceite todas as conexões de entrada
        Socket connectionSocket = welcomeSocket.accept();

        System.out.println("Cliente conectado: " + connectionSocket.getInetAddress().getHostAddress() + ":"
            + connectionSocket.getPort());

        // Declarando um ClientHandler (thread) para cada conexão
        ClientHandler clientHandler = new ClientHandler(connectionSocket);
        clientHandler.start();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      welcomeSocket.close();
    }
  }
}