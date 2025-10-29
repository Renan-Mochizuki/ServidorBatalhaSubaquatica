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
  public static final int TEMPO_KEEPALIVE = 120; // em segundos
  // Proximidade inicial dos jogadores no ínicio da partida (gerado aleatoriamente
  // mas seguindo essa restrição)
  public static final int PROXIMIDADE_INICIAL_JOGADORES = 7;
  // Se verdadeiro, jogadores em partidas em andamento não receberam mensagens
  // globais
  public static final boolean CHAT_GLOBAL_SOMENTE_LOBBY = true;
  // Modo: 0 para calculo de distância em formato quadrado, 1 para formato de
  // losango
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

}
