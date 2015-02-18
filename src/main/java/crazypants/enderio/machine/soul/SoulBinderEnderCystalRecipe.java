package crazypants.enderio.machine.soul;

import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.item.ItemStack;
import crazypants.enderio.EnderIO;
import crazypants.enderio.config.Configs;
import crazypants.enderio.material.FrankenSkull;
import crazypants.enderio.material.Material;

public class SoulBinderEnderCystalRecipe extends AbstractSoulBinderRecipe {

  public static SoulBinderEnderCystalRecipe instance = new SoulBinderEnderCystalRecipe();

  private SoulBinderEnderCystalRecipe() {
    super(Configs.soulBinderEnderCystalRF,Configs.soulBinderEnderCystalLevels, "SoulBinderEnderCystalRecipe", "SpecialMobs.SpecialEnderman", "Enderman");
  }

  @Override
  public ItemStack getInputStack() {    
    return new ItemStack(EnderIO.itemMaterial, 1, Material.VIBRANT_CYSTAL.ordinal());
  }

  @Override
  public ItemStack getOutputStack() {
    return new ItemStack(EnderIO.itemMaterial, 1, Material.ENDER_CRYSTAL.ordinal());
  }

}
