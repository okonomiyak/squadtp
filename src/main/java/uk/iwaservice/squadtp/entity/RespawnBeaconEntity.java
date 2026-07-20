package uk.iwaservice.squadtp.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import uk.iwaservice.squadtp.squad.Squad;
import uk.iwaservice.squadtp.squad.SquadManager;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * A one-hit-point squad respawn point. Immune to damage from squad members
 * and players who share the squad leader's scoreboard team, but destroyed by
 * a single hit from a hostile mob or any other player. Has no AI - it just
 * stands where it was placed until used up or destroyed.
 */
public class RespawnBeaconEntity extends PathfinderMob {

    @Nullable
    private UUID squadId;

    public RespawnBeaconEntity(EntityType<? extends RespawnBeaconEntity> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 0.0);
    }

    @Override
    protected void registerGoals() {
        // Intentionally no goals: the beacon never moves or acts on its own.
    }

    public void setSquadId(UUID id) {
        this.squadId = id;
    }

    @Nullable
    public UUID getSquadId() {
        return squadId;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false; // don't die to fall damage right after being placed
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player && isFriendly(player)) {
            return false;
        }
        if (attacker instanceof Mob mob && !(mob instanceof Enemy)) {
            return false; // a passive/neutral mob bumping into it shouldn't destroy it
        }
        return super.hurt(source, amount);
    }

    /** True if the attacker is a squad member, or shares the squad leader's scoreboard team. */
    private boolean isFriendly(ServerPlayer player) {
        if (squadId == null) {
            return false;
        }
        SquadManager manager = SquadManager.get(player.server);
        Squad squad = manager.getSquad(squadId);
        if (squad == null) {
            return false;
        }
        if (squad.isMember(player.getUUID())) {
            return true;
        }
        var scoreboard = player.server.getScoreboard();
        PlayerTeam attackerTeam = scoreboard.getPlayersTeam(player.getGameProfile().getName());
        PlayerTeam squadTeam = scoreboard.getPlayersTeam(squad.getMemberName(squad.getLeader()));
        return Objects.equals(attackerTeam, squadTeam);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level().isClientSide || squadId == null) {
            return;
        }
        MinecraftServer server = level().getServer();
        if (server == null) {
            return;
        }
        SquadManager manager = SquadManager.get(server);
        Squad squad = manager.getSquad(squadId);
        if (squad != null && getUUID().equals(squad.getBeaconEntityId())) {
            manager.onBeaconDestroyed(server, squad);
        }
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (squadId != null) {
            tag.putUUID("SquadId", squadId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("SquadId")) {
            squadId = tag.getUUID("SquadId");
        }
    }
}
