package crazypants.enderio.item.darksteel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import cofh.api.energy.IEnergyContainerItem;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.EnderIO;
import crazypants.enderio.config.Configs;
import crazypants.enderio.machine.solar.TileEntitySolarPanel;
import crazypants.enderio.network.PacketHandler;
import crazypants.util.Util;
import crazypants.vecmath.VecmathUtil;
import crazypants.vecmath.Vector3d;
import crazypants.vecmath.Vector4d;

public class DarkSteelController {

  public static final DarkSteelController instance = new DarkSteelController();

  private final AttributeModifier[] walkModifiers = new AttributeModifier[] {
      new AttributeModifier(new UUID(12879874982l, 320981923), "generic.movementSpeed", SpeedUpgrade.WALK_MULTIPLIERS[0], 1),
      new AttributeModifier(new UUID(12879874982l, 320981923), "generic.movementSpeed", SpeedUpgrade.WALK_MULTIPLIERS[1], 1),
      new AttributeModifier(new UUID(12879874982l, 320981923), "generic.movementSpeed", SpeedUpgrade.WALK_MULTIPLIERS[2], 1),
  };

  private final AttributeModifier[] sprintModifiers = new AttributeModifier[] {
      new AttributeModifier(new UUID(12879874982l, 320981923), "generic.movementSpeed", SpeedUpgrade.SPRINT_MULTIPLIERS[0], 1),
      new AttributeModifier(new UUID(12879874982l, 320981923), "generic.movementSpeed", SpeedUpgrade.SPRINT_MULTIPLIERS[1], 1),
      new AttributeModifier(new UUID(12879874982l, 320981923), "generic.movementSpeed", SpeedUpgrade.SPRINT_MULTIPLIERS[2], 1),
  };

  private final AttributeModifier swordDamageModifierPowered = new AttributeModifier(new UUID(63242325, 320981923), "Weapon modifier",
      2, 0);

  private boolean wasJumping;
  private int jumpCount;
  private int ticksSinceLastJump;

  private final Map<UUID, Boolean> glideActiveMap = new HashMap<UUID, Boolean>();
  private final Map<UUID, Boolean> speedActiveMap = new HashMap<UUID, Boolean>();
  private final Map<UUID, Boolean> stepAssistActiveMap = new HashMap<UUID, Boolean>();
  
  private boolean nightVisionActive = false;
  private boolean removeNightvision = false;

  private DarkSteelController() {
    PacketHandler.INSTANCE.registerMessage(PacketDarkSteelPowerPacket.class, PacketDarkSteelPowerPacket.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketUpgradeState.class, PacketUpgradeState.class, PacketHandler.nextID(), Side.SERVER);
  }

  public void setGlideActive(EntityPlayer player, boolean isGlideActive) {
    if(player.getGameProfile().getId() != null) {
      glideActiveMap.put(player.getGameProfile().getId(), isGlideActive);
    }
  }

  public boolean isGlideActive(EntityPlayer player) {
    Boolean isActive = glideActiveMap.get(player.getGameProfile().getId());
    if(isActive == null) {
      return false;
    }
    return isActive.booleanValue();
  }
  
  public void setSpeedActive(EntityPlayer player, boolean isSpeedActive) {
    if(player.getGameProfile().getId() != null) {
      speedActiveMap.put(player.getGameProfile().getId(), isSpeedActive);
    }    
  }
  
  public boolean isSpeedActive(EntityPlayer player) {
    Boolean isActive = speedActiveMap.get(player.getGameProfile().getId());
    if(isActive == null) {
      return true;
    }
    return isActive.booleanValue();
  }
  
  public void setStepAssistActive(EntityPlayer player, boolean isActive) {
    if(player.getGameProfile().getId() != null) {
      stepAssistActiveMap.put(player.getGameProfile().getId(), isActive);
    }    
  }
  
  public boolean isStepAssistActive(EntityPlayer player) {
    Boolean isActive = stepAssistActiveMap.get(player.getGameProfile().getId());
    if(isActive == null) {
      return true;
    }
    return isActive.booleanValue();
  }

  @SubscribeEvent
  public void onPlayerTick(TickEvent.PlayerTickEvent event) {
    EntityPlayer player = event.player;

    if(event.phase == Phase.START) {
      //boots
      updateStepHeightAndFallDistance(player);

      //leggings
      updateSpeed(player);

      //sword
      updateSword(player);

      updateGlide(player);

      updateSwim(player);
      
      updateSolar(player);
      
    }

  }

  private void updateSolar(EntityPlayer player) {
    // no processing on client
    if (player.worldObj.isRemote) {
      return;
    }
    
    ItemStack helm = player.getEquipmentInSlot(4);
    SolarUpgrade upgrade = SolarUpgrade.loadFromItem(helm);
    if(upgrade == null) {
      return;
    }
    
    int RFperSecond = Math.round((float) upgrade.getRFPerSec() * TileEntitySolarPanel.calculateLightRatio(player.worldObj, MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posY + 1), MathHelper.floor_double(player.posZ)));
    
    int leftover = RFperSecond % 20;
    boolean addExtraRF = player.worldObj.getTotalWorldTime() % 20 < leftover;
    
    int toAdd = (RFperSecond / 20) + (addExtraRF ? 1 : 0);

    if(toAdd != 0) {

      int nextIndex = player.getEntityData().getInteger("dsarmor:solar") % 4;

      for (int i = 0; i < 4 && toAdd > 0; i++) {
        ItemStack stack = player.inventory.armorInventory[nextIndex];
        if(stack != null && (EnergyUpgrade.loadFromItem(stack) != null || (Configs.darkSteelSolarChargeOthers && stack.getItem() instanceof IEnergyContainerItem))) {
          toAdd -= ((IEnergyContainerItem) stack.getItem()).receiveEnergy(stack, toAdd, false);
        }
        nextIndex = (nextIndex + 1) % 4;
      }
      
      player.getEntityData().setInteger("dsarmor:solar", nextIndex);
    }
  }

  private void updateSwim(EntityPlayer player) {
    ItemStack boots = player.getEquipmentInSlot(1);
    SwimUpgrade upgrade = SwimUpgrade.loadFromItem(boots);
    if(upgrade == null) {
      return;
    }
    if(player.isInWater()) {
      player.motionX *= 1.1;
      player.motionZ *= 1.1;
    }
  }

  private void updateGlide(EntityPlayer player) {
    if(!isGlideActive(player) || !isGliderUpgradeEquipped(player)) {
      return;
    }

    if(!player.onGround && player.motionY < 0 && !player.isSneaking() && !player.isInWater()) {

      double horizontalSpeed = Configs.darkSteelGliderHorizontalSpeed;
      double verticalSpeed = Configs.darkSteelGliderVerticalSpeed;
      if(player.isSprinting()) {
        verticalSpeed = Configs.darkSteelGliderVerticalSpeedSprinting;
      }

      Vector3d look = Util.getLookVecEio(player);
      Vector3d side = new Vector3d();
      side.cross(new Vector3d(0, 1, 0), look);
      Vector3d playerPos = new Vector3d(player.prevPosX, player.prevPosY, player.prevPosZ);
      Vector3d b = new Vector3d(playerPos);
      b.y += 1;
      Vector3d c = new Vector3d(playerPos);
      c.add(side);
      Vector4d plane = new Vector4d();
      VecmathUtil.computePlaneEquation(playerPos, b, c, plane);
      double dist = Math.abs(VecmathUtil.distanceFromPointToPlane(plane, new Vector3d(player.posX, player.posY, player.posZ)));
      double minDist = 0.15;
      if(dist < minDist) {
        double dropRate = (minDist * 10) - (dist * 10);
        verticalSpeed = verticalSpeed + (verticalSpeed * dropRate * 8);
        horizontalSpeed -= (0.02 * dropRate);
      }

      double x = Math.cos(Math.toRadians(player.rotationYawHead + 90))
          * horizontalSpeed;
      double z = Math.sin(Math.toRadians(player.rotationYawHead + 90))
          * horizontalSpeed;

      player.motionX += x;
      player.motionZ += z;

      player.motionY = verticalSpeed;
      player.fallDistance = 0f;

    }

  }

  public boolean isGliderUpgradeEquipped(EntityPlayer player) {
    ItemStack chestPlate = player.getEquipmentInSlot(3);
    GliderUpgrade glideUpgrade = GliderUpgrade.loadFromItem(chestPlate);
    if(glideUpgrade == null) {
      return false;
    }
    return true;
  }

  private void updateSword(EntityPlayer player) {
    if(ItemDarkSteelSword.isEquipped(player)) {
      IAttributeInstance attackInst = player.getAttributeMap().getAttributeInstance(SharedMonsterAttributes.attackDamage);
      attackInst.removeModifier(swordDamageModifierPowered);

      ItemStack sword = player.getCurrentEquippedItem();
      if(Configs.darkSteelSwordPowerUsePerHit <= 0 || EnergyUpgrade.getEnergyStored(sword) >= Configs.darkSteelSwordPowerUsePerHit) {
        attackInst.applyModifier(swordDamageModifierPowered);
      }
    }
  }

  private void updateSpeed(EntityPlayer player) {
    if(player.worldObj.isRemote || !player.onGround) {
      return;
    }

    IAttributeInstance moveInst = player.getAttributeMap().getAttributeInstance(SharedMonsterAttributes.movementSpeed);
    if(moveInst.getModifier(walkModifiers[0].getID()) != null) {
      moveInst.removeModifier(walkModifiers[0]); //any will so as they all have the same UID
    } else if(moveInst.getModifier(sprintModifiers[0].getID()) != null) {
      moveInst.removeModifier(sprintModifiers[0]);
    }

    ItemStack leggings = player.getEquipmentInSlot(2);
    SpeedUpgrade speedUpgrade = SpeedUpgrade.loadFromItem(leggings);
    if(leggings != null && leggings.getItem() == DarkSteelItems.itemDarkSteelLeggings && speedUpgrade != null && isSpeedActive(player)) {

      double horzMovement = Math.abs(player.distanceWalkedModified - player.prevDistanceWalkedModified);
      double costModifier = player.isSprinting() ? Configs.darkSteelSprintPowerCost : Configs.darkSteelWalkPowerCost;
      costModifier = costModifier + (costModifier * speedUpgrade.walkMultiplier);
      int cost = (int) (horzMovement * costModifier);
      int totalEnergy = getPlayerEnergy(player, DarkSteelItems.itemDarkSteelLeggings);

      if(totalEnergy > 0) {
        usePlayerEnergy(player, DarkSteelItems.itemDarkSteelLeggings, cost);
        if(player.isSprinting()) {
          moveInst.applyModifier(sprintModifiers[speedUpgrade.level - 1]);
        } else {
          moveInst.applyModifier(walkModifiers[speedUpgrade.level - 1]);
        }
      }
    }
  }

  private void updateStepHeightAndFallDistance(EntityPlayer player) {
    ItemStack boots = player.getEquipmentInSlot(1);

    if(boots != null && boots.getItem() == DarkSteelItems.itemDarkSteelBoots) {
      int costedDistance = (int) player.fallDistance;
      if(costedDistance > 0) {
        int energyCost = costedDistance * Configs.darkSteelFallDistanceCost;
        int totalEnergy = getPlayerEnergy(player, DarkSteelItems.itemDarkSteelBoots);
        if(totalEnergy > 0 && totalEnergy >= energyCost) {
          usePlayerEnergy(player, DarkSteelItems.itemDarkSteelBoots, energyCost);
          player.fallDistance -= costedDistance;
        }
      }
    }

    JumpUpgrade jumpUpgrade = JumpUpgrade.loadFromItem(boots);
    if(jumpUpgrade != null && boots != null && boots.getItem() == DarkSteelItems.itemDarkSteelBoots && isStepAssistActive(player)) {
      player.stepHeight = 1.0023F;
    } else if(player.stepHeight == 1.0023F) {
      player.stepHeight = 0.5001F;
    }
  }

  void usePlayerEnergy(EntityPlayer player, ItemDarkSteelArmor armor, int cost) {
    if(cost == 0) {
      return;
    }
    boolean extracted = false;
    int remaining = cost;
    if(Configs.darkSteelDrainPowerFromInventory) {
      for (ItemStack stack : player.inventory.mainInventory) {
        if(stack != null && stack.getItem() instanceof IEnergyContainerItem) {
          IEnergyContainerItem cont = (IEnergyContainerItem) stack.getItem();
          int used = cont.extractEnergy(stack, remaining, false);
          remaining -= used;
          extracted |= used > 0;
          if(remaining <= 0) {    
            return;
          }
        }
      }
    }
    if(armor != null && remaining > 0) {
      ItemStack stack = player.inventory.armorInventory[3 - armor.armorType];
      if(stack != null) {
        int used = armor.extractEnergy(stack, remaining, false);
        extracted |= used > 0;
      }
    }
  }

  private int getPlayerEnergy(EntityPlayer player, ItemDarkSteelArmor armor) {
    int res = 0;

    if(Configs.darkSteelDrainPowerFromInventory) {
      for (ItemStack stack : player.inventory.mainInventory) {
        if(stack != null && stack.getItem() instanceof IEnergyContainerItem) {
          IEnergyContainerItem cont = (IEnergyContainerItem) stack.getItem();
          res += cont.extractEnergy(stack, Integer.MAX_VALUE, true);
        }
      }
    }
    if(armor != null) {
      ItemStack stack = player.inventory.armorInventory[3 - armor.armorType];
      res = armor.getEnergyStored(stack);
    }
    return res;
  }

  @SideOnly(Side.CLIENT)
  @SubscribeEvent
  public void onClientTick(TickEvent.ClientTickEvent event) {
    if(event.phase == TickEvent.Phase.END) {
      EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
      if(player == null) {
        return;
      }
      MovementInput input = player.movementInput;
      if(input.jump && !wasJumping) {
        doJump(player);
      } else if(input.jump && jumpCount < 3 && ticksSinceLastJump > 5) {
        doJump(player);
      }

      wasJumping = !player.onGround;
      if(!wasJumping) {
        jumpCount = 0;
      }
      ticksSinceLastJump++;
            
      updateNightvision(player);
    }
    
    
  }

  @SideOnly(Side.CLIENT)
  private void doJump(EntityClientPlayerMP player) {

    ItemStack boots = player.getEquipmentInSlot(1);
    JumpUpgrade jumpUpgrade = JumpUpgrade.loadFromItem(boots);

    if(jumpUpgrade == null || boots == null || boots.getItem() != DarkSteelItems.itemDarkSteelBoots) {
      return;
    }

    int requiredPower = Configs.darkSteelBootsJumpPowerCost * (int) Math.pow(jumpCount + 1, 2.5);
    int availablePower = getPlayerEnergy(player, DarkSteelItems.itemDarkSteelBoots);
    if(availablePower > 0 && requiredPower <= availablePower && jumpCount < jumpUpgrade.level) {
      jumpCount++;
      player.motionY += 0.15 * Configs.darkSteelBootsJumpModifier * jumpCount;
      ticksSinceLastJump = 0;
      usePlayerEnergy(player, DarkSteelItems.itemDarkSteelBoots, requiredPower);
      player.worldObj.playSound(player.posX, player.posY, player.posZ, EnderIO.MODID + ":ds.jump", 1.0f, player.worldObj.rand.nextFloat() * 0.5f + 0.75f, false);
      PacketHandler.INSTANCE.sendToServer(new PacketDarkSteelPowerPacket(requiredPower, DarkSteelItems.itemDarkSteelBoots.armorType));
    }

  }
  
  
  private void updateNightvision(EntityPlayer player) {
    if(isNightVisionUpgradeEquipped(player) && nightVisionActive) {
      player.addPotionEffect(new PotionEffect(Potion.nightVision.getId(), 210, 0, true));
    } 
    if(!isNightVisionUpgradeEquipped(player) && nightVisionActive) {
      nightVisionActive = false;
      removeNightvision = true;
    }
    if(removeNightvision) {
      player.removePotionEffect(Potion.nightVision.getId());
      removeNightvision = false;
    }
  }

  public boolean isNightVisionUpgradeEquipped(EntityPlayer player) {
    ItemStack helmet = player.getEquipmentInSlot(4);    
    return NightVisionUpgrade.loadFromItem(helmet) != null;    
  }

  public void setNightVisionActive(boolean isNightVisionActive) {
    if(nightVisionActive && !isNightVisionActive) {
      removeNightvision = true;
    }
    this.nightVisionActive = isNightVisionActive;    
  }

}
