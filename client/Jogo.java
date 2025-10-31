package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.io.*;
import java.net.*;
import java.util.function.Consumer;

/**
 * Janela principal do jogo.
 * Tela 1: input para digitar o nome do usuário e botão para continuar.
 * Tela 2 (Home): área do jogo + seção à direita com lista de jogadores, cada um
 * com um botão ao lado.
 */
public class Jogo extends JFrame {
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel root = new JPanel(cardLayout);

  // Estado
  private String jogadorAtual;
  private final DefaultListModel<String> jogadoresModel = new DefaultListModel<>();

  // Componentes compartilhados
  private JPanel painelLista; // painel vertical com linhas (nome + botao)
  private JLabel infoJogadorLabel; // label com nome do jogador atual na barra superior
  private JTextArea mensagensArea; // área de mensagens na Home
  // Map de botões por jogador para controlar estado de "Enviado"
  private java.util.Map<String, JButton> playerButtons = new java.util.HashMap<>();
  private JTextField hostField; // host do servidor
  private JTextField portField; // porta do servidor
  private JLabel loginStatusLabel; // status na tela de login
  private JButton loginButton; // botão de login para habilitar/desabilitar
  // Chat input na Home
  private JTextField chatInputField;
  private JButton chatSendButton;
  // Qual jogador recebeu o desafio (estado local de "Enviado")
  private String desafioEnviadoPara = null;
  // Container para desafios recebidos
  private JPanel incomingChallengesPanel;
  // Linha que mostra desafio enviado (aparecerá na mesma caixa de desafios)
  private JPanel outgoingChallengeRow = null;

  // --- Configurações / estado do tabuleiro ---
  private static final int BOARD_SIZE = 16;
  private JButton[][] boardButtons = new JButton[BOARD_SIZE][BOARD_SIZE];
  private boolean[][] atacado = new boolean[BOARD_SIZE][BOARD_SIZE];

  private int startX = 0, startY = 0; // coordenadas iniciais configuráveis
  private int playerX = 0, playerY = 0; // coordenadas atuais do jogador
  private int moveRange = 3; // alcance de movimento configurável
  private int attackRange = 3; // alcance de ataque configurável
  private int sonarRange = 4; // alcance do sonar configurável

  // Marcações de sonar (permanentes) e contadores de uso do jogador
  private boolean[][] sonarMarked = new boolean[BOARD_SIZE][BOARD_SIZE];
  private int sonaresPlaced = 0; // quantos sonares este cliente já colocou (confirmados)
  private int pendingSonares = 0; // requisições de sonar enviadas aguardando confirmação
  private static final int MAX_SONARES = 4;

  private Boolean playerTurn = true; // controle de turno (null = aguardando/reservado)
  private JLabel turnoLabel; // label de status do turno
  private ClientConnection connection; // conexão com servidor (opcional)

  private enum Modo {
    MOVER, ATACAR, SONAR
  }

  private Modo modoAtual = Modo.MOVER;

  private String token;

  public Jogo() {
    super("Batalha Subaquática");
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(900, 600));
    setLocationRelativeTo(null);

    root.add(criarTelaLogin(), "login");
    root.add(criarTelaHome(), "home");
    root.add(criarTelaJogo(), "game");
    setContentPane(root);

    // Dados de exemplo para a lista de jogadores (poderia vir do servidor depois)
    jogadoresModel.addElement("Ana");
    jogadoresModel.addElement("Bruno");
    jogadoresModel.addElement("Carla");
    jogadoresModel.addElement("Diego");

    cardLayout.show(root, "login");
  }

  private JPanel criarTelaLogin() {
    JPanel container = new JPanel(new GridBagLayout());
    container.setBorder(new EmptyBorder(24, 24, 24, 24));

    JPanel box = new JPanel();
    box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
    box.setBorder(new EmptyBorder(16, 16, 16, 16));
    box.setBackground(new Color(245, 248, 255));
    box.setMaximumSize(new Dimension(500, 200));

    JLabel titulo = new JLabel("Bem-vindo à Batalha Subaquática");
    titulo.setAlignmentX(Component.CENTER_ALIGNMENT);
    titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));

    JLabel rotulo = new JLabel("Digite seu nome de jogador:");
    rotulo.setAlignmentX(Component.LEFT_ALIGNMENT);
    rotulo.setBorder(new EmptyBorder(12, 0, 4, 0));

    JTextField campoNome = new JTextField();
    campoNome.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

    // Linha de servidor/porta
    JPanel serverRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    JLabel lblServidor = new JLabel("Servidor:");
    hostField = new JTextField("localhost");
    hostField.setPreferredSize(new Dimension(140, 28));
    JLabel lblPorta = new JLabel("Porta:");
    portField = new JTextField("9876");
    portField.setPreferredSize(new Dimension(80, 28));
    serverRow.add(lblServidor);
    serverRow.add(hostField);
    serverRow.add(lblPorta);
    serverRow.add(portField);

    // Status do login
    loginStatusLabel = new JLabel(" ");
    loginStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    loginStatusLabel.setForeground(new Color(120, 0, 0));

    loginButton = new JButton("Entrar no jogo");
    loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    loginButton.addActionListener((ActionEvent e) -> {
      String nome = campoNome.getText();
      if (nome == null || nome.trim().isEmpty()) {
        JOptionPane.showMessageDialog(this, "Por favor, digite um nome válido.",
            "Nome obrigatório", JOptionPane.WARNING_MESSAGE);
        return;
      }
      if (nome.contains(" ") || !nome.matches("\\A\\p{ASCII}*\\z")) {
        JOptionPane.showMessageDialog(this, "O nome não pode conter espaços ou caracteres especiais.",
            "Nome inválido", JOptionPane.WARNING_MESSAGE);
        return;
      }
      String host = hostField.getText().trim().isEmpty() ? "localhost" : hostField.getText().trim();
      int porta;
      try {
        porta = Integer.parseInt(portField.getText().trim());
      } catch (NumberFormatException ex) {
        JOptionPane.showMessageDialog(this, "Porta inválida.", "Erro", JOptionPane.ERROR_MESSAGE);
        return;
      }
      jogadorAtual = nome.trim();
      tentarConectarELogar(host, porta, jogadorAtual);
    });

    box.add(titulo);
    box.add(rotulo);
    box.add(campoNome);
    box.add(Box.createVerticalStrut(8));
    box.add(serverRow);
    box.add(Box.createVerticalStrut(12));
    box.add(loginButton);
    box.add(Box.createVerticalStrut(4));
    box.add(loginStatusLabel);

    container.add(box);
    return container;
  }

  private JPanel criarTelaHome() {
    JPanel home = new JPanel(new BorderLayout());

    // Criar painel de incoming challenges antes do painel central para que
    // possamos inseri-lo no topo da área central.
    incomingChallengesPanel = new JPanel();
    incomingChallengesPanel.setLayout(new BoxLayout(incomingChallengesPanel, BoxLayout.Y_AXIS));

    JPanel leftTopBox = new JPanel();
    leftTopBox.setLayout(new BoxLayout(leftTopBox, BoxLayout.Y_AXIS));
    leftTopBox.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(220, 220, 220)),
        new EmptyBorder(4, 4, 4, 4)));
    // alinhar a caixa inteira à esquerda dentro do BoxLayout do centro
    leftTopBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    // Forçar largura fixa para que a caixa não redimensione conforme o conteúdo.
    // Mantemos uma pequena margem lateral porque o painel central tem borda.
    Dimension fixedSize = new Dimension(700, 160);
    leftTopBox.setPreferredSize(fixedSize);
    leftTopBox.setMaximumSize(new Dimension(fixedSize.width, 10000));
    leftTopBox.setMinimumSize(new Dimension(fixedSize.width, 40));
    JLabel incomingTitle = new JLabel("Desafios recebidos");
    incomingTitle.setFont(incomingTitle.getFont().deriveFont(Font.BOLD, 12f));
    // alinhar texto à esquerda dentro do BoxLayout
    incomingTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
    leftTopBox.add(incomingTitle);
    leftTopBox.add(Box.createVerticalStrut(6));
    incomingChallengesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    leftTopBox.add(incomingChallengesPanel);

    // Área central (placeholder para o tabuleiro/ação do jogo)
    JPanel centro = new JPanel();
    centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
    // reduzir o espaçamento à esquerda do painel central para diminuir margem
    centro.setBorder(new EmptyBorder(24, 6, 24, 24));

    JLabel placeholder = new JLabel("Área do jogo (Home)");
    placeholder.setAlignmentX(Component.CENTER_ALIGNMENT);
    placeholder.setFont(placeholder.getFont().deriveFont(Font.PLAIN, 18f));
    placeholder.setForeground(new Color(70, 70, 70));
    // Adiciona a caixa de desafios recebidos acima do conteúdo central
    centro.add(leftTopBox);

    JButton irParaJogo = new JButton("Ir para o jogo");
    irParaJogo.setAlignmentX(Component.CENTER_ALIGNMENT);
    irParaJogo.addActionListener(e -> {
      iniciarPartida();
      cardLayout.show(root, "game");
    });

    centro.add(placeholder);
    centro.add(Box.createVerticalStrut(12));
    centro.add(irParaJogo);

    // Seção da direita: lista de jogadores com botões
    JPanel direita = new JPanel(new BorderLayout());
    direita.setPreferredSize(new Dimension(280, 600));
    direita.setBorder(new EmptyBorder(12, 12, 12, 12));

    JLabel titulo = new JLabel("Jogadores online");
    titulo.setBorder(new EmptyBorder(0, 0, 0, 0));
    titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 14f));

    JButton btnAtualizar = new JButton("Atualizar");
    btnAtualizar.addActionListener(e -> {
      if (connection != null && connection.isConnected()) {
        connection.sendLine("LISTARJOGADORES");
      } else {
        JOptionPane.showMessageDialog(this, "Sem conexão com o servidor.", "Lista de jogadores",
            JOptionPane.WARNING_MESSAGE);
      }
    });

    JPanel northBar = new JPanel(new BorderLayout());
    northBar.setBorder(new EmptyBorder(0, 0, 8, 0));
    northBar.add(titulo, BorderLayout.WEST);
    northBar.add(btnAtualizar, BorderLayout.EAST);

    // painelLista (direita) keeps only the northBar at its top now
    JPanel topContainer = new JPanel(new BorderLayout());
    topContainer.add(northBar, BorderLayout.CENTER);

    painelLista = new JPanel();
    painelLista.setLayout(new BoxLayout(painelLista, BoxLayout.Y_AXIS));
    JScrollPane scroll = new JScrollPane(painelLista,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(16);

    direita.add(topContainer, BorderLayout.NORTH);
    direita.add(scroll, BorderLayout.CENTER);

    // Barra superior com info do jogador atual e leftTopBox ao lado esquerdo
    JPanel topo = new JPanel(new BorderLayout());
    topo.setBorder(new EmptyBorder(8, 12, 8, 12));
    infoJogadorLabel = new JLabel("");
    // Aumenta um pouco a fonte para melhor visibilidade
    infoJogadorLabel.setFont(infoJogadorLabel.getFont().deriveFont(Font.BOLD, 13f));
    topo.add(infoJogadorLabel, BorderLayout.CENTER);

    // northContainer coloca o leftTopBox à esquerda e o topo (infoJogadorLabel)
    // no centro — isso cria a nova "box" no canto superior esquerdo da Home.
    JPanel northContainer = new JPanel(new BorderLayout());
    // colocar apenas o topo (infoJogadorLabel) no northContainer; a leftTopBox
    // será mostrada dentro do painel central acima do conteúdo de Home.
    northContainer.add(topo, BorderLayout.CENTER);

    // Caixa de mensagens na parte inferior
    JPanel mensagensPanel = new JPanel(new BorderLayout());
    mensagensPanel.setBorder(new EmptyBorder(8, 12, 12, 12));
    JLabel lblMsg = new JLabel("Mensagens");
    lblMsg.setFont(lblMsg.getFont().deriveFont(Font.BOLD));
    mensagensArea = new JTextArea(5, 20);
    mensagensArea.setEditable(false);
    mensagensArea.setLineWrap(true);
    mensagensArea.setWrapStyleWord(true);
    JScrollPane spMsgs = new JScrollPane(mensagensArea,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    mensagensPanel.add(lblMsg, BorderLayout.NORTH);
    mensagensPanel.add(spMsgs, BorderLayout.CENTER);

    // Linha de input de chat (campo + botão Enviar)
    JPanel chatRow = new JPanel(new BorderLayout(8, 0));
    chatInputField = new JTextField();
    chatSendButton = new JButton("Enviar");
    // Enviar ao clicar no botão
    chatSendButton.addActionListener(e -> enviarMensagemChat());
    // Enviar ao pressionar Enter no campo de texto
    chatInputField.addActionListener(e -> enviarMensagemChat());
    chatRow.add(chatInputField, BorderLayout.CENTER);
    chatRow.add(chatSendButton, BorderLayout.EAST);
    mensagensPanel.add(chatRow, BorderLayout.SOUTH);

    home.add(northContainer, BorderLayout.NORTH);
    home.add(centro, BorderLayout.CENTER);
    home.add(direita, BorderLayout.EAST);
    home.add(mensagensPanel, BorderLayout.SOUTH);
    return home;
  }

  private void atualizarListaJogadores() {
    // Constrói lista final a exibir: jogadores de exemplo + atual (se não existir).
    List<String> nomes = new ArrayList<>();
    for (int i = 0; i < jogadoresModel.size(); i++) {
      String n = jogadoresModel.getElementAt(i);
      if (!Objects.equals(n, jogadorAtual)) {
        nomes.add(n);
      }
    }
    // Garante que o atual apareça no topo da lista, marcado.
    // Ele aparece visualmente, mas o botão ao lado fica desabilitado.
    if (infoJogadorLabel != null) {
      infoJogadorLabel.setText(jogadorAtual == null ? "" : ("Jogador: " + jogadorAtual));
    }
    // rebuild lista de jogadores: limpa referências antigas e recria
    painelLista.removeAll();
    playerButtons.clear();

    // Atualiza caixa de mensagens da Home
    // Preserva as mensagens existentes (antes este método limpava a área de
    // mensagens,
    // o que fazia com que ao clicar em "Atualizar" todo o histórico desaparecesse).
    // Mantemos a posição do caret ao final para mostrar as mensagens mais recentes.
    if (mensagensArea != null) {
      mensagensArea.setCaretPosition(mensagensArea.getDocument().getLength());
    }

    // Primeiro, o próprio jogador
    if (jogadorAtual != null && !jogadorAtual.isEmpty()) {
      painelLista.add(criarLinhaJogador(jogadorAtual + " (você)", false));
      painelLista.add(Box.createVerticalStrut(6));
    }

    // Depois, os demais
    for (String nome : nomes) {
      painelLista.add(criarLinhaJogador(nome, true));
      painelLista.add(Box.createVerticalStrut(6));
    }

    painelLista.revalidate();
    painelLista.repaint();
  }

  private JPanel criarLinhaJogador(String nome, boolean habilitarBotao) {
    JPanel linha = new JPanel(new BorderLayout());
    linha.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(220, 225, 235)),
        new EmptyBorder(8, 8, 8, 8)));
    JLabel label = new JLabel(nome);
    JButton botao = new JButton("Desafiar");
    botao.setEnabled(habilitarBotao);
    // armazena o botão para poder controlar o estado "Enviado"
    if (habilitarBotao) {
      playerButtons.put(nome, botao);
      // Ajusta o texto caso já exista um desafio enviado para este jogador
      if (nome.equals(desafioEnviadoPara)) {
        botao.setText("Enviado");
        botao.setEnabled(true);
      }
    }

    botao.addActionListener(e -> {
      // Se não estiver conectado, avisa
      if (connection == null || !connection.isConnected()) {
        JOptionPane.showMessageDialog(
            this,
            "Sem conexão com o servidor (modo offline)",
            "Ação",
            JOptionPane.INFORMATION_MESSAGE);
        return;
      }

      // Não pode desafiar sem login/token
      if (jogadorAtual == null || jogadorAtual.isEmpty() || token == null || token.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Você precisa estar logado para desafiar.", "Ação",
            JOptionPane.WARNING_MESSAGE);
        return;
      }

      // Reset antigo botão (só um 'Enviado' por vez)
      if (desafioEnviadoPara != null && !desafioEnviadoPara.equals(nome)) {
        JButton prev = playerButtons.get(desafioEnviadoPara);
        if (prev != null) {
          prev.setText("Desafiar");
        }
        // Remover indicação de desafio enviado anterior (se houver)
        if (outgoingChallengeRow != null) {
          incomingChallengesPanel.remove(outgoingChallengeRow);
          outgoingChallengeRow = null;
          incomingChallengesPanel.revalidate();
          incomingChallengesPanel.repaint();
        }
      }

      // Marcar este como enviado
      desafioEnviadoPara = nome;
      botao.setText("Enviado");

      // Atualiza a indicação visual de desafio enviado dentro da caixa de desafios
      if (outgoingChallengeRow != null) {
        incomingChallengesPanel.remove(outgoingChallengeRow);
        outgoingChallengeRow = null;
      }

      // Envia o comando DESAFIAR para o servidor (formato atual do projeto)
      connection.sendLine("DESAFIAR " + jogadorAtual + " " + token + " " + nome);
    });

    linha.add(label, BorderLayout.CENTER);
    linha.add(botao, BorderLayout.EAST);
    return linha;
  }

  // --- Tela de jogo (tabuleiro 16x16) ---
  private JPanel criarTelaJogo() {
    JPanel tela = new JPanel(new BorderLayout());
    tela.setBorder(new EmptyBorder(8, 8, 8, 8));

    // Barra superior com configurações
    JPanel configuracoes = new JPanel();
    configuracoes.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));

    // Seleção de modo
    JToggleButton btMover = new JToggleButton("Mover");
    JToggleButton btAtacar = new JToggleButton("Atacar");
    JToggleButton btSonar = new JToggleButton("Sonar");
    ButtonGroup grupoModo = new ButtonGroup();
    grupoModo.add(btMover);
    grupoModo.add(btAtacar);
    grupoModo.add(btSonar);
    btMover.setSelected(true);
    btMover.addActionListener(e -> modoAtual = Modo.MOVER);
    btAtacar.addActionListener(e -> modoAtual = Modo.ATACAR);
    btSonar.addActionListener(e -> modoAtual = Modo.SONAR);

    configuracoes.add(new JLabel(" | Modo:"));
    configuracoes.add(btMover);
    configuracoes.add(btAtacar);
    configuracoes.add(btSonar);

    tela.add(configuracoes, BorderLayout.NORTH);

    // Tabuleiro (16x16)
    JPanel boardPanel = new JPanel(new GridLayout(BOARD_SIZE, BOARD_SIZE, 1, 1));
    boardPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        final int cx = x, cy = y;
        JButton b = new JButton();
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setPreferredSize(new Dimension(32, 32));
        b.setFocusPainted(false);
        b.addActionListener(e -> onCellClick(cx, cy));
        boardButtons[y][x] = b;
        boardPanel.add(b);
      }
    }
    tela.add(boardPanel, BorderLayout.CENTER);

    // Barra inferior: status de turno
    JPanel status = new JPanel(new BorderLayout());
    status.setBorder(new EmptyBorder(4, 8, 4, 8));
    turnoLabel = new JLabel("Turno: Jogador");
    status.add(turnoLabel, BorderLayout.WEST);

    JButton voltarHome = new JButton("Voltar para Home");
    voltarHome.addActionListener(e -> cardLayout.show(root, "home"));
    status.add(voltarHome, BorderLayout.EAST);

    tela.add(status, BorderLayout.SOUTH);
    return tela;
  }

  private void iniciarPartida() {
    // reinicia estado
    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        atacado[y][x] = false;
        sonarMarked[y][x] = false;
      }
    }
    playerX = clamp(startX, 0, BOARD_SIZE - 1);
    playerY = clamp(startY, 0, BOARD_SIZE - 1);
    playerTurn = true;
    sonaresPlaced = 0;
    pendingSonares = 0;
    atualizarStatusTurno();
    atualizarTabuleiro();
  }

  private void onCellClick(int x, int y) {
    // Se não houver conexão com o servidor, bloquear qualquer ação local.
    if (connection == null || !connection.isConnected()) {
      JOptionPane.showMessageDialog(this, "Sem conexão com o servidor. Conecte-se para jogar.", "Sem conexão",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    // Se o turno for nulo (reservado) ou não for o jogador atual, não permite ação
    if (playerTurn == null || !playerTurn)
      return; // aguarda inimigo ou reserva

    switch (modoAtual) {
      case MOVER:
        if (alcance(playerX, playerY, x, y) <= moveRange) {
          // Não atualizar posição local aqui — enviamos ao servidor e esperamos a
          // confirmação/atualização via mensagem MOVER do servidor.
          if (connection != null && connection.isConnected()) {
            connection.sendLine("MOVER " + jogadorAtual + " " + token + " " + x + " " + y);
            // Ainda encerramos o turno localmente: o servidor controlará a próxima
            // reabilitação via mensagem "TURNO".
            fimDoTurnoDoJogador();
          } else {
            // Conexão verificada antes, mas caso venha a ocorrer, bloqueia ação.
            JOptionPane.showMessageDialog(this, "Sem conexão com o servidor. Conecte-se para jogar.",
                "Sem conexão", JOptionPane.WARNING_MESSAGE);
          }
        } else {
          beepMsg("Fora do alcance de movimento.");
        }
        break;
      case ATACAR:
        if (alcance(playerX, playerY, x, y) <= attackRange) {
          // Não aplicar efeito local imediatamente — envie ao servidor e aguarde
          // confirmação (mensagem "ATACAR") para desenhar o X temporário.
          if (connection != null && connection.isConnected()) {
            connection.sendLine("ATACAR " + jogadorAtual + " " + token + " " + x + " " + y);
            fimDoTurnoDoJogador();
          } else {
            JOptionPane.showMessageDialog(this, "Sem conexão com o servidor. Conecte-se para jogar.",
                "Sem conexão", JOptionPane.WARNING_MESSAGE);
          }
        } else {
          beepMsg("Fora do alcance de ataque.");
        }
        break;
      case SONAR:
        if (alcance(playerX, playerY, x, y) <= sonarRange) {
          // Limite de sonares: soma de confirmados + pendentes
          if (sonaresPlaced + pendingSonares >= MAX_SONARES) {
            JOptionPane.showMessageDialog(this, "Limite de " + MAX_SONARES + " sonares atingido.", "Sonar",
                JOptionPane.INFORMATION_MESSAGE);
            break;
          }
          if (connection != null && connection.isConnected()) {
            // marcaremos ao receber a confirmação do servidor (mensagem "SONAR").
            pendingSonares++;
            connection.sendLine("SONAR " + jogadorAtual + " " + token + " " + x + " " + y);
            fimDoTurnoDoJogador();
          } else {
            JOptionPane.showMessageDialog(this, "Sem conexão com o servidor. Conecte-se para jogar.",
                "Sem conexão", JOptionPane.WARNING_MESSAGE);
          }
        } else {
          beepMsg("Fora do alcance do sonar.");
        }
        break;
    }
  }

  private void fimDoTurnoDoJogador() {
    // When connected, notify server that we finished our turn and wait for the
    // server to assign the next turn (server will send a TURNO message).
    if (connection != null && connection.isConnected()) {
      atualizarStatusTurno();
      // Inform server we passed/ended our turn. Server expects PASSAR <nome> <token>
      try {
        connection.sendLine("PASSAR " + jogadorAtual + " " + token);
      } catch (Exception ignore) {
      }
    } else {
      // No server: block actions and inform the user — cannot proceed offline.
      playerTurn = false;
      atualizarStatusTurno();
      JOptionPane.showMessageDialog(this, "Sem conexão com o servidor. Não é possível prosseguir.", "Erro",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void atualizarStatusTurno() {
    if (turnoLabel != null) {
      String texto;
      if (playerTurn == null) {
        texto = "Turno: aguardando...";
      } else {
        texto = playerTurn ? "Turno: Jogador" : "Turno: Inimigo...";
      }
      turnoLabel.setText(texto);
    }
    // Opcional: desabilitar o tabuleiro quando não é o turno do jogador
    boolean enabled = Boolean.TRUE.equals(playerTurn);
    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        if (boardButtons[y][x] != null)
          boardButtons[y][x].setEnabled(enabled);
      }
    }
  }

  private void atualizarTabuleiro() {
    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        JButton b = boardButtons[y][x];
        if (b == null)
          continue;
        b.setText("");
        if (x == playerX && y == playerY) {
          b.setBackground(new Color(90, 160, 255)); // jogador
        } else if (atacado[y][x]) {
          b.setBackground(new Color(240, 120, 120)); // atacado
          b.setText("X");
        } else if (sonarMarked[y][x]) {
          b.setBackground(new Color(230, 230, 150)); // sonar
          b.setText("S");
        } else {
          b.setBackground(Color.WHITE);
        }
      }
    }
  }

  // Atualiza o modelo de jogadores (lado direito) com uma lista CSV vinda do
  // servidor
  private void atualizarListaJogadoresDoServidor(String csv) {
    if (csv == null)
      csv = "";
    String[] nomes = csv.split(",");
    jogadoresModel.clear();
    for (String n : nomes) {
      String nome = n == null ? "" : n.trim();
      if (!nome.isEmpty()) {
        jogadoresModel.addElement(nome);
      }
    }
    atualizarListaJogadores();
  }

  private int alcance(int x1, int y1, int x2, int y2) {
    // Distância Chebyshev: max(|dx|, |dy|)
    return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
  }

  private int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

  private void beepMsg(String msg) {
    Toolkit.getDefaultToolkit().beep();
    JOptionPane.showMessageDialog(this, msg, "Aviso", JOptionPane.WARNING_MESSAGE);
  }

  // === Integração com servidor ===
  private void tentarConectarELogar(String host, int port, String nome) {
    loginButton.setEnabled(false);
    loginStatusLabel.setForeground(new Color(0, 70, 120));
    loginStatusLabel.setText("Conectando em " + host + ":" + port + "...");

    SwingWorker<Void, Void> worker = new SwingWorker<>() {
      private String erro;

      @Override
      protected Void doInBackground() {
        try {
          if (connection != null) {
            try {
              connection.close();
            } catch (Exception ignore) {
            }
          }
          connection = new ClientConnection(host, port);
          connection.setOnMessage(Jogo.this::handleServerMessage);
          connection.connect();

          // Envia mensagem de cadastro – ajuste o protocolo conforme seu servidor
          connection.sendLine("CADASTRO " + nome);
        } catch (IOException ex) {
          erro = ex.getMessage();
        }
        return null;
      }

      @Override
      protected void done() {
        if (erro != null) {
          loginStatusLabel.setForeground(new Color(120, 0, 0));
          loginStatusLabel.setText("Falha ao conectar: " + erro);
          loginButton.setEnabled(true);
        } else {
          loginStatusLabel.setForeground(new Color(0, 120, 0));
          loginStatusLabel.setText("Conectado. Aguardando resposta do servidor...");

        }
      }
    };
    worker.execute();
  }

  // Envia mensagem de chat para o servidor no formato: CHATGLOBAL <jogadorAtual>
  // <token> <mensagem>
  // Não faz append local; o servidor deverá ecoar a mensagem de volta para todos.
  private void enviarMensagemChat() {
    if (chatInputField == null)
      return;
    String texto = chatInputField.getText();
    if (texto == null)
      texto = "";
    texto = texto.trim();
    if (texto.isEmpty())
      return;

    if (jogadorAtual == null || jogadorAtual.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Você precisa estar logado para enviar mensagens.", "Chat",
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    if (token == null || token.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Token ausente. Refaça o login.", "Chat",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    if (connection != null && connection.isConnected()) {
      // Envia exatamente no formato pedido (separado por espaços)
      connection.sendLine("CHATGLOBAL " + jogadorAtual + " " + token + " " + texto);
      // Não fazemos append local — o servidor deverá reenviar a mensagem para todos
      chatInputField.setText("");
    } else {
      JOptionPane.showMessageDialog(this, "Sem conexão com o servidor.", "Chat",
          JOptionPane.WARNING_MESSAGE);
    }
  }

  // Função que separa o valor de um campo específico da linha
  private String separarValores(String line, String campo) {
    // Adiciona uma vírgula no final para facilitar o parsing do último campo
    String temp = line + ",";
    int startIndex = temp.indexOf(campo + ":");

    if (startIndex == -1) {
      return null; // Campo não encontrado
    }

    startIndex += (campo + ":").length();

    // Caso especial: o campo "lista" termina com "}"
    if (campo.equals("lista")) {
      int endIndex = temp.indexOf("}", startIndex);
      if (endIndex == -1)
        return null;
      return temp.substring(startIndex, endIndex + 1); // inclui o '}'
    } else {
      int endIndex = temp.indexOf(",", startIndex);
      return temp.substring(startIndex, endIndex);
    }
  }

  // Função que separa os itens dentro de uma lista { ... }
  private String[] separarLista(String listaCampo) {
    if (listaCampo == null || !listaCampo.startsWith("{") || !listaCampo.endsWith("}")) {
      return new String[0]; // Lista inválida
    }

    // Remove as chaves { e }
    String conteudo = listaCampo.substring(1, listaCampo.length() - 1);

    // Divide os itens por ';'
    return conteudo.split(";");
  }

  // Ponto central para tratar mensagens vindas do servidor
  private void handleServerMessage(String line) {
    if (line == null)
      return;
    // Garanta que atualizações de UI ocorram na EDT
    SwingUtilities.invokeLater(() -> {
      // Tente interpretar no formato: COMANDO|CODIGO|TEXTO|VALORES
      String[] parts = line.split("\\|", -1);
      String comandoServer = parts.length > 0 ? parts[0] : "";
      String codigoServer = parts.length > 1 ? parts[1] : "";
      String textoServer = parts.length > 2 ? parts[2] : "";
      String valoresServer = parts.length > 3 ? parts[3] : ""; // valores adicionais (ex.: lista CSV)

      System.out.println("Recebido do servidor: " + line);

      // 1) Fluxo de cadastro/login usando pipe
      switch (comandoServer) {
        case "CADASTRAR": {
          if ("201".equals(codigoServer)) {
            // Sucesso: avançar para HOME
            atualizarListaJogadores();
            cardLayout.show(root, "home");
            if (loginButton != null)
              loginButton.setEnabled(true);
            if (loginStatusLabel != null)
              loginStatusLabel.setText(" ");

            // Salvando o token
            token = separarValores(valoresServer, "token");

            // Após entrar na Home, solicitar lista atual de jogadores
            if (connection != null && connection.isConnected()) {
              connection.sendLine("LISTARJOGADORES");
            }
          } else {
            // Falha: exibir textoServer (mensagem do backend) ou padrão
            String motivo = (textoServer != null && !textoServer.isEmpty()) ? textoServer : "Cadastro não realizado";
            if (loginStatusLabel != null) {
              loginStatusLabel.setForeground(new Color(120, 0, 0));
              loginStatusLabel.setText(motivo);
            }
            JOptionPane.showMessageDialog(this, motivo, "Cadastro não realizado", JOptionPane.WARNING_MESSAGE);
            if (loginButton != null)
              loginButton.setEnabled(true);
          }
          break;
        }
        case "LISTARJOGADORES": {
          if ("200".equals(codigoServer)) {
            String jogadoresLista = separarValores(valoresServer, "jogadores");
            String jogadores[] = separarLista(jogadoresLista);
            String csv = "";
            for (String j : jogadores) {
              if (csv.length() > 0) {
                csv += ",";
              }
              String nome = separarValores(j, "nome");
              if (!jogadorAtual.equals(nome)) {
                csv += nome;
              }
            }
            atualizarListaJogadoresDoServidor(csv);
          } else {
            // erro ao listar – opcionalmente exibir no chat
            if (mensagensArea != null) {
              mensagensArea.append("Falha ao listar jogadores: " + textoServer + "\n");
            }
          }
          break;
        }
        case "DESAFIAR": {
          if ("201".equals(codigoServer)) {
            String desafioEnviadoPara = separarValores(valoresServer, "desafiado");
            // Desafio enviado com sucesso
            // Cria painel de desafio recebido
            JPanel challengeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            challengeRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                new EmptyBorder(6, 6, 6, 6)));
            JLabel lbl = new JLabel("Desafio enviado com sucesso para: " + desafioEnviadoPara);

            challengeRow.add(lbl);
            // Remove any previous "Desafio enviado com sucesso" entry before adding
            for (Component comp : incomingChallengesPanel.getComponents()) {
              if (comp instanceof JPanel) {
                JPanel p = (JPanel) comp;
                for (Component inner : p.getComponents()) {
                  if (inner instanceof JLabel) {
                    String txt = ((JLabel) inner).getText();
                    if (txt != null && txt.startsWith("Desafio enviado com sucesso")) {
                      incomingChallengesPanel.remove(p);
                      break;
                    }
                  }
                }
              }
            }
            // Track this as the current outgoing success row so it can be removed later
            outgoingChallengeRow = challengeRow;
            incomingChallengesPanel.add(challengeRow, 0);
            incomingChallengesPanel.revalidate();
            incomingChallengesPanel.repaint();
          } else if ("200".equals(codigoServer)) {
            // Mensagem de desafio recebido: extrai quem desafiou e adiciona painel com
            // aceitar/recusar
            String challenger = separarValores(valoresServer, "desafiante");
            if (challenger.isEmpty())
              break;

            // Cria painel de desafio recebido
            JPanel challengeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            challengeRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                new EmptyBorder(6, 6, 6, 6)));
            JLabel lbl = new JLabel("Desafio recebido de " + challenger);
            JButton btnAceitar = new JButton("Aceitar");
            JButton btnRecusar = new JButton("Recusar");

            // Remove any previous "Desafio enviado com sucesso" entry before adding
            for (Component comp : incomingChallengesPanel.getComponents()) {
              if (comp instanceof JPanel) {
                JPanel p = (JPanel) comp;
                for (Component inner : p.getComponents()) {
                  if (inner instanceof JLabel) {
                    String txt = ((JLabel) inner).getText();
                    if (txt != null && txt.startsWith("Desafio recebido de " + challenger)) {
                      incomingChallengesPanel.remove(p);
                      break;
                    }
                  }
                }
              }
            }

            btnAceitar.addActionListener(ev -> {
              // Envia aceitação para o servidor; formato padrão usado no projeto
              if (connection != null && connection.isConnected()) {
                connection.sendLine("ACEITARDESAFIO " + jogadorAtual + " " + token + " " + challenger);
              }
              incomingChallengesPanel.remove(challengeRow);
              incomingChallengesPanel.revalidate();
              incomingChallengesPanel.repaint();
            });

            btnRecusar.addActionListener(ev -> {
              if (connection != null && connection.isConnected()) {
                connection.sendLine("RECUSARDESAFIO " + jogadorAtual + " " + token + " " + challenger);
              }
              incomingChallengesPanel.remove(challengeRow);
              incomingChallengesPanel.revalidate();
              incomingChallengesPanel.repaint();
            });

            challengeRow.add(lbl);
            challengeRow.add(btnAceitar);
            challengeRow.add(btnRecusar);
            incomingChallengesPanel.add(challengeRow, 0);
            incomingChallengesPanel.revalidate();
            incomingChallengesPanel.repaint();
            break;
          }
        }
        case "ACEITARDESAFIO": {
          break;
        }
        case "RECUSARDESAFIO": {
          break;
        }
        case "CHATGLOBAL": {
          String from = separarValores(valoresServer, "nome");
          String msg = separarValores(valoresServer, "mensagem");
          String display = (from != null && !from.isEmpty()) ? (from + ": " + msg) : msg;
          if (mensagensArea != null && display != null && !display.isEmpty()) {
            mensagensArea.append(display + "\n");
            mensagensArea.setCaretPosition(mensagensArea.getDocument().getLength());
          }
          break;
        }
        case "CHATPARTIDA": {
          break;
        }
        case "PRONTOPARTIDA": {
          break;
        }
        case "RESERVADOPARTIDA": {
          // O servidor reservou a partida e fornece coordenadas iniciais (x, y).
          String xStr = separarValores(valoresServer, "x");
          String yStr = separarValores(valoresServer, "y");
          int rx = 0, ry = 0;
          try {
            rx = Integer.parseInt(xStr == null ? "0" : xStr);
          } catch (NumberFormatException ignore) {
          }
          try {
            ry = Integer.parseInt(yStr == null ? "0" : yStr);
          } catch (NumberFormatException ignore) {
          }

          // Reinicia estado básico do tabuleiro (posicional)
          iniciarPartida();
          // Posiciona jogador conforme informado pelo servidor
          playerX = clamp(rx, 0, BOARD_SIZE - 1);
          playerY = clamp(ry, 0, BOARD_SIZE - 1);
          // Turno ainda indefinido até o servidor enviar TURNO
          playerTurn = null;
          atualizarStatusTurno();
          atualizarTabuleiro();

          // Mostra a tela do jogo
          cardLayout.show(root, "game");

          // Depois de carregar a tela, notifica o servidor que estamos prontos
          if (connection != null && connection.isConnected()) {
            connection.sendLine("PRONTO " + jogadorAtual + " " + token);
          }
          break;
        }
        case "INICIOPARTIDA": {
          break;
        }
        case "MOVER": {
          // Servidor informa movimento (x,y) — aplicamos localmente quando a
          // mensagem indica que o movimento é do próprio jogador.
          String moverNome = separarValores(valoresServer, "nome");
          String xStr = separarValores(valoresServer, "x");
          String yStr = separarValores(valoresServer, "y");
          int mx = 0, my = 0;
          try {
            mx = Integer.parseInt(xStr == null ? "0" : xStr);
          } catch (NumberFormatException ignore) {
          }
          try {
            my = Integer.parseInt(yStr == null ? "0" : yStr);
          } catch (NumberFormatException ignore) {
          }
          if (moverNome != null && moverNome.equals(jogadorAtual)) {
            playerX = clamp(mx, 0, BOARD_SIZE - 1);
            playerY = clamp(my, 0, BOARD_SIZE - 1);
            atualizarTabuleiro();
          }
          break;
        }
        case "ATACAR": {
          // Servidor indica um ataque em (x,y). Marcamos temporariamente com X
          String xStr = separarValores(valoresServer, "x");
          String yStr = separarValores(valoresServer, "y");
          int ax = 0, ay = 0;
          try {
            ax = Integer.parseInt(xStr == null ? "0" : xStr);
          } catch (NumberFormatException ignore) {
          }
          try {
            ay = Integer.parseInt(yStr == null ? "0" : yStr);
          } catch (NumberFormatException ignore) {
          }
          if (ax >= 0 && ax < BOARD_SIZE && ay >= 0 && ay < BOARD_SIZE) {
            atacado[ay][ax] = true;
            atualizarTabuleiro();
            // Remover a marcação após 3 segundos
            final int fAx = ax;
            final int fAy = ay;
            Timer t = new Timer(3000, ev -> {
              atacado[fAy][fAx] = false;
              atualizarTabuleiro();
            });
            t.setRepeats(false);
            t.start();
          }
          break;
        }
        case "SONAR": {
          // Servidor indica um sonar em (x,y). Sonar fica permanente.
          String sonarBy = separarValores(valoresServer, "nome");
          String sxStr = separarValores(valoresServer, "x");
          String syStr = separarValores(valoresServer, "y");
          int sx = 0, sy = 0;
          try {
            sx = Integer.parseInt(sxStr == null ? "0" : sxStr);
          } catch (NumberFormatException ignore) {
          }
          try {
            sy = Integer.parseInt(syStr == null ? "0" : syStr);
          } catch (NumberFormatException ignore) {
          }
          if (sx >= 0 && sx < BOARD_SIZE && sy >= 0 && sy < BOARD_SIZE) {
            sonarMarked[sy][sx] = true;
            // Atualiza contadores: se o sonar for deste jogador, consumimos um pendente
            if (sonarBy != null && sonarBy.equals(jogadorAtual)) {
              if (pendingSonares > 0)
                pendingSonares--;
              sonaresPlaced++;
            }
            atualizarTabuleiro();
          }
          break;
        }
        case "PASSAR": {
          break;
        }
        case "SAIRPARTIDA": {
          break;
        }
        case "SAIR": {
          break;
        }
        case "DETECTADO": {
          break;
        }
        case "ACERTO": {
          break;
        }
        case "MORTE": {
          break;
        }
        case "VITORIA": {
          break;
        }
        case "FIMPARTIDA": {
          break;
        }
        case "TURNO": {
          if ("200".equals(codigoServer)) {
            String nomeJogadorTurno = separarValores(valoresServer, "turno");
            boolean isPlayerTurn = jogadorAtual != null && jogadorAtual.equals(nomeJogadorTurno);
            playerTurn = isPlayerTurn;
            atualizarStatusTurno();
          }
          break;
        }
        case "ERRO": {
          break;
        }
        case "DESCONECTADO": {
          try {
            if (connection != null) {
              connection.close();
            }
          } catch (Exception ignore) {
          }
          jogadorAtual = null;
          token = null;
          connection = null;
          jogadoresModel.clear();
          if (mensagensArea != null) {
            mensagensArea.setText("");
          }
          if (chatInputField != null) {
            chatInputField.setText("");
          }
          if (infoJogadorLabel != null) {
            infoJogadorLabel.setText("");
          }
          if (loginStatusLabel != null) {
            loginStatusLabel.setForeground(new Color(120, 0, 0));
            loginStatusLabel.setText("Desconectado por inatividade");
          }
          if (loginButton != null) {
            loginButton.setEnabled(true);
          }
          // Mostrar popup informando desconexão e voltar para tela de login
          JOptionPane.showMessageDialog(this, "Desconectado por inatividade", "Desconectado",
              JOptionPane.WARNING_MESSAGE);
          cardLayout.show(root, "login");
          break;
        }
        default: {
          break;
        }
      }
    });
  }

  // Cliente simples baseado em socket, com thread de leitura
  private static class ClientConnection {
    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread readerThread;
    private volatile boolean running;
    private Consumer<String> onMessage;

    ClientConnection(String host, int port) {
      this.host = host;
      this.port = port;
    }

    void setOnMessage(Consumer<String> handler) {
      this.onMessage = handler;
    }

    void connect() throws IOException {
      socket = new Socket(host, port);
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
      running = true;
      readerThread = new Thread(() -> {
        try {
          String line;
          while (running && (line = in.readLine()) != null) {
            if (onMessage != null)
              onMessage.accept(line);
          }
        } catch (IOException ignored) {
        } finally {
          running = false;
          try {
            socket.close();
          } catch (Exception ignore) {
          }
        }
      }, "Server-Reader");
      readerThread.setDaemon(true);
      readerThread.start();
    }

    boolean isConnected() {
      return socket != null && socket.isConnected() && !socket.isClosed();
    }

    synchronized void sendLine(String text) {
      if (out != null) {
        out.print(text + "\n");
        out.flush();
      }
    }

    void close() throws IOException {
      running = false;
      if (socket != null)
        socket.close();
    }

  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      // Usa o look and feel do sistema, se disponível
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {
      }

      new Jogo().setVisible(true);
    });
  }
}
