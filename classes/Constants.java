package classes;

// Declarando uma classe Constants para armazenar constantes do jogo
// (Java não suporta declarações de constantes fora de classes)
public class Constants {
  public static final int PORTA_SERVIDOR = 9876;
  public static final int TAMANHO_TABULEIRO = 16;
  public static final int NUMERO_JOGADORES = 2;
  public static final int NUMERO_MAX_DISPOSITIVOS_JOGADOR = 4;
  public static final int NUMERO_PARTIDAS = 5;
  public static final int TEMPO_TURNO = 15; // em segundos
  public static final boolean KEEPALIVE = true; // Ativa ou desativa o keepalive

  // Quanto até um cliente ser desconectado pelo servidor
  public static final int TEMPO_KEEPALIVE = 240; // em segundos

  // Proximidade inicial dos jogadores no ínicio da partida (gerado aleatoriamente
  // mas seguindo essa restrição)
  public static final int PROXIMIDADE_INICIAL_JOGADORES = 7;

  // Se verdadeiro, jogadores em partidas em andamento não receberam mensagens
  // globais
  public static final boolean CHAT_GLOBAL_SOMENTE_LOBBY = true;

  // Modo de cálculo de distância:
  // 0 para formato de quadrado, 1 para formato de losango
  // Distância diz respeito a distância máxima permitida para o jogador realizar a
  // ação e Alcance diz respeito ao alcance máximo de um ataque ou dispositivo
  public static final int MODO_DISTANCIA_MOVIMENTO = 1;
  public static final int MODO_DISTANCIA_ATAQUE = 1;
  public static final int MODO_DISTANCIA_DISPOSITIVO_PROXIMIDADE = 0;
  public static final int MODO_ALCANCE_ATAQUE = 1;
  public static final int MODO_ALCANCE_DISPOSITIVO_PROXIMIDADE = 1;
  public static final int DISTANCIA_MOVIMENTO = 2;
  public static final int DISTANCIA_ATAQUE = 4;
  public static final int DISTANCIA_DISPOSITIVO_PROXIMIDADE = 1;
  public static final int ALCANCE_ATAQUE = 2;
  public static final int ALCANCE_DISPOSITIVO_PROXIMIDADE = 3;
  public static final boolean IMPRIMIRPARTIDA = false;
  public static final String SEPARADOR = "|";
  public static final String SEPARADORCLIENTE = " "; // "\\|"
  public static final String SEPARADORATRIBUTO = ",";
  public static final String SEPARADORITEM = ";"; // Separa itens em listas enviadas ao cliente
  // Nome das mensagens enviadas pelo servidor (resposta direta do cliente)
  public static final String TIPOCADASTRAR = "CADASTRAR";
  public static final String TIPOLISTARPARTIDAS = "LISTARPARTIDAS";
  public static final String TIPOLISTARJOGADORES = "LISTARJOGADORES";
  public static final String TIPOENTRARPARTIDA = "ENTRARPARTIDA";
  public static final String TIPODESAFIAR = "DESAFIAR";
  public static final String TIPOACEITARDESAFIO = "ACEITARDESAFIO";
  public static final String TIPORECUSARDESAFIO = "RECUSARDESAFIO";
  public static final String TIPOCHATGLOBAL = "CHATGLOBAL";
  public static final String TIPOCHATPARTIDA = "CHATPARTIDA";
  public static final String TIPOCHATJOGADOR = "CHATJOGADOR";
  public static final String TIPOPRONTOPARTIDA = "PRONTOPARTIDA";
  public static final String TIPOMOVER = "MOVER";
  public static final String TIPOATACAR = "ATACAR";
  public static final String TIPOSONAR = "SONAR";
  public static final String TIPOPASSAR = "PASSAR";
  public static final String TIPOSAIRPARTIDA = "SAIRPARTIDA";
  public static final String TIPOSAIR = "SAIR";
  public static final String TIPOKEEPALIVE = "KEEPALIVE";
  // Nome das mensagens enviadas pelo servidor (adicionais do servidor)
  public static final String TIPODESCONECTADO = "DESCONECTADO";
  public static final String TIPORESERVADOPARTIDA = "RESERVADOPARTIDA";
  public static final String TIPODETECTADO = "DETECTADO";
  public static final String TIPOINICIOPARTIDA = "INICIOPARTIDA";
  public static final String TIPOACERTO = "ACERTO";
  public static final String TIPOMORTE = "MORTE";
  public static final String TIPOVITORIA = "VITORIA";
  public static final String TIPOFIMPARTIDA = "FIMPARTIDA";
  public static final String TIPOTURNO = "TURNO";
  public static final String TIPOTURNOEXPIROU = "TURNO";
  public static final String TIPOERRO = "ERRO";
}
