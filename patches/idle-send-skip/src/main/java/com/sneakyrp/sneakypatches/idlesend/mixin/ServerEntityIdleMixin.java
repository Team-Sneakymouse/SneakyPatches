package com.sneakyrp.sneakypatches.idlesend.mixin;

import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity;
import com.sneakyrp.sneakypatches.idlesend.IdleSend;
import com.sneakyrp.sneakypatches.idlesend.IdleSendSkip;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Decides for the tracker loop whether an entity's {@code sendChanges()} is left out; see {@link IdleSendSkip}. With
 * no viewer the call has nobody to send to, so all a skip leaves behind is a stale last-sent state. The spawn packet
 * of the next viewer carries the entity's current position but the last-sent rotation, velocity and metadata, and
 * later movement is encoded against the last-sent position. So before that packet is built the last-sent state is set
 * to the entity's current state, exactly as Paper's constructor sets it for a new tracker.
 *
 * <p>Never skipped: players (their own connection is sent their changes) and plugin NPCs that subclass
 * {@link ServerPlayer}; an entity whose tracker has a viewer by Paper's own check, whichever set a plugin put in the
 * tracker; and a {@code ServerEntity} that is not run by the server's tracker.
 */
@Mixin(value = ServerEntity.class, remap = false)
abstract class ServerEntityIdleMixin implements IdleSend {
    @Shadow(remap = false)
    @Final
    private Entity entity;
    @Shadow(remap = false)
    @Final
    private Set<ServerPlayerConnection> trackedPlayers;
    @Shadow(remap = false)
    @Final
    private ServerEntity.Synchronizer synchronizer;
    @Shadow(remap = false)
    @Final
    private VecDeltaCodec positionCodec;
    @Shadow(remap = false)
    private byte lastSentYRot;
    @Shadow(remap = false)
    private byte lastSentXRot;
    @Shadow(remap = false)
    private byte lastSentYHeadRot;
    @Shadow(remap = false)
    private Vec3 lastSentMovement;
    @Shadow(remap = false)
    private boolean wasOnGround;
    @Shadow(remap = false)
    private List<SynchedEntityData.DataValue<?>> trackedDataValues;
    @Shadow(remap = false)
    private int teleportDelay;

    /** At least one send was skipped since the last-sent state was last brought up to date. */
    @Unique
    private boolean sneakyPatches$idle;

    @Override
    public boolean sneakyPatches$skipSend() {
        if (trackedPlayers.isEmpty() && !(entity instanceof ServerPlayer)
            && synchronizer instanceof EntityTrackerTrackedEntity tracker && !tracker.moonrise$hasPlayers()) {
            // The part of sendChanges() that is not about viewers: a living entity's potion effects set its
            // invisible and glowing flags here, and the server reads those (mob targeting).
            entity.updateDataBeforeSync();
            sneakyPatches$idle = true;
            IdleSendSkip.skipped++;
            return true;
        }
        if (sneakyPatches$idle) {
            // A viewer was added without pairing data (not a Paper path). Its client's position base is unknown,
            // so make this send an absolute position sync, as Paper does for hard-colliding entities.
            sneakyPatches$idle = false;
            teleportDelay = 9999;
        }
        return false;
    }

    @Inject(method = "sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"), remap = false, require = 1)
    private void sneakyPatches$refreshIdle(ServerPlayer player,
        Consumer<Packet<? super ClientGamePacketListener>> packets, CallbackInfo ci) {
        if (!sneakyPatches$idle) return;
        sneakyPatches$idle = false;
        IdleSendSkip.refreshed++;
        // The fields ServerEntity's constructor sets, from the same sources.
        positionCodec.setBase(entity.trackingPosition());
        lastSentMovement = entity.getDeltaMovement();
        lastSentYRot = Mth.packDegrees(entity.getYRot());
        lastSentXRot = Mth.packDegrees(entity.getXRot());
        lastSentYHeadRot = Mth.packDegrees(entity.getYHeadRot());
        wasOnGround = entity.onGround();
        trackedDataValues = entity.getEntityData().getNonDefaultValues();
    }
}
