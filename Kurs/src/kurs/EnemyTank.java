package kurs;

import java.awt.*;
import java.util.Random;
public class EnemyTank {

    public enum Type { LIGHT, HEAVY }

    // Позиция в пикселях (левый верхний угол спрайта 28x28)
    private int x, y;

    // Текущее направление: 0=UP 1=RIGHT 2=DOWN 3=LEFT
    private int dir;

    // Скорость перемещения (пикселей за кадр)
    private int speed;

    // Тайловые координаты
    private int tileCol, tileRow;
    private int targetTileCol, targetTileRow;
    private boolean moving = false;

    private int hp;
    private final int maxHp;
    private final int damage;
    private boolean destroyed = false;

    // Задержка между шагами (кадры)
    private int waitTimer = 0;
    private static final int WAIT_MIN = 10;
    private static final int WAIT_MAX = 40;

    private long lastShotTime = 0;
    private static final long SHOOT_COOLDOWN_MS = 2000;

    private final Random rnd = new Random();
    private final Type type;

    // Смещение спрайта 28px внутри тайла 32px
    private static final int SPRITE_OFFSET = (GamePanel.TILE - 28) / 2;

    public EnemyTank(int pixelX, int pixelY, Type type) {
        this.type = type;

        // Переводим пиксельную позицию в тайловую
        this.tileCol = pixelX / GamePanel.TILE;
        this.tileRow = (pixelY - GamePanel.UI_HEIGHT) / GamePanel.TILE;

        snapToTile();

        this.targetTileCol = tileCol;
        this.targetTileRow = tileRow;
        this.dir = rnd.nextInt(4);
        this.waitTimer = rnd.nextInt(WAIT_MAX) + WAIT_MIN;

        if (type == Type.LIGHT) {
            this.speed = 2;
            this.hp = 2; this.maxHp = 2; this.damage = 1;
        } else {
            this.speed = 2;
            this.hp = 4; this.maxHp = 4; this.damage = 1;
        }
    }

    // Выравнивает пиксельную позицию строго по тайлу. 
    private void snapToTile() {
        x = tileCol * GamePanel.TILE + SPRITE_OFFSET;
        y = GamePanel.UI_HEIGHT + tileRow * GamePanel.TILE + SPRITE_OFFSET;
    }

    public void updateAI(PlayerTank player, GamePanel panel) {
        if (destroyed) return;

        if (moving) {
            stepTowardsTarget();
            return;
        }

        // Стоим ровно на тайле
        snapToTile();

        if (canSeePlayer(player, panel)) {
            facePlayer(player);

            // Стреляем только когда полностью на тайле (moving==false)
            long now = System.currentTimeMillis();
            if (now - lastShotTime >= SHOOT_COOLDOWN_MS) {
                lastShotTime = now;
                GameEvents.spawnEnemyBullet(createBullet());
            }

            // Лёгкий сближается
            if (type == Type.LIGHT) {
                waitTimer--;
                if (waitTimer <= 0) {
                    tryStartMoveTowards(player, panel);
                    waitTimer = rnd.nextInt(WAIT_MAX) + WAIT_MIN;
                }
            }
        } else {
            // Патрулирование
            waitTimer--;
            if (waitTimer <= 0) {
                if (rnd.nextInt(3) == 0) dir = rnd.nextInt(4);
                tryStartMove(panel);
                waitTimer = rnd.nextInt(WAIT_MAX) + WAIT_MIN;
            }
        }
    }

    //Шаг к цели двигаем пиксельную позицию, при достижении - защёлкиваемся. 
    private void stepTowardsTarget() {
        int tx = targetTileCol * GamePanel.TILE + SPRITE_OFFSET;
        int ty = GamePanel.UI_HEIGHT + targetTileRow * GamePanel.TILE + SPRITE_OFFSET;

        int dx = tx - x;
        int dy = ty - y;

        if (Math.abs(dx) <= speed && Math.abs(dy) <= speed) {
            tileCol = targetTileCol;
            tileRow = targetTileRow;
            snapToTile();
            moving = false;
        } else {
            if (dx != 0) x += (dx > 0 ? speed : -speed);
            if (dy != 0) y += (dy > 0 ? speed : -speed);
        }
    }

    // Пробует начать движение в направлении dir. При неудаче - пробует другие. 
    private void tryStartMove(GamePanel panel) {
        int[] dc = {0, 1, 0, -1};
        int[] dr = {-1, 0, 1, 0};

        for (int attempt = 0; attempt < 4; attempt++) {
            int nc = tileCol + dc[dir];
            int nr = tileRow + dr[dir];
            if (isTileFree(nc, nr, panel)) {
                targetTileCol = nc;
                targetTileRow = nr;
                moving = true;
                return;
            }
            dir = rnd.nextInt(4);
        }
    }

    //Лёгкий враг пытается двигаться в сторону игрока. 
    private void tryStartMoveTowards(PlayerTank player, GamePanel panel) {
        int[] dc = {0, 1, 0, -1};
        int[] dr = {-1, 0, 1, 0};

        // Сначала пробуем направление к игроку
        int preferred = dir; // dir уже повёрнут к игроку через facePlayer
        int nc = tileCol + dc[preferred];
        int nr = tileRow + dr[preferred];
        if (isTileFree(nc, nr, panel)) {
            targetTileCol = nc;
            targetTileRow = nr;
            moving = true;
            return;
        }
        // Иначе случайный шаг
        tryStartMove(panel);
    }

    //Проверяет, свободен ли тайл (нет стены, в пределах экрана).
    private boolean isTileFree(int col, int row, GamePanel panel) {
        int px = col * GamePanel.TILE + SPRITE_OFFSET;
        int py = GamePanel.UI_HEIGHT + row * GamePanel.TILE + SPRITE_OFFSET;

        if (px < 0 || py < GamePanel.UI_HEIGHT) return false;
        if (px + 28 > GamePanel.WIDTH)          return false;
        if (py + 28 > GamePanel.HEIGHT)         return false;

        return !panel.intersectsWallOrBox(new Rectangle(px, py, 28, 28));
    }

    private void facePlayer(PlayerTank player) {
        int px = player.getX() + 14;
        int py = player.getY() + 14;
        int ex = x + 14;
        int ey = y + 14;

        if (Math.abs(py - ey) >= Math.abs(px - ex))
            dir = (py < ey) ? 0 : 2;
        else
            dir = (px < ex) ? 3 : 1;
    }

    private Bullet createBullet() {
        return new Bullet(x + 14 - 4, y + 14 - 4, dir, false);
    }

    
    private boolean canSeePlayer(PlayerTank player, GamePanel panel) {
        int px = player.getX() + 14;
        int py = player.getY() + 14;
        int ex = x + 14;
        int ey = y + 14;

        int adx = Math.abs(px - ex);
        int ady = Math.abs(py - ey);
        int tolerance = GamePanel.TILE / 2;

        if (ady <= tolerance) {
            // Горизонталь
            int x0 = Math.min(px, ex), x1 = Math.max(px, ex);
            for (int xx = x0; xx <= x1; xx += 4)
                if (panel.intersectsWallOrBox(new Rectangle(xx - 2, ey - 2, 4, 4))) return false;
            return true;
        }
        if (adx <= tolerance) {
            // Вертикаль
            int y0 = Math.min(py, ey), y1 = Math.max(py, ey);
            for (int yy = y0; yy <= y1; yy += 4)
                if (panel.intersectsWallOrBox(new Rectangle(ex - 2, yy - 2, 4, 4))) return false;
            return true;
        }
        return false;
    }

    public void damage(int amount) {
        hp -= amount;
        if (hp <= 0) destroyed = true;
    }

    public boolean isDestroyed() { return destroyed; }
    public int getDamage()       { return damage; }
    public Rectangle getBounds() { return new Rectangle(x, y, 28, 28); }

    public void draw(Graphics g) {
        if (destroyed) return;

        g.setColor(type == Type.LIGHT ? Color.RED : Color.BLUE);
        g.fillRect(x, y, 28, 28);
        g.setColor(Color.BLACK);
        g.drawRect(x, y, 28, 28);

        // HP бар
        g.setColor(Color.DARK_GRAY);
        g.fillRect(x, y - 6, 28, 4);
        int hpW = (int)(28.0 * Math.max(0, hp) / maxHp);
        g.setColor(Color.GREEN);
        g.fillRect(x, y - 6, hpW, 4);

        // Пушка
        g.setColor(Color.WHITE);
        switch (dir) {
            case 0 -> g.fillRect(x + 12, y - 6,  4, 6);
            case 1 -> g.fillRect(x + 28, y + 12, 6, 4);
            case 2 -> g.fillRect(x + 12, y + 28, 4, 6);
            case 3 -> g.fillRect(x - 6,  y + 12, 6, 4);
        }
    }
}