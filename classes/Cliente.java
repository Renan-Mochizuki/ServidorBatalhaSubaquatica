package classes;

import java.net.Socket;

public class Cliente {
  private String nome;
  private String token;
  private int idPartida; // ID da partida que o cliente está participando
  private String jogadorDesafiado; // Nome do jogador que este cliente desafiou
  private Socket connectionSocket;

  public Cliente(String nome, String token, Socket connectionSocket) {
    this.nome = nome;
    this.token = token;
    this.idPartida = -1;
    this.jogadorDesafiado = null;
    this.connectionSocket = connectionSocket;
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

  public Socket getConnectionSocket() {
    return this.connectionSocket;
  }

  public void setConnectionSocket(Socket socket) {
    this.connectionSocket = socket;
  }

  public String getJogadorDesafiado() {
    return this.jogadorDesafiado;
  }

  public void setJogadorDesafiado(String jogadorDesafiado) {
    this.jogadorDesafiado = jogadorDesafiado;
  }

  // Esse método vai ser <default> para ser acessado pela classe filha (Jogador)
  String getToken() {
    return this.token;
  }
}