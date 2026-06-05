package kurs;

import java.awt.*;

public class Bullet {

    private int x;
    private int y;
    private final int size = 8;
    private final int dir; // 0=UP,1=RIGHT,2=DOWN,3=LEFT
    private final boolean friendly; // true — от игрока, false — от врага
    private final int speed = 8;

    private boolean alive = true;
    private int life = 120;

    public Bullet(int x, int y, int dir, boolean friendly) {
        this.x = x;
        this.y = y;
        this.dir = dir;
        this.friendly = friendly;
    }

    public void update() {
        if (!alive) return;

        switch (dir) {
            case 0 -> y -= speed;
            case 1 -> x += speed;
            case 2 -> y += speed;
            case 3 -> x -= speed;
        }

        life--;
        if (life <= 0) alive = false;

        if (x < -size || y < -size || x > GamePanel.WIDTH + size || y > GamePanel.HEIGHT + size) {
            alive = false;
        }
    }

    public void draw(Graphics g) {
        if (!alive) return;

        g.setColor(friendly ? Color.GREEN.brighter() : Color.ORANGE);
        g.fillOval(x, y, size, size);
        g.setColor(Color.BLACK);
        g.drawOval(x, y, size, size);
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, size, size);
    }

    public boolean isAlive() { return alive; }
    public void setAlive(boolean v) { this.alive = v; }
    public boolean isFriendly() { return friendly; }
}
