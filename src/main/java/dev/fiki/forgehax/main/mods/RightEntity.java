package dev.fiki.forgehax.main.mods;

import com.google.common.collect.Maps;
import dev.fiki.forgehax.main.mods.managers.PositionRotationManager;
import dev.fiki.forgehax.main.mods.managers.PositionRotationManager.RotationState;
import dev.fiki.forgehax.main.mods.services.TickRateService;
import dev.fiki.forgehax.main.util.Utils;
import dev.fiki.forgehax.main.util.cmd.argument.Arguments;
import dev.fiki.forgehax.main.util.cmd.settings.*;
import dev.fiki.forgehax.main.util.cmd.settings.maps.SimpleSettingMap;
import dev.fiki.forgehax.main.util.common.PriorityEnum;
import dev.fiki.forgehax.main.util.entity.EntityUtils;
import dev.fiki.forgehax.main.util.key.BindingHelper;
import dev.fiki.forgehax.main.util.math.Angle;
import dev.fiki.forgehax.main.util.math.AngleHelper;
import dev.fiki.forgehax.main.util.mod.Category;
import dev.fiki.forgehax.main.util.mod.ToggleMod;
import dev.fiki.forgehax.main.util.mod.loader.RegisterMod;
import dev.fiki.forgehax.main.util.projectile.Projectile;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static dev.fiki.forgehax.main.Common.*;

@RegisterMod
public class RightEntity extends ToggleMod implements PositionRotationManager.MovementUpdateListener {

  private static Entity target = null;

  public static void setTarget(Entity target) {
    RightEntity.target = target;
  }

  public static Entity getTarget() {
    return target;
  }

  enum Selector {
    CROSSHAIR,
    DISTANCE,
  }

  private final SimpleSettingMap<String, String> entityTypes = newSettingMap(String.class, String.class)
      .name("types")
      .description("Add entity types")
      .keyArgument(Arguments.newStringArgument().label("entity").build())
      .valueArgument(Arguments.newStringArgument().label("item id").build())
      .supplier(Maps::newHashMap)
      .build();

  private final BooleanSetting silent = newBooleanSetting()
      .name("silent")
      .description("Wont look at target when aiming")
      .defaultTo(true)
      .build();

  private final BooleanSetting auto_attack = newBooleanSetting()
      .name("auto-right")
      .description("Automatically attack when target found")
      .defaultTo(true)
      .build();

  private final BooleanSetting hold_target = newBooleanSetting()
      .name("hold-target")
      .description("Keep first caught target until it becomes no longer valid")
      .defaultTo(false)
      .build();

  private final BooleanSetting vis_check = newBooleanSetting()
      .name("trace")
      .description("Check if the target is visible before acquiring")
      .defaultTo(false)
      .build();

  private final BooleanSetting lag_compensation = newBooleanSetting()
      .name("lag-compensation")
      .description("Compensate for server lag")
      .defaultTo(true)
      .build();

  private final IntegerSetting fov = newIntegerSetting()
      .name("fov")
      .description("Aimbot field of view")
      .defaultTo(180)
      .min(0)
      .max(180)
      .build();

  private final DoubleSetting range = newDoubleSetting()
      .name("range")
      .description("Aimbot range")
      .defaultTo(4.5D)
      .build();

  private final FloatSetting cooldown_percent = newFloatSetting()
      .name("cooldown_percent")
      .description("Minimum cooldown percent for next strike")
      .defaultTo(100F)
      .min(0F)
      .build();

  private final BooleanSetting projectile_aimbot = newBooleanSetting()
      .name("proj-aimbot")
      .description("Projectile aimbot")
      .defaultTo(true)
      .build();

  private final BooleanSetting projectile_auto_attack = newBooleanSetting()
      .name("proj-auto-attack")
      .description("Automatically attack when target found for projectile weapons")
      .defaultTo(true)
      .build();

  private final BooleanSetting projectile_trace_check = newBooleanSetting()
      .name("projectile-trace")
      .description("Check the trace of each target if holding a weapon that fires a projectile")
      .defaultTo(true)
      .build();

  private final DoubleSetting projectile_range = newDoubleSetting()
      .name("projectile-range")
      .description("Projectile aimbot range")
      .defaultTo(100D)
      .build();

  private final EnumSetting<Selector> selector = newEnumSetting(Selector.class)
      .name("selector")
      .description("The method used to select a target from a group")
      .defaultTo(Selector.CROSSHAIR)
      .build();

  public RightEntity() {
    super(Category.MISC, "RightEntity", false, "自动右键相关实体(虚无世界刷附魔)");
  }

  private double getLagComp() {
    if (lag_compensation.getValue()) {
      return -(20.D - TickRateService.getInstance().getTickrate());
    } else {
      return 0.D;
    }
  }

  private boolean canAttack(ClientPlayerEntity localPlayer, Entity target) {
    final float cdRatio = cooldown_percent.getValue() / 100F;
    final float cdOffset = cdRatio <= 1F ? 0F : -(localPlayer.getCooldownPeriod() * (cdRatio - 1F));
    return localPlayer.getCooledAttackStrength((float) getLagComp() + cdOffset)
        >= (Math.min(1F, cdRatio))
        && (auto_attack.getValue() || getGameSettings().keyBindAttack.isKeyDown()); // need to work on this
  }

  private Projectile getHeldProjectile() {
    return Projectile.getProjectileByItemStack(getLocalPlayer().getHeldItem(Hand.MAIN_HAND));
  }

  private boolean isHoldingProjectileItem() {
    return !getHeldProjectile().isNull();
  }

  private boolean isProjectileAimbotActivated() {
    return projectile_aimbot.getValue() && isHoldingProjectileItem();
  }

  private boolean isVisible(Entity target) {
    if (isProjectileAimbotActivated() && projectile_trace_check.getValue()) {
      return getHeldProjectile().canHitEntity(EntityUtils.getEyePos(getLocalPlayer()), target);
    } else {
      return !vis_check.getValue() || getLocalPlayer().canEntityBeSeen(target);
    }
  }

  private Vec3d getAttackPosition(Entity entity) {
    return EntityUtils.getInterpolatedPos(entity, 1).add(0, entity.getEyeHeight() / 2, 0);
  }

  /**
   * Check if the entity is a valid target to acquire
   */
  private boolean filterTarget(Vec3d pos, Vec3d viewNormal, Angle angles, Entity entity) {
    final Vec3d tpos = getAttackPosition(entity);
    return Optional.of(entity)
        .filter(EntityUtils::isLiving)
        .filter(EntityUtils::isAlive)
        .filter(EntityUtils::isValidEntity)
        .filter(ent -> !ent.equals(getLocalPlayer()))
        .filter(this::isFiltered)
        .filter(ent -> isInRange(tpos, pos))
        .filter(ent -> isInFov(angles, tpos.subtract(pos)))
        .filter(this::isVisible)
        .isPresent();
  }

  /**
   * 判断类型是否匹配以及玩家手上的物品
   *
   * @param entity
   * @return
   */
  private boolean isFiltered(Entity entity) {
    if (entityTypes.size() == 0) return false;
    return entityTypes.entrySet().stream().anyMatch(entry ->
        entry.getKey().equalsIgnoreCase(entity.getEntityString()) &&
            Objects.requireNonNull(getLocalPlayer().getHeldItemMainhand().getItem().getRegistryName()).toString().equals(entry.getValue())
    );
  }

  private boolean isInRange(Vec3d from, Vec3d to) {
    double dist = isProjectileAimbotActivated() ? projectile_range.getValue() : range.getValue();
    return dist <= 0 || from.distanceTo(to) <= dist;
  }

  private boolean isInFov(Angle angle, Vec3d pos) {
    double fov = this.fov.getValue();
    if (fov >= 180) {
      return true;
    } else {
      Angle look = AngleHelper.getAngleFacingInDegrees(pos);
      Angle diff = angle.sub(look.getPitch(), look.getYaw()).normalize();
      return Math.abs(diff.getPitch()) <= fov && Math.abs(diff.getYaw()) <= fov;
    }
  }

  private double selecting(
      final Vec3d pos, final Vec3d viewNormal, final Angle angles, final Entity entity) {
    switch (selector.getValue()) {
      case DISTANCE:
        return getAttackPosition(entity).subtract(pos).lengthSquared();
      case CROSSHAIR:
      default:
        return getAttackPosition(entity)
            .subtract(pos)
            .normalize()
            .subtract(viewNormal)
            .lengthSquared();
    }
  }

  private Entity findTarget(final Vec3d pos, final Vec3d viewNormal, final Angle angles) {
    return StreamSupport.stream(getWorld().getAllEntities().spliterator(), false)
        .filter(entity -> filterTarget(pos, viewNormal, angles, entity))
        .min(Comparator.comparingDouble(entity -> selecting(pos, viewNormal, angles, entity)))
        .orElse(null);
  }

  @Override
  protected void onEnabled() {
    PositionRotationManager.getManager().register(this, PriorityEnum.HIGHEST);
    BindingHelper.disableContextHandler(getGameSettings().keyBindAttack);
  }

  @Override
  public void onDisabled() {
    PositionRotationManager.getManager().unregister(this);
    BindingHelper.restoreContextHandler(getGameSettings().keyBindAttack);
  }

  @Override
  public void onLocalPlayerMovementUpdate(RotationState.Local state) {
    Vec3d pos = EntityUtils.getEyePos(getLocalPlayer());
    Vec3d look = getLocalPlayer().getLookVec();
    Angle angles = AngleHelper.getAngleFacingInDegrees(look);

    Entity t = getTarget();
    if (!hold_target.getValue()
        || t == null
        || !filterTarget(pos, look.normalize(), angles, getTarget())) {
      setTarget(t = findTarget(pos, look.normalize(), angles));
    }

    if (t == null) {
      return;
    }

    final Entity tar = t;
    Projectile projectile = getHeldProjectile();

    if (projectile.isNull() || !projectile_aimbot.getValue()) {
      // melee aimbot
      Angle va = Utils.getLookAtAngles(t).normalize();
      state.setViewAngles(va, silent.getValue());

      if (canAttack(getLocalPlayer(), tar)) {
        state.invokeLater(rs -> {
          String itemRegistry = entityTypes.get(tar.getEntityString());
          ResourceLocation registryName = getLocalPlayer().getHeldItemMainhand().getItem().getRegistryName();
          if (registryName != null && registryName.toString().equals(itemRegistry)) {
            getPlayerController().interactWithEntity(getLocalPlayer(), tar, Hand.MAIN_HAND);
            getLocalPlayer().swingArm(Hand.MAIN_HAND);
          }
        });
      }
    }
  }
}
