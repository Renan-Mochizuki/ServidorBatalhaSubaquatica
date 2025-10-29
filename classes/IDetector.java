package classes;

// Interface para poder generalizar a função de detectar jogadores do JogoPartida, também poderia ser uma classe abstrata já que míssil e dispositivo possuem os mesmos atributos e métodos
public interface IDetector {
  Jogador getJogadorDono();

  Posicao getPosicao();

  int getAlcance();

  int getNum();

  int getModoAlcance();

  // Método que recebe um jogador e calcula a distância, se
  // estiver dentro do alcance determinado retorna true
  default Boolean detectarJogador(Jogador jogador) {
    // Não detecta o jogador dono do dispositivo
    if (jogador == this.getJogadorDono()) {
      return false;
    }

    return this.getPosicao().distanciaPermitida(jogador.getPosicao().getX(), jogador.getPosicao().getY(), this.getAlcance(),
        this.getModoAlcance());
  }
}
