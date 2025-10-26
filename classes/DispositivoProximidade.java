package classes;

public class DispositivoProximidade {
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

    return this.posicao.distanciaPermitida(jogador.getPosicao().getX(), jogador.getPosicao().getY(), this.alcance,
        Constants.MODO_DETECCAO);
  }
}
