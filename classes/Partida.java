package classes;

import java.util.*;

public class Partida {
  private int id;
  private List<Cliente> clientes;
  private boolean andamento; // true para partida em andamento
  private int numMaxClientes;

  public Partida(int id, int numMaxClientes) {
    this.id = id;
    this.clientes = new ArrayList<>();
    this.andamento = false;
    this.numMaxClientes = numMaxClientes;
  }

  public boolean adicionarCliente(Cliente cliente) {
    if (this.clientes.size() >= this.numMaxClientes) {
      return false;
    }
    this.clientes.add(cliente);
    return true;
  }

  public boolean removerCliente(Cliente cliente) {
    return this.clientes.remove(cliente);
  }

  public int getId() {
    return this.id;
  }

  public boolean getAndamento() {
    return this.andamento;
  }

  public String getInfo() {
    return "id:" + this.id + Constants.SEPARADORATRIBUTO + "andamento:" + this.andamento + Constants.SEPARADORATRIBUTO
        + "numjogadores:" + this.clientes.size() + Constants.SEPARADORATRIBUTO + "maxjogadores:" + this.numMaxClientes;
  }

  public boolean partidaLotada() {
    return this.clientes.size() >= this.numMaxClientes;
  }

  public boolean iniciarPartida() {
    // Se ainda não houver clientes suficientes ou já está em andamento, não inicia
    // a partida
    if (this.clientes.size() < this.numMaxClientes || this.andamento == true) {
      return false;
    }
    this.andamento = true;
    return true;
  }

  public List<Cliente> getClientes() {
    return this.clientes;
  }

  // Método default para ser acessado pela classe filha (JogoPartida)
  void setAndamento(boolean andamento) {
    this.andamento = andamento;
  }

  void limparClientes() {
    this.clientes.clear();
  }
}
