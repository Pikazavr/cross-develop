package kurs.server;

import java.awt.Rectangle;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerGameState {

    // Размеры карты и константы (дублируем, чтобы не зависеть от клиентских классов)
    public static final int TILE       = 32;
    public static final int UI_HEIGHT  = 64;
    public static final int WIDTH      = 1366;
    public static final int HEIGHT     = 768;
    private static final int SPRITE    = 28;
    private static final int OFFSET    = (TILE - SPRITE) / 2; // 2

    // Враги

    public static class ServerEnemy {
        public int id;
        public int x, y;
        public int dir;       // 0=UP 1=RIGHT 2=DOWN 3=LEFT
        public int hp;
        public int maxHp;
        public String type;   // "LIGHT" | "HEAVY"
        public boolean dead = false;

        // Grid движение
        public int tileCol, tileRow;
        public int targetCol, targetRow;
        public boolean moving = false;
        public int speed = 4; // быстрее чтобы проходить тайл за меньше тиков
        public int waitTimer;

        public long lastShotMs = 0;
        public static final long SHOOT_CD = 3700; // реже стреляют — меньше входящего урона

        private static final Random rnd = new Random();

        public ServerEnemy(int id, int tileCol, int tileRow, String type) {
            this.id = id;
            this.type = type;
            this.tileCol = tileCol;
            this.tileRow = tileRow;
            snapToTile();
            this.targetCol = tileCol;
            this.targetRow = tileRow;
            this.dir = rnd.nextInt(4);
            this.waitTimer = rnd.nextInt(6) + 2; // 2-8 тиков × 16мс ≈ быстро

            if ("LIGHT".equals(type)) { hp = 3; maxHp = 3; }
            else                       { hp = 6; maxHp = 6; }
        }

        public void snapToTile() {
            x = tileCol * TILE + OFFSET;
            y = UI_HEIGHT + tileRow * TILE + OFFSET;
        }

        public Rectangle bounds() { return new Rectangle(x, y, SPRITE, SPRITE); }

        public static Random getRnd() { return rnd; }
    }

    //Серверные пули врагов

    public static class ServerBullet {
        public int id;
        public int x, y;
        public int dir;
        public boolean alive = true;
        private static final int SPEED = 12; // быстрее — компенсируем редкий тик
        private int life = 80;

        public ServerBullet(int id, int x, int y, int dir) {
            this.id = id; this.x = x; this.y = y; this.dir = dir;
        }

        public void update() {
            if (!alive) return;
            switch (dir) {
                case 0 -> y -= SPEED;
                case 1 -> x += SPEED;
                case 2 -> y += SPEED;
                case 3 -> x -= SPEED;
            }
            life--;
            if (life <= 0 || x < 0 || y < 0 || x > WIDTH || y > HEIGHT) alive = false;
        }

        public Rectangle bounds() { return new Rectangle(x, y, 8, 8); }
    }

    //Состояние игрока (позиция, нужна для AI)

    private final Map<String, int[]> playerPositions = new HashMap<>(); // clientId -> [x, y]

    //Данные

    private final List<ServerEnemy>  enemies = new CopyOnWriteArrayList<>();
    private final List<ServerBullet> bullets = new CopyOnWriteArrayList<>();
    private int nextEnemyId  = 1;
    private int nextBulletId = 1;

    // Простая карта стен (загружается один раз)
    private int[][] mapTiles;
    private int mapRows, mapCols;

    private final Random rnd = new Random();

    public ServerGameState() {
        loadDefaultMap();
        spawnDefaultEnemies();
    }

       private void loadDefaultMap() {
        try {
            List<String> lines = new ArrayList<>();
            try (Scanner sc = new Scanner(
                    ServerGameState.class.getResourceAsStream("/levels/level1.txt"))) {
                while (sc.hasNextLine()) lines.add(sc.nextLine());
            }
            mapRows = lines.size();
            mapCols = lines.get(0).length();
            mapTiles = new int[mapRows][mapCols];
            for (int r = 0; r < mapRows; r++) {
                String line = lines.get(r);
                for (int c = 0; c < mapCols; c++) {
                    char ch = c < line.length() ? line.charAt(c) : '.';
                    mapTiles[r][c] = (ch == '#' || ch == '/') ? 1 : 0;
                }
            }
        } catch (Exception e) {
            
            mapRows = 24; mapCols = 43;
            mapTiles = new int[mapRows][mapCols];
        }
    }

    private void spawnDefaultEnemies() {
        enemies.clear();
        // Спавним 6 врагов в случайных свободных тайлах
        int spawned = 0;
        int attempts = 0;
        while (spawned < 6 && attempts < 200) {
            attempts++;
            int col = rnd.nextInt(mapCols);
            int row = rnd.nextInt(mapRows);
            if (mapTiles[row][col] != 0) continue;
            int px = col * TILE + OFFSET;
            int py = UI_HEIGHT + row * TILE + OFFSET;
            if (px + SPRITE > WIDTH || py + SPRITE > HEIGHT) continue;
            String type = rnd.nextBoolean() ? "LIGHT" : "HEAVY";
            enemies.add(new ServerEnemy(nextEnemyId++, col, row, type));
            spawned++;
        }
    }

    //Обновление (вызывается каждые 50мс)

    public void update() {
        updateBullets();
        updateEnemies();
        checkBulletEnemyCollisions();
    }

    private void updateBullets() {
        for (ServerBullet b : bullets) {
            b.update();
            if (b.alive && intersectsWall(b.bounds())) b.alive = false;
        }
        bullets.removeIf(b -> !b.alive);
    }

    private void updateEnemies() {
        // Берём первую позицию игрока для AI (упрощённо — один игрок)
        int[] playerPos = playerPositions.isEmpty() ? null
                : playerPositions.values().iterator().next();

        for (ServerEnemy e : enemies) {
            if (e.dead) continue;

            if (e.moving) {
                stepEnemy(e);
            } else {
                e.snapToTile();
                e.waitTimer--;
                if (e.waitTimer <= 0) {
                    if (playerPos != null && canSee(e, playerPos[0], playerPos[1])) {
                        faceTowards(e, playerPos[0], playerPos[1]);
                        // Стреляем
                        long now = System.currentTimeMillis();
                        if (now - e.lastShotMs >= ServerEnemy.SHOOT_CD) {
                            e.lastShotMs = now;
                            spawnEnemyBullet(e);
                        }
                        // Лёгкий сближается
                        if ("LIGHT".equals(e.type)) tryStartMove(e);
                    } else {
                        if (rnd.nextInt(3) == 0) e.dir = rnd.nextInt(4);
                        tryStartMove(e);
                    }
                    e.waitTimer = rnd.nextInt(6) + 2;
                }
            }
        }

        enemies.removeIf(e -> e.dead);
    }

    private void stepEnemy(ServerEnemy e) {
        int tx = e.targetCol * TILE + OFFSET;
        int ty = UI_HEIGHT + e.targetRow * TILE + OFFSET;
        int dx = tx - e.x, dy = ty - e.y;
        if (Math.abs(dx) <= e.speed && Math.abs(dy) <= e.speed) {
            e.tileCol = e.targetCol; e.tileRow = e.targetRow;
            e.snapToTile(); e.moving = false;
        } else {
            if (dx != 0) e.x += dx > 0 ? e.speed : -e.speed;
            if (dy != 0) e.y += dy > 0 ? e.speed : -e.speed;
        }
    }

    private void tryStartMove(ServerEnemy e) {
        int[] dc = {0,1,0,-1}, dr = {-1,0,1,0};
        for (int attempt = 0; attempt < 4; attempt++) {
            int nc = e.tileCol + dc[e.dir];
            int nr = e.tileRow + dr[e.dir];
            if (isTileFree(nc, nr)) {
                e.targetCol = nc; e.targetRow = nr; e.moving = true; return;
            }
            e.dir = rnd.nextInt(4);
        }
    }

    private boolean isTileFree(int col, int row) {
        int px = col * TILE + OFFSET, py = UI_HEIGHT + row * TILE + OFFSET;
        if (px < 0 || py < UI_HEIGHT || px + SPRITE > WIDTH || py + SPRITE > HEIGHT) return false;
        return !intersectsWall(new Rectangle(px, py, SPRITE, SPRITE));
    }

    private void faceTowards(ServerEnemy e, int px, int py) {
        int ex = e.x + 14, ey = e.y + 14;
        int adx = Math.abs(px - ex), ady = Math.abs(py - ey);
        if (ady >= adx) e.dir = (py < ey) ? 0 : 2;
        else            e.dir = (px < ex) ? 3 : 1;
    }

    private boolean canSee(ServerEnemy e, int px, int py) {
        int ex = e.x + 14, ey = e.y + 14;
        int adx = Math.abs(px - ex), ady = Math.abs(py - ey);
        int tol = TILE / 2;
        if (ady <= tol) {
            for (int xx = Math.min(px,ex); xx <= Math.max(px,ex); xx += 4)
                if (intersectsWall(new Rectangle(xx-2, ey-2, 4, 4))) return false;
            return true;
        }
        if (adx <= tol) {
            for (int yy = Math.min(py,ey); yy <= Math.max(py,ey); yy += 4)
                if (intersectsWall(new Rectangle(ex-2, yy-2, 4, 4))) return false;
            return true;
        }
        return false;
    }

    private void spawnEnemyBullet(ServerEnemy e) {
        int bx = e.x + 14 - 4, by = e.y + 14 - 4;
        bullets.add(new ServerBullet(nextBulletId++, bx, by, e.dir));
    }

    private void checkBulletEnemyCollisions() {
        // Пули врагов по игрокам — клиент сам проверит по STATE
        // Здесь удаляем пули вошедшие в стену (уже делается в updateBullets)
    }

    // Обрабатываем выстрел игрока — проверяем попадание по врагам. 
    public List<String> playerShoot(int bx, int by, int dir) {
        List<String> events = new ArrayList<>();
        // Симулируем полёт пули до первого попадания
        int x = bx, y = by;
        for (int step = 0; step < 150; step++) {
            switch (dir) {
                case 0 -> y -= 8;
                case 1 -> x += 8;
                case 2 -> y += 8;
                case 3 -> x -= 8;
            }
            Rectangle br = new Rectangle(x, y, 8, 8);
            if (intersectsWall(br)) break;
            for (ServerEnemy e : enemies) {
                if (e.dead) continue;
                if (br.intersects(e.bounds())) {
                    e.hp--;
                    if (e.hp <= 0) { e.dead = true; events.add("HIT;KILL;" + e.id); }
                    else           { events.add("HIT;DMG;"  + e.id + ";" + e.hp); }
                    return events;
                }
            }
        }
        return events;
    }

    public void updatePlayerPos(String clientId, int px, int py) {
        playerPositions.put(clientId, new int[]{px + 14, py + 14});
    }

    //Стены

    private boolean intersectsWall(Rectangle r) {
        if (mapTiles == null) return false;
        for (int row = 0; row < mapRows; row++) {
            for (int col = 0; col < mapCols; col++) {
                if (mapTiles[row][col] == 0) continue;
                int tx = col * TILE, ty = UI_HEIGHT + row * TILE;
                if (r.intersects(new Rectangle(tx, ty, TILE, TILE))) return true;
            }
        }
        return false;
    }

    //Сериализация

      public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("ENEMIES:");
        boolean first = true;
        for (ServerEnemy e : enemies) {
            if (e.dead) continue;
            if (!first) sb.append('|');
            sb.append(e.id).append(',')
              .append(e.x).append(',')
              .append(e.y).append(',')
              .append(e.hp).append(',')
              .append(e.dir).append(',')
              .append(e.type);
            first = false;
        }
        sb.append(":BULLETS:");
        first = true;
        for (ServerBullet b : bullets) {
            if (!b.alive) continue;
            if (!first) sb.append('|');
            sb.append(b.id).append(',')
              .append(b.x).append(',')
              .append(b.y).append(',')
              .append(b.dir);
            first = false;
        }
        return sb.toString();
    }

    public boolean allEnemiesDead() {
        return enemies.stream().allMatch(e -> e.dead);
    }

    //Полный сброс — перезапуск уровня. 
    public void reset() {
        enemies.clear();
        bullets.clear();
        playerPositions.clear();
        nextEnemyId  = 1;
        nextBulletId = 1;
        spawnDefaultEnemies();
    }
}