package kurs;

import java.util.function.Consumer;

public class GameEvents {
    private static Consumer<Bullet> spawner;

    public static void setBulletSpawner(Consumer<Bullet> s) {
        spawner = s;
    }

    public static void spawnEnemyBullet(Bullet b) {
        if (spawner != null && b != null) spawner.accept(b);
    }
}
