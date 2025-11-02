package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.*;
import java.net.*;
import java.util.function.Consumer;

// Código quase inteiramente feito pelo agente Copilot, apenas algumas partes foram escritas manualmente
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
  // Painel para listar partidas públicas (adicionado)
  private JPanel publicGamesPanel;
  // Map de botões por partida pública para controlar estado de "entrou"
  private java.util.Map<String, JButton> publicGameButtons = new java.util.HashMap<>();
  // Id da partida que o jogador marcou como 'entrou' (apenas uma)
  private String entrouPartidaId = null;
  // Linha que mostra desafio enviado (aparecerá na mesma caixa de desafios)
  private JPanel outgoingChallengeRow = null;
  // Indica se o jogador local está morto na partida atual
  private boolean jogadorMorto = false;

  // --- Configurações / estado do tabuleiro ---
  private static final int BOARD_SIZE = 16;
  private JButton[][] boardButtons = new JButton[BOARD_SIZE][BOARD_SIZE];
  private boolean[][] atacado = new boolean[BOARD_SIZE][BOARD_SIZE];
  // Painel do tabuleiro (guardado para atualizações em lote)
  private JPanel boardPanelRef;
  // Modo buttons e controle de saída expostos como campos para garantir que
  // fiquem ativos independentemente do turno e possam ser controlados.
  private JToggleButton btMoverField;
  private JToggleButton btAtacarField;
  private JToggleButton btSonarField;
  private JButton sairPartidaBtn;

  // Área de mensagens específica da partida (mostrada na tela de jogo)
  private JTextArea gameMessagesArea;

  private int startX = 0, startY = 0; // coordenadas iniciais configuráveis
  private int playerX = 0, playerY = 0; // coordenadas atuais do jogador
  private int moveRange = 2; // alcance de movimento configurável
  private int attackRange = 4; // alcance de ataque configurável
  private int sonarRange = 1; // alcance do sonar configurável

  // Formato de alcance: quadrado (Chebyshev) ou losango (Manhattan)
  private enum ReachShape {
    SQUARE, DIAMOND
  }

  // Permite alterar a forma de alcance para cada modo facilmente
  private ReachShape moveShape = ReachShape.DIAMOND;
  private ReachShape attackShape = ReachShape.DIAMOND;
  private ReachShape sonarShape = ReachShape.SQUARE;

  // Setters para que você possa mudar o alcance e a forma em runtime
  public void setMoveRange(int r) {
    moveRange = Math.max(0, r);
  }

  public void setAttackRange(int r) {
    attackRange = Math.max(0, r);
  }

  public void setSonarRange(int r) {
    sonarRange = Math.max(0, r);
  }

  public void setMoveShapeToSquare() {
    moveShape = ReachShape.SQUARE;
  }

  public void setMoveShapeToDiamond() {
    moveShape = ReachShape.DIAMOND;
  }

  public void setAttackShapeToSquare() {
    attackShape = ReachShape.SQUARE;
  }

  public void setAttackShapeToDiamond() {
    attackShape = ReachShape.DIAMOND;
  }

  public void setSonarShapeToSquare() {
    sonarShape = ReachShape.SQUARE;
  }

  public void setSonarShapeToDiamond() {
    sonarShape = ReachShape.DIAMOND;
  }

  // Marcações de sonar (permanentes) e contadores de uso do jogador
  private boolean[][] sonarMarked = new boolean[BOARD_SIZE][BOARD_SIZE];
  private int sonaresPlaced = 0; // quantos sonares este cliente já colocou (confirmados)
  private int pendingSonares = 0; // requisições de sonar enviadas aguardando confirmação
  private static final int MAX_SONARES = 4;
  // Grid que guarda o id do sonar (0 = nenhum). IDs válidos: 1..MAX_SONARES
  private int[][] sonarIdGrid = new int[BOARD_SIZE][BOARD_SIZE];
  // Coordenadas dos sonares deste jogador por id (1..MAX_SONARES)
  private java.awt.Point[] mySonarCoords = new java.awt.Point[MAX_SONARES + 1];
  // Flags indicando se um sonar (por id) detectou alguém (para colorir)
  private boolean[] mySonarDetected = new boolean[MAX_SONARES + 1];
  // Grid indicando se uma célula de sonar foi marcada como tendo detectado
  // atividade no turno atual. Essas marcações duram até o próximo TURNO
  // (quando são resetadas). O servidor envia DETECTADO a cada turno para
  // sonares que detectaram, então limpamos aqui no início de cada TURNO
  // e deixamos o servidor re-sinalizar os que devem permanecer detectados.
  private boolean[][] sonarDetected = new boolean[BOARD_SIZE][BOARD_SIZE];

  // Duração (ms) da marcação visual de ataque (X). Fica visível por 3 segundos.
  private static final int ATTACK_MARK_MS = 3000;

  // Size (pixels) of each board cell. Change this to adjust square width.
  // You can also call setCellSize(...) at runtime before showing the game.
  private int cellSize = 32;

  private Boolean playerTurn = true; // controle de turno (null = aguardando/reservado)
  private JLabel turnoLabel; // label de status do turno
  private ClientConnection connection; // conexão com servidor (opcional)
  private String nomeJogadorTurnoAtual;

  private enum Modo {
    MOVER, ATACAR, SONAR
  }

  private Modo modoAtual = Modo.MOVER;

  private String token;

  // Keepalive scheduler for periodic pings to the server
  private ScheduledExecutorService keepaliveScheduler = null;
  private static final long KEEPALIVE_INTERVAL_SECONDS = 30;

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

  // Inicia o agendador de keepalive (se já não iniciado). Envia
  // periodicamente "KEEPALIVE <jogadorAtual> <token>" ao servidor.
  private void startKeepalive() {
    if (keepaliveScheduler != null && !keepaliveScheduler.isShutdown())
      return; // já rodando
    keepaliveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "keepalive-sender");
      t.setDaemon(true);
      return t;
    });
    keepaliveScheduler.scheduleAtFixedRate(() -> {
      try {
        if (connection != null && connection.isConnected() && jogadorAtual != null && token != null) {
          connection.sendLine("KEEPALIVE " + jogadorAtual + " " + token);
        }
      } catch (Exception ignore) {
      }
    }, KEEPALIVE_INTERVAL_SECONDS, KEEPALIVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  private void stopKeepalive() {
    try {
      if (keepaliveScheduler != null) {
        keepaliveScheduler.shutdownNow();
        keepaliveScheduler = null;
      }
    } catch (Exception ignore) {
    }
  }

  private JPanel criarTelaLogin() {
    JPanel container = new JPanel(new GridBagLayout());
    container.setBorder(new EmptyBorder(24, 24, 24, 24));

    JPanel box = new JPanel();
    box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
    box.setBorder(new EmptyBorder(16, 16, 16, 16));
    box.setBackground(new Color(245, 248, 255));
    box.setMaximumSize(new Dimension(700, 200));

    JLabel titulo = new JLabel("Batalha Subaquática");
    titulo.setAlignmentX(Component.CENTER_ALIGNMENT);
    titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));

    JLabel rotulo = new JLabel("Digite seu nome de jogador:");
    rotulo.setBorder(new EmptyBorder(12, 0, 4, 0));
    // Envolver o rótulo em um container para forçar alinhamento à esquerda
    JPanel rotuloContainer = new JPanel(new BorderLayout());
    rotuloContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
    rotuloContainer.add(rotulo, BorderLayout.WEST);
    rotuloContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, rotulo.getPreferredSize().height));

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
    box.add(rotuloContainer);
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
    Dimension fixedSize = new Dimension(350, 160);
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

    // Adiciona a caixa de desafios recebidos acima do conteúdo central
    centro.add(leftTopBox);
    centro.add(Box.createVerticalStrut(12));

    // --- Painel de Partidas Públicas (caixa vertical) ---
    publicGamesPanel = new JPanel();
    publicGamesPanel.setLayout(new BoxLayout(publicGamesPanel, BoxLayout.Y_AXIS));
    publicGamesPanel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(220, 220, 220)),
        new EmptyBorder(8, 8, 8, 8)));
    Dimension publicSize = new Dimension(300, 200);
    publicGamesPanel.setPreferredSize(publicSize);
    publicGamesPanel.setMaximumSize(new Dimension(publicSize.width, 10000));
    JLabel pgTitle = new JLabel("Partidas públicas");
    pgTitle.setFont(pgTitle.getFont().deriveFont(Font.BOLD, 12f));
    pgTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
    publicGamesPanel.add(pgTitle);
    publicGamesPanel.add(Box.createVerticalStrut(6));
    // painel que conterá as linhas das partidas dentro de um scroll
    JPanel publicGamesList = new JPanel();
    publicGamesList.setLayout(new BoxLayout(publicGamesList, BoxLayout.Y_AXIS));
    publicGamesList.setAlignmentX(Component.LEFT_ALIGNMENT);
    JScrollPane publicScroll = new JScrollPane(publicGamesList,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    publicScroll.setPreferredSize(new Dimension(publicSize.width - 8, 220));
    publicGamesPanel.add(publicScroll);
    publicGamesPanel.add(Box.createVerticalStrut(6));

    // Guardar referência ao painel interno (onde as linhas serão adicionadas)
    // usando the name property so we can find it later when updating
    publicGamesList.setName("_publicGamesList");

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

    // Botão de sair no canto superior direito da Home. Envia "SAIR <jogador>
    // <token>"
    // ao servidor (se conectado), fecha a conexão, limpa o estado do cliente e
    // retorna para a tela de cadastro.
    JButton logoutBtn = new JButton("Sair");
    logoutBtn.addActionListener(e -> {
      // Envia o comando SAIR ao servidor, se possível, e fecha a conexão.
      if (connection != null && connection.isConnected()) {
        connection.sendLine("SAIR " + (jogadorAtual == null ? "" : jogadorAtual) + " " + (token == null ? "" : token));
        try {
          connection.close();
        } catch (IOException ex) {
          // Ignorar – apenas uma tentativa de limpeza
        }
        stopKeepalive();
      }

      // Limpar estado local do cliente
      jogadorAtual = null;
      token = null;
      connection = null;
      jogadoresModel.clear();
      if (mensagensArea != null)
        mensagensArea.setText("");
      if (chatInputField != null)
        chatInputField.setText("");
      if (infoJogadorLabel != null)
        infoJogadorLabel.setText("");
      if (loginStatusLabel != null) {
        loginStatusLabel.setForeground(new Color(120, 0, 0));
        loginStatusLabel.setText("Desconectado");
      }
      if (loginButton != null)
        loginButton.setEnabled(true);

      // Resetar botões de desafio/UI relacionada
      resetChallengeButtons();

      // Voltar para a tela de login
      cardLayout.show(root, "login");
    });
    northContainer.add(logoutBtn, BorderLayout.EAST);

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
    // Colocar um painel intermediário para acomodar o centro e, à sua direita,
    // a caixa de Partidas Públicas (assim fica entre o centro e a lista de
    // jogadores que está no EAST).
    JPanel mid = new JPanel(new BorderLayout());
    mid.add(centro, BorderLayout.CENTER);
    mid.add(publicGamesPanel, BorderLayout.EAST);
    home.add(mid, BorderLayout.CENTER);
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

  // Reseta o estado dos botões de desafio (volta para "Desafiar") e limpa
  // indicação de desafio enviado. Deve ser chamado sempre que entramos na
  // Home ou iniciamos uma partida para garantir consistência da UI.
  private void resetChallengeButtons() {
    // Limpa indicação de desafio enviado
    desafioEnviadoPara = null;

    // Restaura todos os botões para o estado padrão
    if (playerButtons != null) {
      for (java.util.Map.Entry<String, JButton> e : playerButtons.entrySet()) {
        JButton b = e.getValue();
        if (b != null) {
          b.setText("Desafiar");
          b.setEnabled(true);
        }
      }
    }

    // Remove linha de saída de desafio, se existir
    if (outgoingChallengeRow != null && incomingChallengesPanel != null) {
      incomingChallengesPanel.remove(outgoingChallengeRow);
      outgoingChallengeRow = null;
      incomingChallengesPanel.revalidate();
      incomingChallengesPanel.repaint();
    }

    // Resetar botões de partidas públicas (se existirem)
    if (publicGameButtons != null) {
      for (java.util.Map.Entry<String, JButton> e : publicGameButtons.entrySet()) {
        JButton b = e.getValue();
        if (b != null) {
          b.setText("entrar");
          b.setEnabled(true);
        }
      }
      entrouPartidaId = null;
    }
  }

  // Wrapper para mostrar a tela Home garantindo que os botões de desafio
  // sejam resetados antes de exibir a UI.
  private void showHome() {
    // Reset local 'morto' flag when returning to Home
    jogadorMorto = false;
    resetChallengeButtons();
    cardLayout.show(root, "home");
  }

  // Wrapper para mostrar a tela de jogo. Também reseta os botões para evitar
  // que um estado anterior (ex.: "Enviado") permaneça ao retornar à Home.
  private void showGame() {
    resetChallengeButtons();
    cardLayout.show(root, "game");
  }

  private JPanel criarLinhaJogador(String nome, boolean habilitarBotao) {
    JPanel linha = new JPanel(new BorderLayout());
    linha.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
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
    // Cria uma linha superior que contém: à esquerda os controles de modo e
    // à direita o label de turno. Abaixo desta linha vem as mensagens da partida.
    JPanel configuracoesLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

    // Seleção de modo
    btMoverField = new JToggleButton("Mover");
    btAtacarField = new JToggleButton("Atacar");
    btSonarField = new JToggleButton("Sonar");
    ButtonGroup grupoModo = new ButtonGroup();
    grupoModo.add(btMoverField);
    grupoModo.add(btAtacarField);
    grupoModo.add(btSonarField);
    btMoverField.setSelected(true);
    btMoverField.addActionListener(e -> {
      modoAtual = Modo.MOVER;
      // atualizar destaques imediatamente ao mudar o modo
      atualizarTabuleiro();
    });
    btAtacarField.addActionListener(e -> {
      modoAtual = Modo.ATACAR;
      atualizarTabuleiro();
    });
    btSonarField.addActionListener(e -> {
      modoAtual = Modo.SONAR;
      atualizarTabuleiro();
    });

    configuracoesLeft.add(new JLabel(" | Modo:"));
    configuracoesLeft.add(btMoverField);
    configuracoesLeft.add(btAtacarField);
    configuracoesLeft.add(btSonarField);

    // label de turno exibido na mesma linha, à direita
    turnoLabel = new JLabel("Turno: Jogador");
    turnoLabel.setFont(turnoLabel.getFont().deriveFont(Font.BOLD, 18f));

    // Painel superior com left (modos) e center (turno centralizado)
    JPanel topConfigRow = new JPanel(new BorderLayout());
    topConfigRow.add(configuracoesLeft, BorderLayout.WEST);
    // painel do centro contendo o label do turno centralizado
    JPanel centerTurno = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    centerTurno.setOpaque(false);
    turnoLabel.setHorizontalAlignment(SwingConstants.CENTER);
    centerTurno.add(turnoLabel);
    topConfigRow.add(centerTurno, BorderLayout.CENTER);

    // Criar painel norte que contém a linha de configurações e, abaixo dela,
    // a caixa de mensagens da partida (evita popups para mensagens do servidor).
    JPanel northPanel = new JPanel();
    northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
    northPanel.add(topConfigRow);

    // Área de mensagens da partida (pequena, rolável)
    JPanel gameMsgPanel = new JPanel(new BorderLayout());
    gameMsgPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
    JLabel gmLabel = new JLabel("Mensagens da partida");
    gmLabel.setFont(gmLabel.getFont().deriveFont(Font.BOLD, 12f));
    gameMessagesArea = new JTextArea(3, 40);
    gameMessagesArea.setEditable(false);
    gameMessagesArea.setLineWrap(true);
    gameMessagesArea.setWrapStyleWord(true);
    JScrollPane gmScroll = new JScrollPane(gameMessagesArea,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    gmScroll.getVerticalScrollBar().setUnitIncrement(12);
    gameMsgPanel.add(gmLabel, BorderLayout.NORTH);
    gameMsgPanel.add(gmScroll, BorderLayout.CENTER);
    northPanel.add(gameMsgPanel);

    tela.add(northPanel, BorderLayout.NORTH);

    // Tabuleiro (16x16)
    // Use zero gaps so cells are "juntinhos" (tight together)
    JPanel boardPanel = new JPanel(new GridLayout(BOARD_SIZE, BOARD_SIZE, 1, 1));
    // guarda referência para otimizar atualizações em lote
    boardPanelRef = boardPanel;
    boardPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        final int cx = x, cy = y;
        JButton b = new JButton();
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setPreferredSize(new Dimension(32, 32));
        b.setFocusPainted(false);
        // Garantir que o background seja pintado corretamente ao mudar cores
        b.setOpaque(true);
        b.setBackground(Color.WHITE);
        b.addActionListener(e -> onCellClick(cx, cy));
        boardButtons[y][x] = b;
        boardPanel.add(b);
      }
    }
    tela.add(boardPanel, BorderLayout.CENTER);

    // Barra inferior: ações (sem label de turno — agora mostrado no topo)
    JPanel status = new JPanel(new BorderLayout());
    status.setBorder(new EmptyBorder(4, 8, 4, 8));

    // Botão para passar o turno (envia apenas a linha PASSAR <jogadorAtual>
    // <token>)
    JButton passarTurno = new JButton("Passar turno");
    passarTurno.addActionListener(e -> {
      if (connection != null && connection.isConnected()) {
        connection.sendLine("PASSAR " + jogadorAtual + " " + token);
      } else {
        JOptionPane.showMessageDialog(this, "Sem conexão com o servidor.", "Passar turno", JOptionPane.WARNING_MESSAGE);
      }
    });

    // Botão para sair da partida: envia SAIRPARTIDA e volta para a tela Home
    sairPartidaBtn = new JButton("Sair da partida");
    sairPartidaBtn.addActionListener(e -> {
      if (connection != null && connection.isConnected()) {
        connection.sendLine("SAIRPARTIDA " + jogadorAtual + " " + token);
      }
      // Volta para a Home independentemente da conexão (UI)
      showHome();
    });

    // Container à direita com botões de ação
    JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    rightActions.add(passarTurno);
    rightActions.add(sairPartidaBtn);

    status.add(rightActions, BorderLayout.EAST);

    tela.add(status, BorderLayout.SOUTH);
    return tela;
  }

  private void iniciarPartida() {
    // reinicia estado
    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        atacado[y][x] = false;
        sonarMarked[y][x] = false;
        sonarDetected[y][x] = false;
      }
    }
    // jogador volta a estar vivo no início da partida
    jogadorMorto = false;
    playerX = clamp(startX, 0, BOARD_SIZE - 1);
    playerY = clamp(startY, 0, BOARD_SIZE - 1);
    playerTurn = true;
    sonaresPlaced = 0;
    pendingSonares = 0;
    // limpa ids e detections
    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        sonarIdGrid[y][x] = 0;
      }
    }
    for (int i = 1; i <= MAX_SONARES; i++) {
      mySonarCoords[i] = null;
      mySonarDetected[i] = false;
    }
    atualizarStatusTurno();
    atualizarTabuleiro();
  }

  private void onCellClick(int x, int y) {
    // debug println removed to reduce console overhead
    // Se não houver conexão com o servidor, bloquear qualquer ação local.
    if (connection == null || !connection.isConnected()) {
      JOptionPane.showMessageDialog(this, "Sem conexão com o servidor. Conecte-se para jogar.", "Sem conexão",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    // Se o jogador estiver morto nesta partida, bloquear qualquer ação
    if (jogadorMorto) {
      JOptionPane.showMessageDialog(this, "Você está morto nesta partida e não pode agir.", "Inativo",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    // Se o turno for nulo (reservado) ou não for o jogador atual, não permite ação
    if (playerTurn == null || !playerTurn)
      return; // aguarda inimigo ou reserva

    switch (modoAtual) {
      case MOVER:
        // Não atualizar posição local aqui — enviamos ao servidor e esperamos a
        // confirmação/atualização via mensagem MOVER do servidor.
        if (connection != null && connection.isConnected()) {
          connection.sendLine("MOVER " + jogadorAtual + " " + token + " " + x + " " + y);
          // Immediately reset sonar visuals to 'S' (clear detections) and
          // trust the server to re-send DETECTADO for any sonars that detect.
          clearAllSonarDetections();
          // Ainda encerramos o turno localmente: o servidor controlará a próxima
          // reabilitação via mensagem "TURNO".
          fimDoTurnoDoJogador();
        }
        break;
      case ATACAR:
        // Não aplicar efeito local imediatamente — envie ao servidor e aguarde
        // confirmação (mensagem "ATACAR") para desenhar o X temporário.
        if (connection != null && connection.isConnected()) {
          connection.sendLine("ATACAR " + jogadorAtual + " " + token + " " + x + " " + y);
          // Clear detections so sonars show as 'S' until server says otherwise
          clearAllSonarDetections();
          fimDoTurnoDoJogador();
        }
        break;
      case SONAR:
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
          // Clear detections immediately so sonars revert to 'S' until server
          // sends DETECTADO messages again.
          clearAllSonarDetections();
          fimDoTurnoDoJogador();
        }
        break;
    }
  }

  private void fimDoTurnoDoJogador() {
    // Do NOT automatically send PASSAR here. Previously this method sent
    // PASSAR to the server after every action, which caused every move/attack/
    // sonar to also send a PASSAR. PASSAR should be sent explicitly by the
    // user (via the "Passar turno" button) or by higher-level game logic.
    if (connection != null && connection.isConnected()) {
      // After performing an action we block local input and wait for the
      // server to inform the next turn (server will send a TURNO message).
      playerTurn = null; // waiting for server
      atualizarStatusTurno();
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
        texto = "Turno: aguardando todos jogadores carregarem";
      } else {
        texto = playerTurn ? "Seu turno: " + nomeJogadorTurnoAtual : "Turno Inimigo: " + nomeJogadorTurnoAtual;
      }
      turnoLabel.setText(texto);
    }
    // Opcional: desabilitar o tabuleiro quando não é o turno do jogador
    // Não desabilitamos todos os botões do tabuleiro (custa muito). Em vez
    // disso, mantemos a lógica de aceitação de cliques em onCellClick() que
    // já verifica o turno, e apenas repintamos o painel do tabuleiro uma vez.
    if (boardPanelRef != null) {
      boardPanelRef.repaint();
    }

    // Garantir que os controles de modo e sair da partida permaneçam ativos
    // mesmo quando não for o turno do jogador — o usuário deve poder preparar
    // ações ou sair a qualquer momento.
    if (btMoverField != null)
      btMoverField.setEnabled(true);
    if (btAtacarField != null)
      btAtacarField.setEnabled(true);
    if (btSonarField != null)
      btSonarField.setEnabled(true);
    if (sairPartidaBtn != null)
      sairPartidaBtn.setEnabled(true);
    // Garantir que o tabuleiro seja redesenhado (e.g. mostrar/ocultar os
    // destaques de alcance quando o turno começar/acabar)
    atualizarTabuleiro();
  }

  private void atualizarTabuleiro() {
    // Quando for o turno do jogador mostramos os destaques de alcance.
    boolean showReach = (playerTurn != null && playerTurn);

    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        JButton b = boardButtons[y][x];
        if (b == null)
          continue;
        b.setText("");

        // Prioridade alta: jogador, ataques e sonares já marcados
        if (x == playerX && y == playerY) {
          b.setBackground(new Color(90, 160, 255)); // jogador
          if (jogadorAtual != null && !jogadorAtual.isEmpty()) {
            String nome = jogadorAtual.trim();
            if (!nome.isEmpty()) {
              String first = nome.substring(0, 1).toUpperCase();
              // If the first letter would be 'S' or 'X' (case-insensitive),
              // use 'J' instead to avoid duplicate/confusing symbols on the board.
              if ("S".equals(first) || "X".equals(first)) {
                b.setText("J");
              } else {
                b.setText(first);
              }
            }
          }
          continue;
        }

        if (atacado[y][x]) {
          b.setBackground(new Color(240, 120, 120)); // atacado
          b.setText("X");
          continue;
        }

        // Prioridade: se a célula foi marcada como DETECTADO pelo servidor,
        // mostrar '*' imediatamente (mesmo que sonarMarked não esteja sincronizado
        // por alguma razão). Isso evita que um redraw substitua '*' por 'S'.
        if (sonarDetected[y][x]) {
          b.setBackground(new Color(255, 180, 80)); // detectou alguém (destaque)
          b.setText("*");
          continue;
        }

        if (sonarMarked[y][x]) {
          // Se esta célula foi sinalizada como tendo detectado, destacar
          if (sonarDetected[y][x]) {
            b.setBackground(new Color(255, 180, 80)); // detectou alguém (destaque)
            b.setText("*");
          } else {
            // sem detecção: diferenciar sonares próprios de terceiros visualmente
            int sid = sonarIdGrid[y][x];
            boolean isMy = false;
            if (sid >= 1 && sid <= MAX_SONARES) {
              java.awt.Point mp = mySonarCoords[sid];
              if (mp != null && mp.x == x && mp.y == y)
                isMy = true;
            }
            b.setBackground(isMy ? new Color(200, 230, 150) : new Color(230, 230, 150));
            // Mostrar símbolo S quando não detectado; servidor DETECTADO irá
            // alternar para '*' até que o jogador local realize uma ação
            // que limpe as detecções. Reexibir 'S' por padrão quando não há '*'.
            b.setText("S");
          }
          continue;
        }

        // Se chegou até aqui a célula está 'limpa' — considerar destaques de alcance
        boolean inMove = false, inAttack = false, inSonarReach = false;
        if (showReach) {
          inMove = isInRange(playerX, playerY, x, y, moveRange, moveShape);
          inAttack = isInRange(playerX, playerY, x, y, attackRange, attackShape);
          inSonarReach = isInRange(playerX, playerY, x, y, sonarRange, sonarShape);
        }

        // Determinar cor com base no modo atualmente selecionado.
        // Exibir somente o alcance do modo ativo como destaque forte. Não
        // mostramos os alcances de outros modos para evitar confusão.
        if (showReach) {
          switch (modoAtual) {
            case MOVER:
              if (inMove) {
                b.setBackground(new Color(120, 200, 255)); // movimento
              } else {
                b.setBackground(Color.WHITE);
              }
              break;
            case ATACAR:
              if (inAttack) {
                b.setBackground(new Color(255, 120, 120)); // ataque
              } else {
                b.setBackground(Color.WHITE);
              }
              break;
            case SONAR:
              if (inSonarReach) {
                b.setBackground(new Color(255, 200, 120)); // sonar
              } else {
                b.setBackground(Color.WHITE);
              }
              break;
            default:
              b.setBackground(Color.WHITE);
          }
        } else {
          b.setBackground(Color.WHITE);
        }
      }
    }
  }

  // Limpa todas as marcações de DETECTADO localmente (faz com que sonares
  // voltem a aparecer como 'S' até que o servidor reenvie DETECTADO). Deve ser
  // chamada imediatamente quando o jogador realiza uma ação local (mover/
  // atacar/sonar) para confiar no servidor como autoridade.
  private void clearAllSonarDetections() {
    for (int yy = 0; yy < BOARD_SIZE; yy++) {
      for (int xx = 0; xx < BOARD_SIZE; xx++) {
        sonarDetected[yy][xx] = false;
      }
    }
    // Atualiza a interface imediatamente
    atualizarTabuleiro();
    try {
      if (boardPanelRef != null) {
        boardPanelRef.revalidate();
        boardPanelRef.repaint();
      }
    } catch (Exception ignore) {
    }
  }

  // Verifica se a célula (x,y) está dentro do alcance do centro (cx,cy) para
  // o range e a forma fornecidos.
  private boolean isInRange(int cx, int cy, int x, int y, int range, ReachShape shape) {
    if (range <= 0)
      return false;
    int dx = Math.abs(cx - x);
    int dy = Math.abs(cy - y);
    if (shape == ReachShape.SQUARE) {
      // Chebyshev distance -> quadrado (max)
      return Math.max(dx, dy) <= range;
    } else {
      // DIAMOND: Manhattan distance -> losango
      return (dx + dy) <= range;
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

  // Atualiza a lista de partidas públicas a partir do campo 'partidas:{...}'
  private void atualizarListaPartidasDoServidor(String listaCampo) {
    if (listaCampo == null) {
      listaCampo = "{}";
    }
    // localizar o painel interno onde colocaremos as linhas
    JPanel publicGamesList = null;
    for (java.awt.Component c : publicGamesPanel.getComponents()) {
      if (c instanceof JScrollPane) {
        JScrollPane sp = (JScrollPane) c;
        java.awt.Component vp = sp.getViewport().getView();
        if (vp instanceof JPanel && "_publicGamesList".equals(vp.getName())) {
          publicGamesList = (JPanel) vp;
          break;
        }
      }
    }
    if (publicGamesList == null) {
      return; // sem onde mostrar
    }

    // limpa lista atual
    publicGamesList.removeAll();
    // limpar referências aos botões antigos para evitar leaks; manteremos
    // novo mapa com as novas instâncias abaixo
    publicGameButtons.clear();

    // separar itens dentro de { ... }
    String[] items = separarLista(listaCampo);
    System.out.println(listaCampo);
    if (items == null || items.length == 0) {
      JLabel empty = new JLabel("(nenhuma partida pública)");
      empty.setForeground(new Color(90, 90, 90));
      empty.setAlignmentX(Component.LEFT_ALIGNMENT);
      publicGamesList.add(empty);
    } else {
      for (String it : items) {
        if (it == null || it.trim().isEmpty())
          continue;
        // cada item: id:1,andamento:true,numjogadores:2,maxjogadores:4
        String id = "?";
        String andamento = "?";
        String num = "?";
        String numMax = "?";
        String[] parts = it.split(",");
        for (String p : parts) {
          String[] kv = p.split(":", 2);
          if (kv.length < 2)
            continue;
          String k = kv[0].trim();
          String v = kv[1].trim();
          if ("id".equals(k))
            id = v;
          else if ("andamento".equals(k) || "emprogresso".equals(k))
            andamento = v;
          else if ("numjogadores".equals(k) || "num".equals(k) || "jogadores".equals(k))
            num = v;
          else if ("maxjogadores".equals(k) || "max".equals(k) || "capacidade".equals(k) || "maxplayers".equals(k))
            numMax = v;
        }

        // tornar chave imutável para uso dentro do listener
        final String idKey = id;

        JPanel row = new JPanel(new BorderLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(6, 4, 6, 4));
        String estaAndamento = "true".equals(andamento) ? "em andamento" : "disponível";
        String text = "ID: " + id + " - " + estaAndamento + " - jogadores: " + num + " capacidade: " + numMax;
        JLabel lbl = new JLabel(text);
        row.add(lbl, BorderLayout.CENTER);
        // botão para entrar na partida
        JButton enterBtn = new JButton("entrar");
        enterBtn.addActionListener(ev -> {
          if (connection == null || !connection.isConnected()) {
            JOptionPane.showMessageDialog(this, "Sem conexão com o servidor.", "Entrar partida",
                JOptionPane.WARNING_MESSAGE);
            return;
          }
          if (jogadorAtual == null || jogadorAtual.isEmpty() || token == null || token.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Você precisa estar logado para entrar em partidas.", "Entrar partida",
                JOptionPane.WARNING_MESSAGE);
            return;
          }

          // Resetar botão anterior, se existir e for diferente
          if (entrouPartidaId != null && !entrouPartidaId.equals(idKey)) {
            JButton prev = publicGameButtons.get(entrouPartidaId);
            if (prev != null) {
              prev.setText("entrar");
              prev.setEnabled(true);
            }
          }

          // Disable this button while awaiting server response to avoid duplicate
          // requests.
          enterBtn.setEnabled(false);

          // Envia o comando para o servidor. The button text will only change to
          // "entrou" when the server confirms with an ENTARPARTIDA|200 response.
          connection.sendLine("ENTRARPARTIDA " + jogadorAtual + " " + token + " " + idKey);
        });

        // Se já estivermos marcados como tendo entrado nesta partida, ajustar o rótulo
        if (entrouPartidaId != null && entrouPartidaId.equals(idKey)) {
          enterBtn.setText("entrou");
        }

        publicGameButtons.put(idKey, enterBtn);
        row.add(enterBtn, BorderLayout.EAST);
        publicGamesList.add(row);
        publicGamesList.add(Box.createVerticalStrut(4));
      }
    }

    publicGamesList.revalidate();
    publicGamesList.repaint();
  }

  private int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
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
          // Só atualiza para "Conectado..." se ainda estivermos no estado de
          // conexão — assim não sobrescrevemos uma mensagem que o servidor
          // já tenha enviado (por exemplo: falha no cadastro).
          if (loginStatusLabel != null) {
            String atual = loginStatusLabel.getText();
            if (atual == null || atual.startsWith("Conectando")) {
              loginStatusLabel.setForeground(new Color(0, 120, 0));
              loginStatusLabel.setText("Conectado. Aguardando resposta do servidor...");
            }
          }

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

  // Append a message to the partida (game) message area. If the game area
  // is not available (e.g. user is on Home), fall back to the main mensagens
  // area so the message isn't lost.
  private void appendGameMessage(String msg) {
    if (msg == null)
      return;
    if (gameMessagesArea != null) {
      gameMessagesArea.append(msg + "\n");
      gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
    } else if (mensagensArea != null) {
      // fallback so messages are visible even if game UI not open
      mensagensArea.append(msg + "\n");
      mensagensArea.setCaretPosition(mensagensArea.getDocument().getLength());
    }
  }

  // Função que separa o valor de um campo específico da linha
  private String separarValores(String line, String campo) {
    if (line == null || campo == null)
      return null;

    String temp = line;
    int startIndex = temp.indexOf(campo + ":");
    if (startIndex == -1) {
      return null; // Campo não encontrado
    }

    startIndex += (campo + ":").length();

    // pular espaços em branco após ':'
    while (startIndex < temp.length() && Character.isWhitespace(temp.charAt(startIndex))) {
      startIndex++;
    }

    if (startIndex >= temp.length())
      return null;

    // Se o valor começa com '{', procure o '}' correspondente (suporta chaves
    // aninhadas)
    if (temp.charAt(startIndex) == '{') {
      int depth = 0;
      for (int i = startIndex; i < temp.length(); i++) {
        char c = temp.charAt(i);
        if (c == '{')
          depth++;
        else if (c == '}') {
          depth--;
          if (depth == 0) {
            return temp.substring(startIndex, i + 1);
          }
        }
      }
      // Se não encontramos o fechamento, retornar null em vez de truncar
      return null;
    }

    // Caso normal: valor sem chaves — termina na próxima vírgula ou fim da string
    int endIndex = temp.indexOf(',', startIndex);
    if (endIndex == -1) {
      return temp.substring(startIndex);
    }
    return temp.substring(startIndex, endIndex);
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
      String valoresServer = parts.length > 3 ? parts[3] : "";

      System.out.println("Recebido do servidor: " + line);

      // 1) Fluxo de cadastro/login usando pipe
      switch (comandoServer) {
        case "CADASTRAR": {
          if ("201".equals(codigoServer)) {
            // Sucesso: avançar para HOME
            atualizarListaJogadores();
            showHome();
            if (loginButton != null)
              loginButton.setEnabled(true);
            if (loginStatusLabel != null)
              loginStatusLabel.setText(" ");

            // Salvando o token
            token = separarValores(valoresServer, "token");

            // Inicia envio periódico de KEEPALIVE (apenas após termos token)
            if (token != null && !token.isEmpty()) {
              startKeepalive();
            }

            // Após entrar na Home, solicitar lista atual de jogadores
            if (connection != null && connection.isConnected()) {
              connection.sendLine("LISTARJOGADORES");
              connection.sendLine("LISTARPARTIDAS");
            }
          } else {
            // Falha: mostrar a mensagem do servidor na label de status (sem popup)
            String motivo = (textoServer != null && !textoServer.isEmpty()) ? textoServer : "Cadastro não realizado";
            if (loginStatusLabel != null) {
              loginStatusLabel.setForeground(new Color(120, 0, 0));
              loginStatusLabel.setText(motivo);
            }
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
        case "LISTARPARTIDAS": {
          if ("200".equals(codigoServer)) {
            String partidasCampo = separarValores(valoresServer, "partidas");
            System.out.println("Partidas públicas recebidas: " + partidasCampo);
            atualizarListaPartidasDoServidor(partidasCampo);
          } else {
            if (mensagensArea != null) {
              mensagensArea.append("Falha ao listar partidas: " + textoServer + "\n");
            }
          }
          break;
        }
        case "ENTRARPARTIDA": {
          // Server response to a request to enter a public game
          String id = separarValores(valoresServer, "id");
          if (id == null || id.isEmpty()) {
            // Fallback: some servers may use 'partida' or 'game'
            id = separarValores(valoresServer, "partida");
            if (id == null || id.isEmpty())
              id = separarValores(valoresServer, "game");
          }

          if ("200".equals(codigoServer)) {
            if (id != null && publicGameButtons.containsKey(id)) {
              JButton b = publicGameButtons.get(id);
              if (b != null) {
                b.setText("entrou");
                b.setEnabled(false);
              }
            }
            entrouPartidaId = id;
            if (mensagensArea != null) {
              mensagensArea.append("Entrou na partida: " + (id == null ? "?" : id) + "\n");
            }
          } else {
            // Não altere para 'entrou' em caso de falha; reabilitar botão se existia
            if (id != null && publicGameButtons.containsKey(id)) {
              JButton b = publicGameButtons.get(id);
              if (b != null) {
                b.setText("entrar");
                b.setEnabled(true);
              }
            }
            if (mensagensArea != null) {
              mensagensArea.append("Falha ao entrar na partida: " + textoServer + "\n");
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
          String fromP = separarValores(valoresServer, "nome");
          String msgP = separarValores(valoresServer, "mensagem");
          String displayP = (fromP != null && !fromP.isEmpty()) ? ("[Partida] " + fromP + ": " + msgP)
              : ("[Partida] " + msgP);
          if (displayP != null && !displayP.isEmpty()) {
            appendGameMessage(displayP);
          }
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
          showGame();

          // Depois de carregar a tela, notifica o servidor que estamos prontos
          if (connection != null && connection.isConnected()) {
            connection.sendLine("PRONTO " + jogadorAtual + " " + token);
          }
          break;
        }
        case "INICIOPARTIDA": {
          // O servidor indica que a partida foi iniciada (todos prontos).
          appendGameMessage("Partida iniciada");
          // Certifica-se de mostrar a tela do jogo
          showGame();
          // Durante o início o servidor enviará o TURNO logo em seguida
          break;
        }
        case "MOVER": {
          // Servidor informa movimento (x,y).
          // Se o servidor respondeu 408 => posição inválida: não aplicar nada
          // e reabilitar o jogador para tentar novamente. Para qualquer outro
          // código consideramos confirmação e aplicamos a posição.
          String xStr = separarValores(valoresServer, "x");
          String yStr = separarValores(valoresServer, "y");
          if ("400".equals(codigoServer)) {
            // Movimento inválido: reabilita o jogador para tentar novamente
            playerTurn = true;
            atualizarStatusTurno();
            if (gameMessagesArea != null) {
              gameMessagesArea.append("Movimento inválido.\n");
              gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
            }
          } else {
            // Apenas aplique a nova posição se o servidor realmente forneceu
            // as coordenadas x e y; caso contrário ignoramos para evitar zerar
            // a posição (0,0) quando o servidor não retornou valores.
            if (xStr != null && yStr != null) {
              try {
                int mx = Integer.parseInt(xStr);
                int my = Integer.parseInt(yStr);
                playerX = clamp(mx, 0, BOARD_SIZE - 1);
                playerY = clamp(my, 0, BOARD_SIZE - 1);
                atualizarTabuleiro();
              } catch (NumberFormatException nfe) {
                if (gameMessagesArea != null) {
                  gameMessagesArea
                      .append("Movimento confirmado, mas coordenadas inválidas recebidas; ignorando atualização.\n");
                  gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
                }
              }
            } else {
              if (gameMessagesArea != null) {
                gameMessagesArea
                    .append("Movimento confirmado, mas servidor não retornou coordenadas; posição mantida.\n");
                gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
              }
            }
          }
          break;
        }
        case "ATACAR": {
          // Servidor indica um ataque em (x,y). Tratamos código 408 como ataque
          // inválido (não faz nada); outros códigos causam a marcação temporária.
          if ("400".equals(codigoServer)) {
            playerTurn = true;
            atualizarStatusTurno();
            if (gameMessagesArea != null) {
              gameMessagesArea.append("Ataque inválido.\n");
              gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
            }
            break;
          }
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
            // Marca o ataque temporariamente na UI por ATTACK_MARK_MS milissegundos.
            final int fx = ax;
            final int fy = ay;
            atacado[fy][fx] = true;
            atualizarTabuleiro();
            // Timer para limpar a marcação após o período definido
            Timer clearTimer = new Timer(ATTACK_MARK_MS, ev -> {
              atacado[fy][fx] = false;
              atualizarTabuleiro();
              ((Timer) ev.getSource()).stop();
            });
            clearTimer.setRepeats(false);
            clearTimer.start();
          }
          break;
        }
        case "SONAR": {
          // Simplified sonar handling: trust the server.
          // If the placement was invalid, re-enable the player and fix counters.
          if ("400".equals(codigoServer)) {
            // Position invalid for sonar. The server may omit the "nome"
            // field in the error; restore local state so the player can
            // retry (similar to MOVER/ATACAR handling).
            if (pendingSonares > 0)
              pendingSonares--;
            playerTurn = true;
            atualizarStatusTurno();
            if (gameMessagesArea != null) {
              gameMessagesArea.append("Posição de sonar inválida.\n");
              gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
            }
            break;
          }

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
            // Marca o sonar; não alteramos sonarDetected aqui. Confiamos no
            // servidor para enviar DETECTADO quando um sonar detectar alguém.
            sonarMarked[sy][sx] = true;

            // Se for meu sonar, ajustar contadores mínimos para manter UX
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
          // O servidor confirma que o jogador passou o turno ou notifica passagem.
          if ("200".equals(codigoServer)) {
            appendGameMessage("Turno passado.");
            // Aguardamos o servidor enviar o próximo TURNO para reabilitar quem for
          } else {
            // Mostra falha se houver
            appendGameMessage("Falha ao passar turno: " + textoServer);
          }
          break;
        }
        case "SAIRPARTIDA": {
          // Servidor confirmou saída de partida — voltar para a Home e limpar estado
          if ("200".equals(codigoServer)) {
            appendGameMessage("Você saiu da partida.");
            playerTurn = false;
            atualizarStatusTurno();
            cardLayout.show(root, "home");
          } else {
            // informar erro
            String txt = textoServer != null && !textoServer.isEmpty() ? textoServer : "Erro ao sair da partida";
            if (gameMessagesArea != null) {
              gameMessagesArea.append(txt + "\n");
              gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
            }
          }
          break;
        }
        case "SAIR": {
          // Servidor confirmou logout ou desconexão
          if ("200".equals(codigoServer) || "204".equals(codigoServer) || "409".equals(codigoServer)) {
            try {
              // stop keepalive before closing connection
              stopKeepalive();
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
              loginStatusLabel.setText("Desconectado");
            }
            if (loginButton != null) {
              loginButton.setEnabled(true);
            }
            cardLayout.show(root, "login");
          } else {
            JOptionPane.showMessageDialog(this,
                textoServer != null && !textoServer.isEmpty() ? textoServer : "Servidor pediu saída", "Sair",
                JOptionPane.INFORMATION_MESSAGE);
          }
          break;
        }
        case "DETECTADO": {
          // Servidor informa que um sonar detectou alguém. Espera-se um campo
          // num:<id> indicando qual sonar (id) detectou.
          String numStr = separarValores(valoresServer, "num");
          String sxStr = separarValores(valoresServer, "x");
          String syStr = separarValores(valoresServer, "y");
          int sx = -1, sy = -1;
          try {
            sx = Integer.parseInt(sxStr == null ? "-1" : sxStr);
            sy = Integer.parseInt(syStr == null ? "-1" : syStr);
          } catch (NumberFormatException e) {
            // dados inválidos — ignorar
            break;
          }

          // marca o sonar como existente e sinaliza detecção nesta célula
          sonarMarked[sy][sx] = true;
          sonarDetected[sy][sx] = true;

          // Forçar o texto e cor da célula imediatamente para '*' (detecção)
          try {
            JButton cellBtn = boardButtons[sy][sx];
            if (cellBtn != null) {
              cellBtn.setBackground(new Color(255, 180, 80));
              cellBtn.setText("*");
            }
          } catch (Exception ignore) {
          }

          // se o servidor forneceu um id, registre-o; também atualize
          // as coordenadas locais desse id se for do próprio jogador
          if (numStr != null) {
            try {
              int id = Integer.parseInt(numStr);
              if (id >= 1 && id <= MAX_SONARES) {
                sonarIdGrid[sy][sx] = id;
                java.awt.Point prev = mySonarCoords[id];
                if (prev == null || prev.x != sx || prev.y != sy) {
                  mySonarCoords[id] = new java.awt.Point(sx, sy);
                }
                // marque também a flag por id (útil para lógica antiga)
                mySonarDetected[id] = true;
              }
            } catch (NumberFormatException ignore) {
            }
          }

          atualizarTabuleiro();
          // Forçar atualização visual imediata do painel do tabuleiro
          try {
            if (boardPanelRef != null) {
              boardPanelRef.revalidate();
              boardPanelRef.repaint();
            }
          } catch (Exception ignore) {
          }
          if (gameMessagesArea != null) {
            String msg = "Sonar detectou atividade";
            if (numStr != null)
              msg += " id:" + numStr;
            msg += ".";
            appendGameMessage(msg);
          }
          break;
        }
        case "ACERTO": {
          // Servidor notifica que um míssil acertou jogadores. Valores:
          // jogadores:{nome:...,x:...,y:...;...}
          String lista = separarValores(valoresServer, "jogadores");
          String[] items = separarLista(lista);
          if (items != null && items.length > 0) {
            for (String it : items) {
              String nome = separarValores(it, "nome");
              String sx = separarValores(it, "x");
              String sy = separarValores(it, "y");
              String linha = "Míssil acertou: " + (nome == null ? "?" : nome) + " em (" + sx + "," + sy + ")";
              if (gameMessagesArea != null) {
                gameMessagesArea.append(linha + "\n");
                gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
              }
              // Marcação visual opcional para o dono do míssil
              try {
                int ix = Integer.parseInt(sx == null ? "-1" : sx);
                int iy = Integer.parseInt(sy == null ? "-1" : sy);
                if (ix >= 0 && ix < BOARD_SIZE && iy >= 0 && iy < BOARD_SIZE) {
                  // Marcar o local atingido temporariamente e limpar após ATTACK_MARK_MS
                  final int fx = ix;
                  final int fy = iy;
                  atacado[fy][fx] = true;
                  atualizarTabuleiro();
                  Timer clearTimer = new Timer(ATTACK_MARK_MS, ev -> {
                    atacado[fy][fx] = false;
                    atualizarTabuleiro();
                    ((Timer) ev.getSource()).stop();
                  });
                  clearTimer.setRepeats(false);
                  clearTimer.start();
                }
              } catch (NumberFormatException ignore) {
              }
            }
          }
          break;
        }
        case "MORTE": {
          // Servidor avisa que este jogador foi morto (valores incluem dono:<nome>)
          String dono = separarValores(valoresServer, "dono");
          String mensagem = textoServer != null && !textoServer.isEmpty() ? textoServer
              : "Você foi acertado por um míssil";
          if (dono != null && !dono.isEmpty())
            mensagem += " por " + dono;
          if (gameMessagesArea != null) {
            gameMessagesArea.append("Você foi morto: " + mensagem + "\n");
            gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
          }
          // Marcar como morto localmente, remover do tabuleiro e continuar na tela de
          // jogo
          jogadorMorto = true;
          // Remove o marcador do jogador do tabuleiro
          playerX = -1;
          playerY = -1;
          // Desabilitar ações locais (não é mais turno) e atualizar UI
          playerTurn = false;
          atualizarStatusTurno();
          // Repaint/atualiza tabuleiro para remover o jogador visualmente
          atualizarTabuleiro();
          break;
        }
        case "VITORIA": {
          // Recebeu notificação de vitória pessoal
          String msg = textoServer != null && !textoServer.isEmpty() ? textoServer : "Você venceu!";
          // Mostrar popup de vitória
          JOptionPane.showMessageDialog(this, msg, "Vitória", JOptionPane.INFORMATION_MESSAGE);
          // Também registrar na caixa de mensagens da partida, se existir
          if (gameMessagesArea != null) {
            gameMessagesArea.append("Vitória: " + msg + "\n");
            gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
          }
          // Voltar para a Home
          playerTurn = false;
          atualizarStatusTurno();
          showHome();
          break;
        }
        case "FIMPARTIDA": {
          // Partida finalizada para todos; valor deve conter vencedor:<nome>
          String vencedor = separarValores(valoresServer, "vencedor");
          String msgFim = (vencedor != null && !vencedor.isEmpty()) ? ("Partida finalizada. Vencedor: " + vencedor)
              : "Partida finalizada.";
          if (gameMessagesArea != null) {
            gameMessagesArea.append(msgFim + "\n");
            gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
          }
          // Reset UI
          playerTurn = false;
          atualizarStatusTurno();
          showHome();
          break;
        }
        case "TURNO": {
          // NOTE: Do not clear `sonarDetected` or `mySonarDetected` here.
          // The client now follows a server-trust model: DETECTADO messages
          // from the server explicitly set detection marks and those marks
          // are retained until the local player performs an action that
          // clears them (e.g. MOVER/ATACAR/SONAR). Clearing here caused
          // detections to disappear prematurely when a TURNO message was
          // sent shortly after DETECTADO.

          if ("200".equals(codigoServer)) {
            String nomeJogadorTurno = separarValores(valoresServer, "turno");
            boolean isPlayerTurn = jogadorAtual != null && jogadorAtual.equals(nomeJogadorTurno);
            playerTurn = isPlayerTurn;
            nomeJogadorTurnoAtual = nomeJogadorTurno;
            atualizarStatusTurno();
          } else {
            // Pode ser um TURNO com código diferente (ex.: 408) indicando que o
            // turno expirou para o jogador anterior. Exibe mensagem e atualiza estado.
            String nomeTurno = separarValores(valoresServer, "nome");
            if (nomeTurno != null && nomeTurno.equals(jogadorAtual)) {
              playerTurn = false;
            }
            atualizarStatusTurno();
            String aviso = textoServer != null && !textoServer.isEmpty() ? textoServer : "Turno expirou";
            if (gameMessagesArea != null) {
              gameMessagesArea.append("Turno: " + aviso + "\n");
              gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
            }
          }
          break;
        }
        case "ERRO": {
          // Exibir mensagem simples de erro enviada pelo servidor
          String msg = (textoServer != null && !textoServer.isEmpty()) ? textoServer
              : separarValores(valoresServer, "mensagem");
          if (msg == null || msg.isEmpty())
            msg = "Erro do servidor";
          if (gameMessagesArea != null) {
            gameMessagesArea.append("Erro do servidor: " + msg + "\n");
            gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
          }
          break;
        }
        case "DESCONECTADO": {
          try {
            if (connection != null) {
              connection.close();
            }
          } catch (Exception ignore) {
          }
          stopKeepalive();
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
          // Registrar a desconexão na área de mensagens da partida e voltar para
          // a tela de login (sem popup)
          if (gameMessagesArea != null) {
            gameMessagesArea.append("Desconectado por inatividade\n");
            gameMessagesArea.setCaretPosition(gameMessagesArea.getDocument().getLength());
          }
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
