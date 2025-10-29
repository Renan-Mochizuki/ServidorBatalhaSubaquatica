package classes;

import java.util.*;

public class Partida {
  // Esses atributos são default para poderem ser acessados pela classe filha
  // (JogoPartida)
  private int id;
  private List<Cliente> clientes;
  private Boolean andamento; // true para partida em andamento

  public Partida(int id) {
    this.id = id;
    this.clientes = new ArrayList<>();
    this.andamento = false;
  }

  public Boolean adicionarCliente(Cliente cliente) {
    if (this.clientes.size() >= Constants.NUMERO_JOGADORES) {
      return false;
    }
    this.clientes.add(cliente);
    return true;
  }

  public Boolean removerCliente(Cliente cliente) {
    return this.clientes.remove(cliente);
  }

  public int getId() {
    return this.id;
  }

  public Boolean getAndamento() {
    return this.andamento;
  }

  public String getInfo() {
    return "id:" + this.id + ",andamento:" + this.andamento + ",numjogadores:" + this.clientes.size();
  }

  public Boolean partidaLotada() {
    return this.clientes.size() >= Constants.NUMERO_JOGADORES;
  }

  public Boolean iniciarPartida() {
    // Se ainda não houver clientes suficientes ou já está em andamento, não inicia
    // a partida
    if (this.clientes.size() < Constants.NUMERO_JOGADORES || this.andamento == true) {
      return false;
    }
    this.andamento = true;
    return true;
  }

  public List<Cliente> getClientes() {
    return this.clientes;
  }

  // Método default para ser acessado pela classe filha (JogoPartida)
  void setAndamento(Boolean andamento) {
    this.andamento = andamento;
  }

  void limparClientes() {
    this.clientes.clear();
  }
}
