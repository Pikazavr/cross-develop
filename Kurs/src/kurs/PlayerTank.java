package kurs;

import java.awt.*;

public class PlayerTank {

    private int x;
    private int y;
    private int dir; // 0=UP, 1=RIGHT, 2=DOWN, 3=LEFT
    private final int width = 28;
    private final int height = 28;

    private int hp = 20;
    private final int maxHp = 20;

    // Ограничение стрельбы (ms) — 0.25 секунды
    private long lastShotTime = 0;
    private static final long SHOOT_COOLDOWN_MS = 250;

    public PlayerTank(int startX, int startY) {
        this.x = startX;
        this.y = startY;
        this.dir = 0;
    }

    
    public void move(double dx, double dy, GamePanel panel) {
        if (dx == 0 && dy == 0) return;

        if (dx != 0 && dy != 0) {
            if (Math.abs(dx) >= Math.abs(dy)) dy = 0;
            else dx = 0;
        }

        if (dx > 0) dir = 1;
        else if (dx < 0) dir = 3;
        else if (dy > 0) dir = 2;
        else if (dy < 0) dir = 0;

        double newX = x + dx;
        double newY = y + dy;

        int uiHeight = GamePanel.UI_HEIGHT;
        if (newY < uiHeight) newY = uiHeight;

        if (newX < 0) newX = 0;
        if (newY < 0) newY = 0;
        if (newX + width > GamePanel.WIDTH) newX = GamePanel.WIDTH - width;
        if (newY + height > GamePanel.HEIGHT) newY = GamePanel.HEIGHT - height;

        Rectangle newRect = new Rectangle((int)newX, (int)newY, width, height);

        if (!panel.intersectsWallOrBox(newRect)) {
            x = (int)newX;
            y = (int)newY;
        } else {
            // Попробуем по одной оси
            Rectangle rx = new Rectangle((int)(x + dx), y, width, height);
            Rectangle ry = new Rectangle(x, (int)(y + dy), width, height);

            if (dx != 0 && !panel.intersectsWallOrBox(rx)) {
                x = rx.x;
                return;
            }
            if (dy != 0 && !panel.intersectsWallOrBox(ry)) {
                y = ry.y;
            }
        }
    }

    public Bullet shoot() {
        long now = System.currentTimeMillis();
        if (now - lastShotTime < SHOOT_COOLDOWN_MS) return null;
        lastShotTime = now;

        int bx = x + width / 2 - 4;
        int by = y + height / 2 - 4;
        return new Bullet(bx, by, dir, true);
    }

    public void damage(int amount) {
        hp -= amount;
        if (hp < 0) hp = 0;
    }

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }

    public Rectangle getBounds() { return new Rectangle(x, y, width, height); }

    public void draw(Graphics g) {
        g.setColor(Color.GREEN.darker());
        g.fillRect(x, y, width, height);

        g.setColor(Color.WHITE);
        g.drawRect(x, y, width, height);

        g.setColor(Color.WHITE);
        switch (dir) {
            case 0 -> g.fillRect(x + width/2 - 3, y - 8, 6, 8);
            case 1 -> g.fillRect(x + width, y + height/2 - 3, 8, 6);
            case 2 -> g.fillRect(x + width/2 - 3, y + height, 6, 8);
            case 3 -> g.fillRect(x - 8, y + height/2 - 3, 8, 6);
        }
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getDirection() { return dir; }

    public void resetPosition(int nx, int ny) {
        this.x = nx;
        this.y = ny;
        this.hp = maxHp;
    }
}
