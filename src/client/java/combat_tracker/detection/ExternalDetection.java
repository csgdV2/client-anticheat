package combat_tracker.detection;

import combat_tracker.CombatTrackerClient;
import com.sun.jna.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.util.ArrayList;
import java.util.List;

public final class ExternalDetection {
    private static final ExternalDetection INSTANCE = new ExternalDetection();
    private final BehaviorEngine behavior = new BehaviorEngine();
    private final WindowsInputMonitor windows = new WindowsInputMonitor();
    private final long[] nativeBaseline = new long[4];
    private final long[] firstObserved = new long[4];
    private boolean session;
    private boolean sampling;
    private Vec3 previousEye;
    private Vec3 previousLook;
    private Player aimCandidate;
    private int heldType;
    private long shieldStart;
    private boolean shieldAttack;

    public static ExternalDetection get() {
        return INSTANCE;
    }

    public void initialize() {
        windows.start();
    }

    public void beginSession() {
        behavior.reset();
        for (int index = 0; index < 4; index++) {
            nativeBaseline[index] = windows.count(index);
            firstObserved[index] = 0;
        }
        clearContext();
        session = true;
    }

    public void endSession() {
        session = false;
        pause();
    }

    public void shutdown() {
        endSession();
        windows.stop();
    }

    public String coverage() {
        return windows.status();
    }

    public void tick(Minecraft client) {
        if (!session || !GameplayContext.active(client)) pause();
    }

    private void pause() {
        windows.setGameplayWindow(0, false);
        if (sampling) behavior.pause();
        clearContext();
    }

    private void clearContext() {
        sampling = false;
        previousEye = previousLook = null;
        aimCandidate = null;
        heldType = 0;
        shieldStart = 0;
        shieldAttack = false;
    }

    public void frame(Minecraft client) {
        if (!session || !GameplayContext.active(client)) {
            pause();
            return;
        }
        if (Platform.isWindows()) {
            windows.setGameplayWindow(GLFWNativeWin32.glfwGetWin32Window(GameplayContext.windowHandle(client)), true);
        }
        Vec3 eye = client.player.getEyePosition();
        if (previousEye != null && eye.distanceToSqr(previousEye) > 16) {
            behavior.pause();
            clearContext();
        }
        sampling = true;
        updateObservedTimes();
        previousEye = eye;
        Vec3 look = client.player.getViewVector(1.0F).normalize();
        double rotation = previousLook == null ? 0 : angle(look, previousLook);
        previousLook = look;
        int crosshairTarget = -1;
        Player shieldOpponent = null;
        if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player player
                && player != client.player && player.isAlive()
                && player.getBoundingBox().distanceToSqr(eye) <= 9.1) {
            crosshairTarget = player.getId();
            shieldOpponent = player;
        }
        if (aimCandidate == null || !aimCandidate.isAlive() || aimCandidate.isRemoved()
                || aimCandidate.level() != client.level
                || !client.player.hasLineOfSight(aimCandidate)
                || aimCandidate.getBoundingBox().getCenter().distanceToSqr(eye) > 36
                || angle(look, aimCandidate.getBoundingBox().getCenter().subtract(eye)) > 40) {
            aimCandidate = null;
            double best = 30;
            for (Player candidate : client.level.players()) {
                if (candidate == client.player || !candidate.isAlive()
                        || candidate.getBoundingBox().getCenter().distanceToSqr(eye) > 36
                        || !client.player.hasLineOfSight(candidate)) continue;
                double error = angle(look, candidate.getBoundingBox().getCenter().subtract(eye));
                if (error < best) {
                    best = error;
                    aimCandidate = candidate;
                }
            }
        }
        behavior.frame(System.nanoTime(), System.currentTimeMillis(), crosshairTarget,
                aimCandidate == null ? -1 : aimCandidate.getId(),
                aimCandidate == null ? Double.NaN : angle(look, aimCandidate.getBoundingBox().getCenter().subtract(eye)), rotation);
        sampleShield(client, shieldOpponent, System.nanoTime());
        observeHeld(client, System.nanoTime());
    }

    public void attack(Entity target) {
        Minecraft client = Minecraft.getInstance();
        if (!session || !GameplayContext.active(client)) return;
        long now = System.nanoTime();
        Player opponent = target instanceof Player player && player.isAlive() ? player : null;
        sampleShield(client, opponent, now);
        observeHeld(client, now);
        behavior.shields().attack(now, System.currentTimeMillis(), opponent == null ? -1 : opponent.getId(),
                shieldBlocking(opponent), heldType(client), client.player.getAttackStrengthScale(0.5F) >= 0.9F);
        if (target instanceof Player player && player.isAlive()) {
            behavior.attack(now, System.currentTimeMillis(), player.getId(),
                    CombatTrackerClient.clickNano, client.player.getAttackStrengthScale(0.5F) >= 0.9F);
            if (heldType == 2 && shieldStart != 0 && player.isBlocking()) shieldAttack = true;
        }
    }

    public void swingMiss() {
        Minecraft client = Minecraft.getInstance();
        if (!session || !GameplayContext.active(client) || heldType(client) != 2) return;
        Player opponent = client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player player
                ? player : null;
        sampleShield(client, opponent, System.nanoTime());
        behavior.shields().miss();
    }

    private void sampleShield(Minecraft client, Player opponent, long nano) {
        behavior.shields().frame(nano, System.currentTimeMillis(), opponent == null ? -1 : opponent.getId(),
                shieldBlocking(opponent), heldType(client));
    }

    private static boolean shieldBlocking(Player player) {
        return player != null && player.isBlocking()
                && (player.getMainHandItem().is(Items.SHIELD) || player.getOffhandItem().is(Items.SHIELD));
    }

    private static int heldType(Minecraft client) {
        var item = client.player.getMainHandItem();
        return item.is(ItemTags.SWORDS) ? 1 : item.is(ItemTags.AXES) ? 2 : 0;
    }

    private void observeHeld(Minecraft client, long now) {
        var held = client.player.getMainHandItem();
        int type = held.is(ItemTags.SWORDS) ? 1 : held.is(ItemTags.AXES) ? 2 : 0;
        if (type == 2 && heldType == 1) {
            shieldStart = now;
            shieldAttack = false;
        } else if (type == 1 && heldType == 2 && shieldStart != 0) {
            if (shieldAttack) {
                behavior.shieldSwap((now - shieldStart) / 1_000_000.0, System.currentTimeMillis());
            }
            shieldStart = 0;
            shieldAttack = false;
        } else if (type == 0 || (shieldStart != 0 && now - shieldStart > 2_000_000_000L)) {
            shieldStart = 0;
            shieldAttack = false;
        }
        heldType = type;
    }

    public List<ExternalFinding> findings() {
        updateObservedTimes();
        List<ExternalFinding> out = new ArrayList<>(behavior.findings());
        String[] labels = {"Windows software generated mouse buttons", "Windows software generated key presses",
                "Windows software generated pointer movement", "Windows software generated scrolling"};
        for (int index = 0; index < 4; index++) {
            int count = (int) Math.min(1_000_000, Math.max(0, windows.count(index) - nativeBaseline[index]));
            if (count == 0) continue;
            long now = windows.lastEventEpochMs(index);
            out.add(new ExternalFinding("windows-" + index, labels[index], "OBSERVED_INPUT", count,
                    count, 0, "Windows marked these events as injected while Minecraft gameplay was focused. The originating program is unknown; accessibility tools and remappers may generate these events. This is not proof of cheating.",
                    firstObserved[index], now));
        }
        return out;
    }

    private void updateObservedTimes() {
        for (int index = 0; index < 4; index++) {
            if (firstObserved[index] == 0 && windows.count(index) > nativeBaseline[index]) {
                firstObserved[index] = windows.lastEventEpochMs(index);
            }
        }
    }

    public int currentBehaviorScore() {
        return behavior.currentScore(System.currentTimeMillis());
    }

    private static double angle(Vec3 first, Vec3 second) {
        double scale = first.length() * second.length();
        if (scale < 1.0e-9) return 0;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, first.dot(second) / scale))));
    }
}
