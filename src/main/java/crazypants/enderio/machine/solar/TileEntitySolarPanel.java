package crazypants.enderio.machine.solar;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import cofh.api.energy.EnergyStorage;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import crazypants.enderio.TileEntityEio;
import crazypants.enderio.config.Configs;
import crazypants.enderio.power.IInternalPowerProvider;
import crazypants.enderio.power.IPowerInterface;
import crazypants.enderio.power.PowerHandlerUtil;
import crazypants.enderio.waila.IWailaNBTProvider;
import crazypants.util.BlockCoord;

public class TileEntitySolarPanel extends TileEntityEio implements IInternalPowerProvider, IWailaNBTProvider {
  
  private final List<Receptor> receptors = new ArrayList<Receptor>();
  private ListIterator<Receptor> receptorIterator = receptors.listIterator();
  private boolean receptorsDirty = true;

  private int lastCollectionValue = -1;
  
  private int checkOffset;
  private static final int CHECK_INTERVAL = 100;
  
  EnergyStorage destroyedNetworkBuffer = null;
  
  protected SolarPanelNetwork network = new SolarPanelNetwork();

  public TileEntitySolarPanel() {
    checkOffset = (int) (Math.random() * 20);
  }
  
  public void onNeighborBlockChange() {
    receptorsDirty = true;
  }

  @Override
  public BlockCoord getLocation() {
    return new BlockCoord(this);
  }

  // RF Power

  @Override
  public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
    return 0;
  }

  @Override
  public boolean canConnectEnergy(ForgeDirection from) {
    return from == ForgeDirection.DOWN;
  }

  @Override
  public int getEnergyStored(ForgeDirection from) {
    return getEnergyStored();
  }

  @Override
  public int getMaxEnergyStored(ForgeDirection from) {
    return getMaxEnergyStored();
  }
  
  @Override
  public int getMaxEnergyRecieved(ForgeDirection dir) {
    return 0;
  }

  @Override
  public int getEnergyStored() {
    return network.getEnergyStored();
  }

  @Override
  public int getMaxEnergyStored() {
    return network.getMaxEnergyStored();
  }

  @Override
  public void setEnergyStored(int stored) {
    network.setEnergyStored(stored);
  }

  @Override
  public void updateEntity() {
    if(worldObj == null || worldObj.isRemote) {
      return;
    }
    collectEnergy();
    transmitEnergy();
    
    if (network.isValid()) {
      if (destroyedNetworkBuffer != null) {
        network.addBuffer(destroyedNetworkBuffer);
        destroyedNetworkBuffer = null;
      }
      network.onUpdate(this);
    }
    
    if (!network.isValid() || (worldObj.getTotalWorldTime() % 20 == 0 && network.addToNetwork(this))) {
      findNetwork();
    }
  }
  
  @Override
  public void invalidate() {
    network.removeFromNetwork(this);
    super.invalidate();
  }

  private void findNetwork() {
    for (ForgeDirection dir : SolarPanelNetwork.VALID_CONS) {
      TileEntity te = new BlockCoord(this).getLocation(dir).getTileEntity(worldObj);
      if(te != null && te instanceof TileEntitySolarPanel && ((TileEntitySolarPanel) te).canConnect(this)) {
        SolarPanelNetwork network = ((TileEntitySolarPanel) te).network;
        if(network != null) {
          network.addToNetwork(this);
        }
      }
    }

    if(!network.isValid()) {
      network = new SolarPanelNetwork(this);
    }
  }

  private boolean canConnect(TileEntitySolarPanel other) {
    return getBlockMetadata() == other.getBlockMetadata();
  }

  private void collectEnergy() {
    if(!worldObj.canBlockSeeTheSky(xCoord, yCoord, zCoord)) {
      return;
    }

    if(lastCollectionValue == -1 || (worldObj.getWorldTime() + checkOffset) % CHECK_INTERVAL == 0) {
      float fromSun = calculateLightRatio();
      lastCollectionValue = Math.round(getEnergyPerTick() * fromSun);
    }
    if(lastCollectionValue > 0) {
      network.setEnergyStored(Math.min(lastCollectionValue + network.getEnergyStored(), network.getMaxEnergyStored()));
    }
  }

  private int getEnergyPerTick() {
    int meta = getBlockMetadata();
    if(meta == 0) {
      return Configs.maxPhotovoltaicOutputRF;
    }
    return Configs.maxPhotovoltaicAdvancedOutputRF;
  }

  float calculateLightRatio() {
    return calculateLightRatio(worldObj, xCoord, yCoord, zCoord);
  }

  public static float calculateLightRatio(World world, int x, int y, int z) {
    int lightValue = world.getSavedLightValue(EnumSkyBlock.Sky, x, y, z) - world.skylightSubtracted;
    float sunAngle = world.getCelestialAngleRadians(1.0F);

    if(sunAngle < (float) Math.PI) {
      sunAngle += (0.0F - sunAngle) * 0.2F;
    } else {
      sunAngle += (((float) Math.PI * 2F) - sunAngle) * 0.2F;
    }

    lightValue = Math.round(lightValue * MathHelper.cos(sunAngle));

    lightValue = MathHelper.clamp_int(lightValue, 0, 15);
    return lightValue / 15f;
  }

  private boolean transmitEnergy() {


    int canTransmit = Math.min(getEnergyStored(), network.getMaxEnergyExtracted());
    int transmitted = 0;

    checkReceptors();

    if(!receptors.isEmpty() && !receptorIterator.hasNext()) {
      receptorIterator = receptors.listIterator();
    }

    int appliedCount = 0;
    int numReceptors = receptors.size();
    while (receptorIterator.hasNext() && canTransmit > 0 && appliedCount < numReceptors) {

      Receptor receptor = receptorIterator.next();
      IPowerInterface pp = receptor.receptor;
      if(pp != null && pp.getMinEnergyReceived(receptor.fromDir.getOpposite()) <= canTransmit) {
        int used = pp.recieveEnergy(receptor.fromDir.getOpposite(), canTransmit);
        transmitted += used;
        canTransmit -= used;
      }

      if(canTransmit <= 0) {
        break;
      }

      if(!receptors.isEmpty() && !receptorIterator.hasNext()) {
        receptorIterator = receptors.listIterator();
      }
      appliedCount++;
    }

    setEnergyStored(getEnergyStored() - transmitted);    

    return transmitted > 0;

  }

  private void checkReceptors() {
    if(!receptorsDirty) {
      return;
    }
    receptors.clear();
    BlockCoord bc = new BlockCoord(xCoord, yCoord, zCoord);
    ForgeDirection dir = ForgeDirection.DOWN;
    BlockCoord checkLoc = bc.getLocation(dir);
    TileEntity te = worldObj.getTileEntity(checkLoc.x, checkLoc.y, checkLoc.z);
    IPowerInterface pi = PowerHandlerUtil.create(te);
    if(pi != null) {
      receptors.add(new Receptor(pi, dir));
    }
    receptorIterator = receptors.listIterator();
    receptorsDirty = false;

  }

  static class Receptor {
    IPowerInterface receptor;
    ForgeDirection fromDir;

    private Receptor(IPowerInterface rec, ForgeDirection fromDir) {
      receptor = rec;
      this.fromDir = fromDir;
    }
  }

  @Override
  public void readCustomNBT(NBTTagCompound tag) {
    network.readFromNBT(this, tag);
  }

  @Override
  public void writeCustomNBT(NBTTagCompound tag) {
    if (network.isValid() && network.shouldSave(this)) {
      network.writeToNBT(tag);
      tag.setInteger("rfCap", network.getMaxEnergyStored()); // for WAILA
    }
  }

  @Override
  public Packet getDescriptionPacket() {
    NBTTagCompound nbt = new NBTTagCompound();
    writeToNBT(nbt);
    return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
  }

  @Override
  public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
    readFromNBT(pkt.func_148857_g());
  }
  
  @Override
  public boolean displayPower() {
    return true;
  }

  public void setNetwork(SolarPanelNetwork network) {
    this.network = network;
  }
  
  public boolean isMaster() {
    return network.getMaster() == this;
  }

  @Override
  public void getData(NBTTagCompound tag) {
    if (network.isValid()) {
      network.getMaster().writeToNBT(tag);
    }
  }
}
