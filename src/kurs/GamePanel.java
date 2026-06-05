package kurs;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class GamePanel extends JPanel implements Runnable {

    public static final int TILE       = 32;
    public static final int WIDTH      = 1366;
    public static final int HEIGHT     = 768;
    public static final int UI_HEIGHT  = 64;

    private enum GameState { SPLASH, MENU, CONNECTING, GAME, PAUSE, WIN, GAME_OVER }
    private GameState state = GameState.SPLASH;

    private static final int SPLASH_DURATION_MS = 2000;
    private int menuIndex = 0;

    //Игрок
    private PlayerTank player;

    //Враги и пули — приходят с сервера
    private final List<RemoteEnemy>  remoteEnemies = new CopyOnWriteArrayList<>();
    private final List<RemoteBullet> remoteBullets = new CopyOnWriteArrayList<>();

    // Пули игрока — только визуал, логика на сервере
    private final List<Bullet> playerBullets = new CopyOnWriteArrayList<>();

    //Карта
    private int[][] mapTiles;

    //Сеть
    private GameClient gameClient;
    private volatile boolean serverConnected = false;
    private String connectError = "";
    private final AtomicLong lastMoveSent = new AtomicLong(0);
    private static final long MOVE_INTERVAL_MS = 50;

    //Игровой поток
    private Thread gameThread;
    private volatile boolean running = false;
    private final int FPS = 60;
    private final long TARGET_TIME = 1000 / FPS;

    //Ввод
    private volatile boolean up, down, left, right, shoot;
    private enum MoveDir { NONE, UP, DOWN, LEFT, RIGHT }
    private volatile MoveDir lastMoveDir = MoveDir.NONE;

    private javax.swing.Timer splashTimer;

    //Вложенные классы для данных с сервера

    public static class RemoteEnemy {
        public int id, x, y, hp, maxHp, dir;
        public String type;
        public RemoteEnemy(int id, int x, int y, int hp, int dir, String type) {
            this.id = id; this.x = x; this.y = y;
            this.hp = hp; this.dir = dir; this.type = type;
            this.maxHp = "LIGHT".equals(type) ? 3 : 6;
        }
        public Rectangle getBounds() { return new Rectangle(x, y, 28, 28); }
    }

    public static class RemoteBullet {
        public int id, x, y, dir;
        public RemoteBullet(int id, int x, int y, int dir) {
            this.id = id; this.x = x; this.y = y; this.dir = dir;
        }
        public Rectangle getBounds() { return new Rectangle(x, y, 8, 8); }
    }

  

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        setBackground(Color.BLACK);

        player = new PlayerTank(100, UI_HEIGHT + 50);

        try { loadLevel("/levels/level1.txt"); }
        catch (Exception e) { createEmptyMap(24, 43); }

        setupKeyBindings();

        splashTimer = new javax.swing.Timer(SPLASH_DURATION_MS, e -> {
            splashTimer.stop();
            state = GameState.MENU;
            repaint();
        });
        splashTimer.setRepeats(false);
        splashTimer.start();
    }

    //Карта

    private void createEmptyMap(int rows, int cols) {
        mapTiles = new int[rows][cols];
    }

    private void loadLevel(String path) throws Exception {
        List<String> lines = new ArrayList<>();
        try (Scanner sc = new Scanner(getClass().getResourceAsStream(path))) {
            while (sc.hasNextLine()) lines.add(sc.nextLine());
        }
        if (lines.isEmpty()) throw new Exception("empty");
        int rows = lines.size(), cols = lines.get(0).length();
        mapTiles = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            String line = lines.get(r);
            for (int c = 0; c < Math.min(cols, line.length()); c++) {
                char ch = line.charAt(c);
                if (ch == '#') mapTiles[r][c] = 1;
                else if (ch == '/') mapTiles[r][c] = 2;
                else if (ch == 'G') {
                    player = new PlayerTank(
                        c * TILE + (TILE-28)/2,
                        UI_HEIGHT + r * TILE + (TILE-28)/2);
                }
            }
        }
    }

    public boolean intersectsWallOrBox(Rectangle rect) {
        if (mapTiles == null) return false;
        for (int r = 0; r < mapTiles.length; r++)
            for (int c = 0; c < mapTiles[0].length; c++) {
                if (mapTiles[r][c] == 0) continue;
                if (rect.intersects(new Rectangle(c*TILE, UI_HEIGHT+r*TILE, TILE, TILE))) return true;
            }
        return false;
    }

    //Сеть

    public void connectToServer(String host, int port, String playerName) {
        state = GameState.CONNECTING;
        repaint();

        new Thread(() -> {
            try {
                gameClient = new GameClient(host, port, this::onServerMessage);
                gameClient.connect();
                gameClient.send("JOIN;" + playerName);
                // Ждём WELCOME (до 5 сек)
                long deadline = System.currentTimeMillis() + 5000;
                while (!serverConnected && System.currentTimeMillis() < deadline)
                    Thread.sleep(50);

                if (!serverConnected) throw new Exception("Сервер не ответил");

                SwingUtilities.invokeLater(() -> {
                    state = GameState.MENU;
                    repaint();
                });
            } catch (Exception e) {
                connectError = e.getMessage();
                SwingUtilities.invokeLater(() -> {
                    state = GameState.MENU;
                    repaint();
                });
            }
        }).start();
    }

    private void onServerMessage(String msg) {
        if (msg.startsWith("SERVER;WELCOME")) {
            serverConnected = true;
            return;
        }

        if (msg.startsWith("STATE;")) {
            parseState(msg.substring(6));
            // Проверяем победу — нет врагов
            if (state == GameState.GAME && remoteEnemies.isEmpty()) {
                SwingUtilities.invokeLater(this::onWin);
            }
            return;
        }

        if (msg.startsWith("HIT;")) {
            String[] p = msg.split(";");
            if (p.length >= 2 && "KILL".equals(p[1])) {
                // Враг убит — уже удалён из следующего STATE
            }
            return;
        }

        // Урон по игроку от пуль — сервер шлёт DAMAGE;amount
        if (msg.startsWith("DAMAGE;")) {
            try {
                int dmg = Integer.parseInt(msg.split(";")[1]);
                player.damage(dmg);
                if (player.getHp() <= 0) SwingUtilities.invokeLater(this::onPlayerDeath);
            } catch (Exception ignored) {}
        }
    }
    
    private void parseState(String data) {
        try {
            String[] sections = data.split(":BULLETS:");
            String enemyPart  = sections[0].replace("ENEMIES:", "");
            String bulletPart = sections.length > 1 ? sections[1] : "";

            List<RemoteEnemy> newEnemies = new ArrayList<>();
            if (!enemyPart.isEmpty()) {
                for (String token : enemyPart.split("\\|")) {
                    String[] f = token.split(",");
                    if (f.length < 6) continue;
                    newEnemies.add(new RemoteEnemy(
                        Integer.parseInt(f[0]),
                        Integer.parseInt(f[1]),
                        Integer.parseInt(f[2]),
                        Integer.parseInt(f[3]),
                        Integer.parseInt(f[4]),
                        f[5]
                    ));
                }
            }

            List<RemoteBullet> newBullets = new ArrayList<>();
            if (!bulletPart.isEmpty()) {
                for (String token : bulletPart.split("\\|")) {
                    String[] f = token.split(",");
                    if (f.length < 4) continue;
                    newBullets.add(new RemoteBullet(
                        Integer.parseInt(f[0]),
                        Integer.parseInt(f[1]),
                        Integer.parseInt(f[2]),
                        Integer.parseInt(f[3])
                    ));
                }
            }

            remoteEnemies.clear();
            remoteEnemies.addAll(newEnemies);
            remoteBullets.clear();
            remoteBullets.addAll(newBullets);

            // Проверяем столкновение пуль врагов с игроком локально
            for (RemoteBullet b : remoteBullets) {
                if (b.getBounds().intersects(player.getBounds())) {
                    player.damage(1);
                    if (player.getHp() <= 0) SwingUtilities.invokeLater(this::onPlayerDeath);
                }
            }

        } catch (Exception e) {
            System.err.println("Ошибка парсинга STATE: " + e.getMessage());
        }
    }

    private void sendMove() {
        if (gameClient == null || !gameClient.isConnected()) return;
        long now = System.currentTimeMillis();
        if (now - lastMoveSent.get() >= MOVE_INTERVAL_MS) {
            gameClient.send("MOVE;" + player.getX() + ";" + player.getY());
            lastMoveSent.set(now);
        }
    }

    private void sendShoot() {
        if (gameClient == null || !gameClient.isConnected()) return;
        gameClient.send("SHOOT;" + player.getX() + ";" + player.getY() + ";" + player.getDirection());
    }

    // Игровой цикл 

    public void startGame() {
        if (!serverConnected) return; // без сервера — не стартуем
        if (running) return;
        running = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void run() {
        while (running) {
            long start = System.nanoTime();
            if (state == GameState.GAME) updateLocal();
            repaint();
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            long wait = TARGET_TIME - elapsed;
            if (wait < 1) wait = 1;
            try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
        }
    }

    
    private void updateLocal() {
        double dx = 0, dy = 0;
        switch (lastMoveDir) {
            case UP    -> dy = -2;
            case DOWN  -> dy =  2;
            case LEFT  -> dx = -2;
            case RIGHT -> dx =  2;
        }
        if (dx != 0 || dy != 0) { player.move(dx, dy, this); sendMove(); }

        if (shoot) {
            Bullet b = player.shoot();
            if (b != null) { playerBullets.add(b); sendShoot(); }
            shoot = false;
        }

        // Двигаем пули игрока 
        playerBullets.forEach(Bullet::update);
        playerBullets.forEach(b -> { if (b.isAlive() && intersectsWallOrBox(b.getBounds())) b.setAlive(false); });
        playerBullets.removeIf(b -> !b.isAlive());
    }

    private void onPlayerDeath() {
        running = false;
        state = GameState.GAME_OVER;
        menuIndex = 0;
        repaint();
    }

    private void onWin() {
        running = false;
        state = GameState.WIN;
        menuIndex = 0;
        repaint();
    }

    // Отрисовка 

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, WIDTH, UI_HEIGHT);
        drawPlayerHP(g);

        if (state == GameState.SPLASH)     { drawSplash(g);     return; }
        if (state == GameState.MENU)       { drawMenu(g);        return; }
        if (state == GameState.CONNECTING) { drawConnecting(g);  return; }

        g.setColor(Color.BLACK);
        g.fillRect(0, UI_HEIGHT, WIDTH, HEIGHT - UI_HEIGHT);

        drawMap(g);
        player.draw(g);

        
        for (RemoteEnemy e : remoteEnemies) drawRemoteEnemy(g, e);
        
        for (RemoteBullet b : remoteBullets) drawRemoteBullet(g, b);
        
        playerBullets.forEach(b -> b.draw(g));

        if (state == GameState.PAUSE)     drawPauseOverlay(g);
        if (state == GameState.WIN)       drawWinOverlay(g);
        if (state == GameState.GAME_OVER) drawGameOverOverlay(g);
    }

    private void drawRemoteEnemy(Graphics g, RemoteEnemy e) {
        g.setColor("LIGHT".equals(e.type) ? Color.RED : Color.BLUE);
        g.fillRect(e.x, e.y, 28, 28);
        g.setColor(Color.BLACK);
        g.drawRect(e.x, e.y, 28, 28);
        // HP бар
        g.setColor(Color.DARK_GRAY); g.fillRect(e.x, e.y-6, 28, 4);
        int hw = (int)(28.0 * Math.max(0, e.hp) / e.maxHp);
        g.setColor(Color.GREEN); g.fillRect(e.x, e.y-6, hw, 4);
        // Пушка
        g.setColor(Color.WHITE);
        switch (e.dir) {
            case 0 -> g.fillRect(e.x+12, e.y-6,  4, 6);
            case 1 -> g.fillRect(e.x+28, e.y+12, 6, 4);
            case 2 -> g.fillRect(e.x+12, e.y+28, 4, 6);
            case 3 -> g.fillRect(e.x-6,  e.y+12, 6, 4);
        }
    }

    private void drawRemoteBullet(Graphics g, RemoteBullet b) {
        g.setColor(Color.ORANGE);
        g.fillOval(b.x, b.y, 8, 8);
        g.setColor(Color.BLACK);
        g.drawOval(b.x, b.y, 8, 8);
    }

    private void drawMap(Graphics g) {
        if (mapTiles == null) return;
        for (int r = 0; r < mapTiles.length; r++)
            for (int c = 0; c < mapTiles[0].length; c++) {
                int tx = c*TILE, ty = UI_HEIGHT + r*TILE;
                if (mapTiles[r][c] == 1) {
                    g.setColor(Color.GRAY.darker()); g.fillRect(tx,ty,TILE,TILE);
                    g.setColor(Color.BLACK);          g.drawRect(tx,ty,TILE,TILE);
                } else if (mapTiles[r][c] == 2) {
                    g.setColor(new Color(160,110,60)); g.fillRect(tx+2,ty+2,TILE-4,TILE-4);
                    g.setColor(Color.BLACK);            g.drawRect(tx+2,ty+2,TILE-4,TILE-4);
                }
            }
    }

    private void drawPlayerHP(Graphics g) {
        int bw=400,bh=28,x=20,y=18;
        g.setColor(Color.BLACK);     g.fillRect(x-2,y-2,bw+4,bh+4);
        g.setColor(Color.DARK_GRAY); g.fillRect(x,y,bw,bh);
        float pct = (float)player.getHp()/player.getMaxHp();
        g.setColor(Color.RED);       g.fillRect(x,y,(int)(bw*pct),bh);
        g.setColor(Color.WHITE);     g.drawRect(x,y,bw,bh);
        g.setFont(new Font("Arial",Font.BOLD,16));
        g.setColor(Color.WHITE);
        g.drawString(player.getHp()+"/"+player.getMaxHp()+" HP", x+bw+10, y+bh-6);
    }

    private void drawSplash(Graphics g) {
        g.setColor(Color.BLACK); g.fillRect(0,0,WIDTH,HEIGHT);
        g.setColor(Color.WHITE); g.setFont(new Font("Arial",Font.BOLD,72));
        String t="ТАНЧИКИ"; g.drawString(t, WIDTH/2-g.getFontMetrics().stringWidth(t)/2, HEIGHT/2);
    }

    private void drawConnecting(Graphics g) {
        g.setColor(Color.BLACK); g.fillRect(0,0,WIDTH,HEIGHT);
        g.setColor(Color.YELLOW); g.setFont(new Font("Arial",Font.BOLD,40));
        String t = connectError.isEmpty() ? "Подключение к серверу..." : "Ошибка: " + connectError;
        g.drawString(t, WIDTH/2 - g.getFontMetrics().stringWidth(t)/2, HEIGHT/2);
        if (!connectError.isEmpty()) {
            g.setColor(Color.WHITE); g.setFont(new Font("Arial",Font.PLAIN,24));
            String sub = "Нажмите ENTER чтобы вернуться в меню";
            g.drawString(sub, WIDTH/2 - g.getFontMetrics().stringWidth(sub)/2, HEIGHT/2+50);
        }
    }

    private void drawMenu(Graphics g) {
        g.setColor(Color.BLACK); g.fillRect(0,0,WIDTH,HEIGHT);
        g.setColor(Color.WHITE); g.setFont(new Font("Arial",Font.BOLD,72));
        String title="ТАНЧИКИ";
        g.drawString(title, WIDTH/2 - g.getFontMetrics().stringWidth(title)/2, 140);

        // Статус соединения
        g.setFont(new Font("Arial",Font.PLAIN,20));
        if (serverConnected) { g.setColor(Color.GREEN);  g.drawString("● Сервер подключён", 20, HEIGHT-20); }
        else                 { g.setColor(Color.RED);    g.drawString("● Сервер не подключён — игра недоступна", 20, HEIGHT-20); }

        g.setFont(new Font("Arial",Font.BOLD,40));
        String[] opts = {"Играть", "Выход"};
        for (int i=0;i<opts.length;i++) {
            // Играть недоступно без сервера
            boolean disabled = (i==0 && !serverConnected);
            g.setColor(disabled ? Color.GRAY : (menuIndex==i ? Color.YELLOW : Color.WHITE));
            g.drawString(opts[i], WIDTH/2-60, 320+i*80);
        }
    }

    private void drawPauseOverlay(Graphics g) {
        Graphics2D g2=(Graphics2D)g.create();
        g2.setColor(new Color(0,0,0,160)); g2.fillRect(0,0,WIDTH,HEIGHT);
        g2.setFont(new Font("Arial",Font.BOLD,80)); g2.setColor(Color.WHITE);
        String t="ПАУЗА"; g2.drawString(t, WIDTH/2-g2.getFontMetrics().stringWidth(t)/2, HEIGHT/2-80);
        g2.setFont(new Font("Arial",Font.BOLD,36));
        String[] opts={"Продолжить","Перезапустить уровень","Выйти в меню"};
        for (int i=0;i<opts.length;i++) {
            g2.setColor(menuIndex==i?Color.YELLOW:Color.WHITE);
            g2.drawString(opts[i], WIDTH/2-180, HEIGHT/2+10+i*50);
        }
        g2.dispose();
    }

    private void drawWinOverlay(Graphics g) {
        Graphics2D g2=(Graphics2D)g.create();
        g2.setColor(new Color(0,0,0,180)); g2.fillRect(0,0,WIDTH,HEIGHT);
        g2.setFont(new Font("Arial",Font.BOLD,80)); g2.setColor(Color.YELLOW);
        String t="WINNER WINNER";
        g2.drawString(t, WIDTH/2-g2.getFontMetrics().stringWidth(t)/2, HEIGHT/2-60);
        g2.setFont(new Font("Arial",Font.BOLD,64)); g2.setColor(new Color(255,180,0));
        String s="CHICKEN DINNER!";
        g2.drawString(s, WIDTH/2-g2.getFontMetrics().stringWidth(s)/2, HEIGHT/2+20);
        g2.setFont(new Font("Arial",Font.BOLD,36)); g2.setColor(Color.WHITE);
        String opt="Выйти в меню";
        g2.drawString(opt, WIDTH/2-g2.getFontMetrics().stringWidth(opt)/2, HEIGHT/2+100);
        g2.dispose();
    }

    private void drawGameOverOverlay(Graphics g) {
        Graphics2D g2=(Graphics2D)g.create();
        g2.setColor(new Color(0,0,0,180)); g2.fillRect(0,0,WIDTH,HEIGHT);
        g2.setFont(new Font("Arial",Font.BOLD,96)); g2.setColor(Color.RED);
        String t="GAME OVER";
        g2.drawString(t, WIDTH/2-g2.getFontMetrics().stringWidth(t)/2, HEIGHT/2-40);
        g2.setFont(new Font("Arial",Font.BOLD,36));
        String[] opts={"Перезапустить уровень","Выйти в меню"};
        for (int i=0;i<opts.length;i++) {
            g2.setColor(menuIndex==i?Color.YELLOW:Color.WHITE);
            g2.drawString(opts[i], WIDTH/2-200, HEIGHT/2+20+i*50);
        }
        g2.dispose();
    }

    //Управление 

    private void setupKeyBindings() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke("W"),          "up_press");
        im.put(KeyStroke.getKeyStroke("S"),          "down_press");
        im.put(KeyStroke.getKeyStroke("A"),          "left_press");
        im.put(KeyStroke.getKeyStroke("D"),          "right_press");
        im.put(KeyStroke.getKeyStroke("SPACE"),      "shoot_press");
        im.put(KeyStroke.getKeyStroke("ESCAPE"),     "pause");
        im.put(KeyStroke.getKeyStroke("released W"), "up_rel");
        im.put(KeyStroke.getKeyStroke("released S"), "down_rel");
        im.put(KeyStroke.getKeyStroke("released A"), "left_rel");
        im.put(KeyStroke.getKeyStroke("released D"), "right_rel");
        im.put(KeyStroke.getKeyStroke("UP"),         "menu_up");
        im.put(KeyStroke.getKeyStroke("DOWN"),       "menu_dn");
        im.put(KeyStroke.getKeyStroke("ENTER"),      "menu_ok");

        am.put("up_press",  act(() -> { if(state==GameState.GAME){up=true;lastMoveDir=MoveDir.UP;} }));
        am.put("down_press",act(() -> { if(state==GameState.GAME){down=true;lastMoveDir=MoveDir.DOWN;} }));
        am.put("left_press",act(() -> { if(state==GameState.GAME){left=true;lastMoveDir=MoveDir.LEFT;} }));
        am.put("right_press",act(()->{ if(state==GameState.GAME){right=true;lastMoveDir=MoveDir.RIGHT;} }));
        am.put("shoot_press",act(()->{ if(state==GameState.GAME) shoot=true; }));

        am.put("up_rel",  act(()->{ up=false;   updateDir(); }));
        am.put("down_rel",act(()->{ down=false;  updateDir(); }));
        am.put("left_rel",act(()->{ left=false;  updateDir(); }));
        am.put("right_rel",act(()->{ right=false; updateDir(); }));

        am.put("pause", act(() -> {
            if (state == GameState.GAME) {
                state = GameState.PAUSE;
                menuIndex = 0;
                // Говорим серверу приостановить обновления
                if (gameClient != null && gameClient.isConnected())
                    gameClient.send("PAUSE");
                repaint();
            } else if (state == GameState.PAUSE) {
                state = GameState.GAME;
                // Возобновляем сервер
                if (gameClient != null && gameClient.isConnected())
                    gameClient.send("RESUME");
                repaint();
            }
        }));

        am.put("menu_up", act(() -> { int m=menuMax(); if(m>0){menuIndex=(menuIndex-1+m)%m; repaint();} }));
        am.put("menu_dn", act(() -> { int m=menuMax(); if(m>0){menuIndex=(menuIndex+1)%m;   repaint();} }));
        am.put("menu_ok", act(this::handleEnter));
    }

    private AbstractAction act(Runnable r) {
        return new AbstractAction(){ public void actionPerformed(ActionEvent e){ r.run(); } };
    }

    private void updateDir() {
        if (up)    lastMoveDir=MoveDir.UP;
        else if(down)  lastMoveDir=MoveDir.DOWN;
        else if(left)  lastMoveDir=MoveDir.LEFT;
        else if(right) lastMoveDir=MoveDir.RIGHT;
        else           lastMoveDir=MoveDir.NONE;
    }

    private int menuMax() {
        return switch(state) {
            case MENU      -> 2;
            case PAUSE     -> 3;
            case GAME_OVER -> 2;
            case WIN       -> 1;
            case CONNECTING-> 1;
            default        -> 0;
        };
    }

    private void handleEnter() {
        switch(state) {
            case MENU -> {
                if (menuIndex==0 && serverConnected) { state=GameState.GAME; startGame(); }
                else if (menuIndex==1) System.exit(0);
            }
            case CONNECTING -> { connectError=""; state=GameState.MENU; repaint(); }
            case PAUSE -> {
                switch(menuIndex) {
                    case 0 -> {
                        state = GameState.GAME;
                        if (gameClient != null && gameClient.isConnected())
                            gameClient.send("RESUME");
                        repaint();
                    }
                    case 1 -> restartLevel();
                    case 2 -> goToMenu();
                }
            }
            case GAME_OVER -> { if(menuIndex==0) restartLevel(); else goToMenu(); }
            case WIN       -> goToMenu();
        }
    }

    private void restartLevel() {
        running = false;
        // Ждём остановки старого потока
        if (gameThread != null && gameThread.isAlive()) {
            try { gameThread.join(500); } catch (InterruptedException ignored) {}
        }
        remoteEnemies.clear(); remoteBullets.clear(); playerBullets.clear();
        try { loadLevel("/levels/level1.txt"); } catch(Exception ignored){}
        lastMoveDir=MoveDir.NONE; up=down=left=right=shoot=false; menuIndex=0;
        // Говорим серверу сбросить состояние
        if (gameClient != null && gameClient.isConnected()) {
            gameClient.send("RESTART");
        }
        state=GameState.GAME; running=true;
        gameThread=new Thread(this); gameThread.start();
    }

    private void goToMenu() {
        running=false;
        remoteEnemies.clear(); remoteBullets.clear(); playerBullets.clear();
        try { loadLevel("/levels/level1.txt"); } catch(Exception ignored){}
        lastMoveDir=MoveDir.NONE; up=down=left=right=shoot=false; menuIndex=0;
        state=GameState.MENU; repaint();
    }

    public void stopNetwork() {
        if (gameClient!=null) { gameClient.close(); gameClient=null; serverConnected=false; }
    }
}