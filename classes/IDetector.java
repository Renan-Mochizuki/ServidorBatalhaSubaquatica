package classes;

// Interface para poder generalizar a função de detectar jogadores do JogoPartida, também poderia ser uma classe abstrata já que míssil e dispositivo possuem os mesmos atributos e métodos
public interface IDetector {
  Jogador getJogadorDono();

  Posicao getPosicao();

  int getAlcance();

  int getNum();

  Boolean detectarJogador(Jogador jogador);
}
