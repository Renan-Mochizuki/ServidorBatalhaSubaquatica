package classes;

import java.util.*;

public class JogoPartida extends Partida {
  private List<DispositivoProximidade> dispositivos;
  private List<Jogador> jogadores;
  String jogadorTurno;

  public JogoPartida(int id, List<Cliente> clientes) {
    super(id);
    setAndamento(true);
    this.dispositivos = new ArrayList<>();
    this.jogadores = new ArrayList<>();
    this.jogadorTurno = null;
    // Não iremos copiar clientes diretamente, pois queremos transformá-los em
    // Jogadores, poderiamos criar usar a mesma lista clientes já existente, mas
    // teriamos que ficar convertendo para Jogador, então para facilitar criamos uma
    // lista nova de jogadores
    Random random = new Random();

    // Vamos percorrer cada cliente e instanciar um novo Jogador a partir dele
    Iterator<Cliente> iterator = clientes.iterator();
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
        Iterator<Jogador> iterator2 = this.getJogadores().iterator();
        while (iterator2.hasNext()) {
          // Obtendo o jogador e suas coordenadas
          Jogador jogadorJaInstanciado = iterator2.next();
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
      Jogador j = new Jogador(cl.getNome(), cl.getToken(), cl.getConnectionSocket(), x1, y1,
          Constants.NUMERO_MAX_DISPOSITIVOS_JOGADOR);
      this.jogadores.add(j);
    }
  }

  public int getId() {
    return super.getId();
  }

  public List<Jogador> getJogadores() {
    return this.jogadores;
  }

  public Jogador buscarJogadorPorNome(String nome) {
    Iterator<Jogador> iterator = this.jogadores.iterator();
    while (iterator.hasNext()) {
      Jogador j = iterator.next();
      if (j.getNome().equals(nome)) {
        return j;
      }
    }
    return null;
  }

  public String getJogadorTurno() {
    return jogadorTurno;
  }

  public void setJogadorTurno(String jogadorTurno) {
    this.jogadorTurno = jogadorTurno;
  }

  public String proximoTurno() {
    if (jogadores.isEmpty())
      return null;

    // Verifica se não há turno definido ainda
    if (jogadorTurno == null) {
      jogadorTurno = jogadores.get(0).getNome();
      return jogadorTurno;
    }

    // Percorre a lista de jogadores para encontrar o jogador com o turno atual e
    // avança para o próximo jogador da lista de jogadores dessa partida
    for (int i = 0; i < jogadores.size(); i++) {
      if (jogadores.get(i).getNome().equals(jogadorTurno)) {
        // Garantir que volta ao início da lista se estiver no último jogador
        int proximoIndice = (i + 1) % jogadores.size();
        jogadorTurno = jogadores.get(proximoIndice).getNome();
        return jogadorTurno;
      }
    }
    return null;
  }

  public Boolean movimento(String nomeJogador, int deslocamentoX, int deslocamentoY) {
    Jogador jogador = buscarJogadorPorNome(nomeJogador);
    if (jogador != null) {
      return jogador.mover(deslocamentoX, deslocamentoY);
    }
    return false;
  }

  public void finalizarPartida() {
    setAndamento(false);
  }

  public void imprimirPartida() {
    
  }

}
