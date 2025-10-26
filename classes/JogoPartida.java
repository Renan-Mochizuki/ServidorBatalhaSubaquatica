package classes;

import java.util.*;

public class JogoPartida extends Partida {
  private String tokenPartida;
  private List<DispositivoProximidade> dispositivos;
  private List<Jogador> jogadores;

  public JogoPartida(Partida partida, String tokenPartida) {
    super(partida.getId());
    setAndamento(true);
    this.tokenPartida = tokenPartida;
    this.dispositivos = new ArrayList<>();
    this.jogadores = new ArrayList<>();
    // Não iremos copiar clientes diretamente, pois queremos transformá-los em
    // Jogadores, poderiamos criar usar a mesma lista clientes já existente, mas
    // teriamos que ficar convertendo para Jogador, então para facilitar criamos uma
    // lista nova de jogadores
    Random random = new Random();

    // Vamos percorrer cada cliente e instanciar um novo Jogador a partir dele
    Iterator<Cliente> iterator = partida.getClientes().iterator();
    while (iterator.hasNext()) {
      Cliente cl = iterator.next();
      // Variavel para a posição do jogador que vai ser aleatória
      int x1 = 0;
      int y1 = 0;
      // Variavel de controle do loop
      Boolean posicaoValida = false;

      // Código meio ineficiente para garantir que os jogadores não sejam posicionados
      // aleatóriamente um perto do outro de acordo com a constante
      // PROXIMIDADE_INICIAL_JOGADORES
      while (!posicaoValida) {
        posicaoValida = true;
        // Crie um x e y aleatórios
        x1 = random.nextInt(16);
        y1 = random.nextInt(16);
        // Vamos verificar para todos os jogadores já instanciados no jogo para
        // verificar se essas coordenadas geradas estão próximas de qualquer jogador
        Iterator<Cliente> iterator2 = this.getClientes().iterator();
        while (iterator2.hasNext()) {
          // Obtendo o jogador e suas coordenadas
          Jogador jogadorJaInstanciado = (Jogador) iterator2.next();
          int xJogador = jogadorJaInstanciado.getPosicao().getX();
          int yJogador = jogadorJaInstanciado.getPosicao().getY();
          // Calcule a distância entre as posições
          int distanciaX = Math.abs(x1 - xJogador);
          int distanciaY = Math.abs(y1 - yJogador);

          // Modo de detecção em formato de quadrado
          if (Constants.MODO_DETECCAO == 0) {
            if (distanciaX <= Constants.PROXIMIDADE_INICIAL_JOGADORES
                && distanciaY <= Constants.PROXIMIDADE_INICIAL_JOGADORES) {
              posicaoValida = false;
              break;
            }
          } else {
            // Modo de detecção em formato de losango
            int distanciaTotal = distanciaX + distanciaY;
            if (distanciaTotal <= Constants.PROXIMIDADE_INICIAL_JOGADORES) {
              posicaoValida = false;
              break;
            }
          }
        }
      }

      Jogador j = new Jogador(cl.getNome(), cl.getToken(), x1, y1, Constants.NUMERO_MAX_DISPOSITIVOS_JOGADOR);
      this.jogadores.add(j);
    }
  }

  public Boolean validarToken(String token) {
    return this.tokenPartida.equals(token);
  }

  public List<Jogador> getJogadores() {
    return this.jogadores;
  }

}
