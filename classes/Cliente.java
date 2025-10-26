package classes;

public class Cliente {
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