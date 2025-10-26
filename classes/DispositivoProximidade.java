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
