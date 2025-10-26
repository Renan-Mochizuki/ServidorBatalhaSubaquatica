import java.io.*;
import java.net.*;
import java.util.*;

import javax.xml.crypto.Data;

// Declarando uma classe Constants para armazenar constantes do jogo
// (Java não suporta declarações de constantes fora de classes)
class Constants {
  static final int PORTA_SERVIDOR = 9876;
  static final int NUMERO_PARTIDAS = 5;
  static final int TAMANHO_TABULEIRO = 16;
  static final int MODO_DETECCAO = 1; // 0 para detecção em formato quadrado, 1 para formato de losango
  static final int ALCANCE_DETECCAO = 2;
  static final int NUMERO_JOGADORES = 2;
  static final int NUMERO_MAX_DISPOSITIVOS_JOGADOR = 4;
}

class Cliente {
  private String nome;
  private String token;
  private int idPartida; // ID da partida que o cliente está participando

  public Cliente(String nome, String token) {
    this.nome = nome;
    this.token = token;
    this.idPartida = -1;
  }

  public String getNome() {
    return this.nome;
  }

  public Boolean validarToken(String token) {
    return this.token.equals(token);
  }

  public int getIdPartida() {
    return this.idPartida;
  }

  public void setIdPartida(int idPartida) {
    this.idPartida = idPartida;
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

// Classe da partida
class Partida {
  private int id;
  private List<Cliente> clientes;
  private List<DispositivoProximidade> dispositivos;
  private Boolean andamento; // true para partida em andamento

  public Partida(int id) {
    this.id = id;
    this.clientes = new ArrayList<>();
    this.dispositivos = new ArrayList<>();
    this.andamento = false;
  }

  public Boolean adicionarCliente(Cliente cliente) {
    if (this.clientes.size() >= Constants.NUMERO_JOGADORES) {
      return false;
    }
    this.clientes.add(cliente);
    return true;
  }

  public Boolean removerCliente(Cliente cliente) {
    return this.clientes.remove(cliente);
  }

  public int getId() {
    return this.id;
  }

  public Boolean getAndamento() {
    return this.andamento;
  }

  public String getInfo() {
    return "id:" + this.id + ",andamento:" + this.andamento + ",numjogadores:" + this.clientes.size();
  }

  public Boolean partidaLotada() {
    return this.clientes.size() >= Constants.NUMERO_JOGADORES;
  }

  public Boolean iniciarPartida() {
    // Se ainda não houver clientes suficientes ou já está em andamento, não inicia
    // a partida
    if (this.clientes.size() < Constants.NUMERO_JOGADORES || this.andamento == true) {
      return false;
    }
    this.andamento = true;
    return true;
  }

  public List<Cliente> getClientes() {
    return this.clientes;
  }
}

public class Server {
  // Lista para armazenar os clientes conectados
  // Usando um HashMap para facilitar o acesso por nome ou ID
  // Só permite um cliente por nome
  // ChatGPT sugeriu esse HashMap
  static Map<String, Cliente> listaCliente = new HashMap<>();
  // Declarando lista de partidas
  static List<Partida> partidas = new ArrayList<>();

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

    // Método que recebe um indice para verificar se foi informado algo para o campo
    // daquele indice
    // Se não foi informado, envia uma mensagem dizendo que nomeCampo não foi
    // informado ao cliente
    static Boolean verificarCampo(String nomeCampo, int indice, String[] splitedSentence, DataOutputStream outToClient)
        throws IOException {
      if (splitedSentence.length < indice + 1 || splitedSentence[indice].isEmpty()) {
        outToClient.writeBytes("0|" + nomeCampo + " nao informado|\n");
        return false;
      }
      return true;
    }

    // Método para validar se o cliente existe e se o token está correto
    static Boolean validarCliente(Cliente cliente, String tokenCliente, DataOutputStream outToClient)
        throws IOException {
      if (cliente == null) {
        outToClient.writeBytes("0|Cliente nao encontrado|\n");
        return false;
      }
      if (!cliente.validarToken(tokenCliente)) {
        outToClient.writeBytes("0|Token invalido|\n");
        return false;
      }
      return true;
    }

    // Método para tentar iniciar a partida, toda vez que um cliente se conecta a
    // partida ele tentará iniciar a partida, se chama o método iniciarPartida da
    // Partida que apenas muda o estado de andamento, se conseguir, devemos
    // instanciar agora jogadores
    static Boolean tentarIniciarPartida(Partida partida) {
      if (partida.iniciarPartida()) {
        System.out.println("Partida " + partida.getId() + " iniciada!");
        return true;
      }
      return false;
    }

    // Método para encontrar uma partida pelo id, percorrendo a lista de partidas e
    // comparando o id
    static Partida encontrarPartida(int idPartida) {
      Partida partidaEscolhida = null;
      Iterator<Partida> it = partidas.iterator();
      while (it.hasNext()) {
        Partida partida = it.next();
        if (partida.getId() == idPartida) {
          partidaEscolhida = partida;
          break;
        }
      }
      return partidaEscolhida;
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
                if (!verificarCampo("nome", 1, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];

                // Criando um token para o cliente, o cliente receberá esse token e deve
                // utilizá-lo para garantir sua identidade, poderíamos gerar um hash a partir do
                // nome e um salt mas um randomUUID já garante a unicidade necessária
                // String tokenCliente = UUID.randomUUID().toString();
                String tokenCliente = "a";
                System.out.println("Cadastrando cliente: " + nomeCliente + " com token: " + tokenCliente);

                Cliente novoCliente = new Cliente(nomeCliente, tokenCliente);

                // Tenta adicionar o cliente na lista, esse método retorna o item anterior
                // daquela chave (nomecliente), ou seja, se não existir item com aquela chave,
                // retorna null
                // ChatGPT sugeriu o HashMap do listaCliente e esse método putIfAbsent
                if (listaCliente.putIfAbsent(nomeCliente, novoCliente) != null) {
                  outToClient.writeBytes("0|Um cliente com esse nome ja existe|\n");
                } else {
                  outToClient.writeBytes("1|Cadastrado com sucesso|" + tokenCliente + "\n");
                }
                break;
              }
              // LISTARPARTIDAS
              case "LISTARPARTIDAS": {
                String partidasServidor = "";
                Iterator<Partida> it = Server.partidas.iterator();
                while (it.hasNext()) {
                  Partida partida = it.next();
                  partidasServidor += partida.getInfo();
                  partidasServidor += ";";
                }
                outToClient.writeBytes("1|Partidas ativas|" + partidasServidor + "\n");
                break;
              }
              // LISTARJOGADORES
              case "LISTARJOGADORES": {
                String clientesServidor = "";
                Iterator<Cliente> it = listaCliente.values().iterator();
                while (it.hasNext()) {
                  Cliente cliente = it.next();
                  clientesServidor += cliente.getNome();
                  clientesServidor += ";";
                }
                outToClient.writeBytes("1|Jogadores conectados|" + clientesServidor + "\n");
                break;
              }
              // ENTRARPARTIDA <nome> <token> <idPartida>
              case "ENTRARPARTIDA": {
                if (!verificarCampo("nome", 1, splitedSentence, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, outToClient) ||
                    !verificarCampo("idPartida", 3, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                String idPartidaStr = splitedSentence[3];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient))
                  break;

                // Convertendo idPartida para inteiro
                int idPartida;
                try {
                  idPartida = Integer.parseInt(idPartidaStr);
                } catch (NumberFormatException e) {
                  outToClient.writeBytes("0|ID da partida invalido|\n");
                  break;
                }

                // Percorre todas partidas e verifica se o id
                Partida partidaEscolhida = encontrarPartida(idPartida);

                // Partida não encontrada
                if (partidaEscolhida == null) {
                  outToClient.writeBytes("0|Partida inexistente|\n");
                  break;
                }

                // Se a partida estiver lotada, não pode entrar
                if (partidaEscolhida.partidaLotada() == true) {
                  outToClient.writeBytes("0|Partida lotada|\n");
                  break;
                }

                // Pega o id da partida armazenada em cliente
                int idPartidaCliente = cliente.getIdPartida();

                // Se o cliente já está na partida escolhida, não faz nada
                if(idPartidaCliente == idPartida) {
                  outToClient.writeBytes("1|Cliente ja esta nessa partida|\n");
                  break;
                }

                // Se o cliente já estava em alguma partida
                if (idPartidaCliente != -1) {
                  Partida partidaAnterior = encontrarPartida(idPartidaCliente);
                  if (partidaAnterior != null) {
                    // Remove o cliente da partida anterior
                    partidaAnterior.removerCliente(cliente);
                    System.out.println("Cliente " + nomeCliente + " saiu da partida " + idPartidaCliente);
                  }
                }

                // Define a partida do cliente
                cliente.setIdPartida(idPartida);
                partidaEscolhida.adicionarCliente(cliente);

                System.out.println("Cliente " + nomeCliente + " entrou na partida " + idPartida);

                tentarIniciarPartida(partidaEscolhida);

                outToClient.writeBytes("1|Entrou na partida com sucesso|\n");
                break;
              }
              // KEEPALIVE <nome> <token>
              case "KEEPALIVE": {

              }
              // SAIR <nome> <token>
              case "SAIR": {
                if (!verificarCampo("nome", 1, splitedSentence, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient))
                  break;

                System.out.println("Removendo cliente: " + nomeCliente + " com token: " + tokenCliente);

                listaCliente.remove(nomeCliente);
                outToClient.writeBytes("1|Cliente desconectado com sucesso|\n");

                // Sai do loop para fechar o socket
                sair = true;
                break;
              }
              default:
                outToClient.writeBytes("0|Comando desconhecido|\n");
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
      // Loop para criar partidas
      for (int i = 0; i < Constants.NUMERO_PARTIDAS; i++) {
        partidas.add(new Partida(i + 1));
      }

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