package dev.fiki.forgehax.main.mods.commands;

import dev.fiki.forgehax.main.util.entity.EntityUtils;
import dev.fiki.forgehax.main.util.mod.CommandMod;
import dev.fiki.forgehax.main.util.mod.loader.RegisterMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static dev.fiki.forgehax.main.Common.getLocalPlayer;
import static dev.fiki.forgehax.main.Common.getWorld;

@RegisterMod
public class EntityCommand extends CommandMod {

  public EntityCommand() {
    super("EntityCommand");
  }

  {
    newSimpleCommand()
        .name("entitys")
        .description("Show around entitys type name")
        .executor(args -> {
          Vec3d pos = EntityUtils.getEyePos(getLocalPlayer());
          String collect = StreamSupport.stream(getWorld().getAllEntities().spliterator(), false)
              .filter(EntityUtils::isLiving)
              .filter(EntityUtils::isAlive)
              .filter(EntityUtils::isValidEntity)
              .filter(entity -> !(entity instanceof PlayerEntity))
              .filter(entity -> EntityUtils.getEyePos(entity).distanceTo(pos) <= 5)
              .map(entity -> entity.getType().getName().getString())
              .collect(Collectors.joining(","));
          args.inform(collect);
        }).build();
  }
}
