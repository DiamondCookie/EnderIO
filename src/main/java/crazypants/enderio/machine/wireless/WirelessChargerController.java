package crazypants.enderio.machine.wireless;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import crazypants.enderio.config.Configs;
import crazypants.util.BlockCoord;

public class WirelessChargerController {

  public static WirelessChargerController instance = new WirelessChargerController();

  public static final int RANGE = Configs.wirelessChargerRange;
  public static final int RANGE_SQ = RANGE * RANGE;

  static {
    FMLCommonHandler.instance().bus().register(WirelessChargerController.instance);
    MinecraftForge.EVENT_BUS.register(WirelessChargerController.instance);
  }

  private final Map<Integer, Map<BlockCoord, IWirelessCharger>> perWorldChargers = new HashMap<Integer, Map<BlockCoord, IWirelessCharger>>();
  private int changeCount;

  private WirelessChargerController() {
  }

  public void registerCharger(IWirelessCharger charger) {
    if(charger == null) {
      return;
    }
    Map<BlockCoord, IWirelessCharger> chargers = getChargersForWorld(charger.getWorld());
    chargers.put(charger.getLocation(), charger);
    changeCount++;
  }

  public void deregisterCharger(IWirelessCharger capBank) {
    if(capBank == null) {
      return;
    }
    Map<BlockCoord, IWirelessCharger> chargers = getChargersForWorld(capBank.getWorld());
    chargers.remove(capBank.getLocation());
    changeCount++;
  }

  @SubscribeEvent
  public void onPlayerTick(TickEvent.PlayerTickEvent event) {
    if(event.side == Side.CLIENT || event.phase != TickEvent.Phase.END) {
      return;
    }
    chargePlayersItems(event.player);
  }

  public int getChangeCount() {
    return changeCount;
  }

  public void getChargers(World world, BlockCoord bc, Collection<IWirelessCharger> res) {
    Map<BlockCoord, IWirelessCharger> chargers = getChargersForWorld(world);
    for (IWirelessCharger wc : chargers.values()) {
      if(wc.getLocation().distanceSquared(bc) <= RANGE_SQ) {
        res.add(wc);
      }
    }
  }

  public void chargePlayersItems(EntityPlayer player) {
    Map<BlockCoord, IWirelessCharger> chargers = getChargersForWorld(player.worldObj);
    if(chargers.isEmpty()) {
      return;
    }
    BlockCoord bc = new BlockCoord(player);
    for (IWirelessCharger capBank : chargers.values()) {
      if(capBank.isActive() && capBank.getLocation().distanceSquared(bc) <= RANGE_SQ) {
        boolean done = chargeFromCapBank(player, capBank);
        if(done) {
          return;
        }
      }
    }
  }

  private boolean chargeFromCapBank(EntityPlayer player, IWirelessCharger capBank) {
    boolean res = capBank.chargeItems(player.inventory.armorInventory);
    res |= capBank.chargeItems(player.inventory.mainInventory);    
    return res;
  }

  private Map<BlockCoord, IWirelessCharger> getChargersForWorld(World world) {
    Map<BlockCoord, IWirelessCharger> res = perWorldChargers.get(world.provider.dimensionId);
    if(res == null) {
      res = new HashMap<BlockCoord, IWirelessCharger>();
      perWorldChargers.put(world.provider.dimensionId, res);
    }
    return res;
  }

  public Collection<IWirelessCharger> getChargers(World world) {
    return getChargerMap(world).values();
  }

  public Map<BlockCoord, IWirelessCharger> getChargerMap(World world) {
    return perWorldChargers.get(world.provider.dimensionId);
  }
}
