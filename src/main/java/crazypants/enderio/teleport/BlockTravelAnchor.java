package crazypants.enderio.teleport;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.BlockEio;
import crazypants.enderio.EnderIO;
import crazypants.enderio.GuiHandler;
import crazypants.enderio.ModObject;
import crazypants.enderio.TileEntityEio;
import crazypants.enderio.config.Configs;
import crazypants.enderio.gui.IResourceTooltipProvider;
import crazypants.enderio.machine.MachineRecipeInput;
import crazypants.enderio.machine.MachineRecipeRegistry;
import crazypants.enderio.machine.painter.BasicPainterTemplate;
import crazypants.enderio.machine.painter.PainterUtil;
import crazypants.enderio.network.PacketHandler;
import crazypants.enderio.teleport.packet.PacketAccessMode;
import crazypants.enderio.teleport.packet.PacketConfigSync;
import crazypants.enderio.teleport.packet.PacketDrainStaff;
import crazypants.enderio.teleport.packet.PacketLabel;
import crazypants.enderio.teleport.packet.PacketOpenAuthGui;
import crazypants.enderio.teleport.packet.PacketTravelEvent;
import crazypants.util.IFacade;
import crazypants.util.Lang;
import net.minecraftforge.common.UsernameCache;

public class BlockTravelAnchor extends BlockEio implements IGuiHandler, ITileEntityProvider, IResourceTooltipProvider, IFacade {

  public static int renderId = -1;

  public static BlockTravelAnchor create() {

    PacketHandler.INSTANCE.registerMessage(PacketAccessMode.class, PacketAccessMode.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketLabel.class, PacketLabel.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketTravelEvent.class, PacketTravelEvent.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketDrainStaff.class, PacketDrainStaff.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketOpenAuthGui.class, PacketOpenAuthGui.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketConfigSync.class, PacketConfigSync.class, PacketHandler.nextID(), Side.CLIENT);

    ConnectionHandler ch = new ConnectionHandler();
    FMLCommonHandler.instance().bus().register(ch);

    BlockTravelAnchor result = new BlockTravelAnchor();
    result.init();
    return result;
  }

  IIcon selectedOverlayIcon;
  IIcon highlightOverlayIcon;

  private BlockTravelAnchor() {
    super(ModObject.blockTravelAnchor.unlocalisedName, TileTravelAnchor.class);
    if(!Configs.travelAnchorEnabled) {
      setCreativeTab(null);
    }
  }

  @Override
  protected void init() {
    super.init();
    EnderIO.guiHandler.registerGuiHandler(GuiHandler.GUI_ID_TRAVEL_ACCESSABLE, this);
    EnderIO.guiHandler.registerGuiHandler(GuiHandler.GUI_ID_TRAVEL_AUTH, this);
    MachineRecipeRegistry.instance.registerRecipe(ModObject.blockPainter.unlocalisedName, new PainterTemplate());
  }

  @Override
  public void registerBlockIcons(IIconRegister iIconRegister) {
    super.registerBlockIcons(iIconRegister);
    highlightOverlayIcon = iIconRegister.registerIcon("enderio:blockTravelAnchorHighlight");
    selectedOverlayIcon = iIconRegister.registerIcon("enderio:blockTravelAnchorSelected");
  }

  @Override
  public IIcon getIcon(IBlockAccess world, int x, int y, int z, int blockSide) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof TileTravelAnchor) {
      TileTravelAnchor tef = (TileTravelAnchor) te;
      if(tef.getSourceBlock() != null) {
        return tef.getSourceBlock().getIcon(blockSide, tef.getSourceBlockMetadata());
      }
    }
    return super.getIcon(world, x, y, z, blockSide);
  }

  @Override
  public TileEntity createNewTileEntity(World var1, int var2) {
    return new TileTravelAnchor();
  }

  @Override
  public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack par6ItemStack) {
    if(entity instanceof EntityPlayer) {
      TileEntity te = world.getTileEntity(x, y, z);
      if(te instanceof TileTravelAnchor) {
        TileTravelAnchor ta = (TileTravelAnchor) te;
        ta.setPlacedBy((EntityPlayer) entity);
        Block b = PainterUtil.getSourceBlock(par6ItemStack);
        ta.setSourceBlock(b);
        ta.setSourceBlockMetadata(PainterUtil.getSourceBlockMetadata(par6ItemStack));
        world.markBlockForUpdate(x, y, z);
      }
    }
  }
  
  @Override
  public boolean openGui(World world, int x, int y, int z, EntityPlayer entityPlayer, int side) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof ITravelAccessable) {
      ITravelAccessable ta = (ITravelAccessable) te;
      if(ta.canUiBeAccessed(entityPlayer)) {
        entityPlayer.openGui(EnderIO.instance, GuiHandler.GUI_ID_TRAVEL_ACCESSABLE, world, x, y, z);
      } else {
        if(world.isRemote && !entityPlayer.isSneaking()) {
          entityPlayer.addChatComponentMessage(new ChatComponentText(Lang.localize("gui.travelAccessable.privateBlock1") + " " + UsernameCache.getLastKnownUsername(ta.getPlacedBy()) + " "
              + Lang.localize("gui.travelAccessable.privateBlock2")));
        }
      }
    }
    return true;
  }

  @Override
  public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof ITravelAccessable) {
      if(ID == GuiHandler.GUI_ID_TRAVEL_ACCESSABLE) {
        return new ContainerTravelAccessable(player.inventory, (ITravelAccessable) te, world);
      } else {
        return new ContainerTravelAuth(player.inventory);
      }
    }
    return null;
  }

  @Override
  public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof ITravelAccessable) {
      if(ID == GuiHandler.GUI_ID_TRAVEL_ACCESSABLE) {
        return new GuiTravelAccessable(player.inventory, (ITravelAccessable) te, world);
      } else {
        return new GuiTravelAuth(player, (ITravelAccessable) te, world);
      }
    }
    return null;
  }

  @Override
  protected void processDrop(World world, int x, int y, int z, TileEntityEio te, ItemStack drop) {
    TileTravelAnchor anchor = (TileTravelAnchor) te;

    if(anchor == null) {
      return;
    }

    ItemStack itemStack = new ItemStack(this);
    Block srcBlk = anchor.getSourceBlock();
    if(srcBlk != null) {
      itemStack = createItemStackForSourceBlock(anchor.getSourceBlock(), anchor.getSourceBlockMetadata());
      drop.stackTagCompound = (NBTTagCompound) itemStack.stackTagCompound.copy();
    }
  }

  @Override
  public boolean doNormalDrops(World world, int x, int y, int z) {
    return false;
  }

  @Override
  @SideOnly(Side.CLIENT)
  public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof TileTravelAnchor) {
      TileTravelAnchor tef = (TileTravelAnchor) te;
      if(tef.getSourceBlock() != null) {
        return tef.getSourceBlock().colorMultiplier(world, x, y, z);
      }
    }
    return super.colorMultiplier(world, x, y, z);
  }

  @Override
  public int getRenderType() {
    return renderId;
  }

  @Override
  public String getUnlocalizedNameForTooltip(ItemStack itemStack) {
    return getUnlocalizedName();
  }

  public ItemStack createItemStackForSourceBlock(Block block, int damage) {
    if(block == this) {
      return new ItemStack(this);
    }
    ItemStack result = new ItemStack(this, 1, damage);
    PainterUtil.setSourceBlock(result, block, damage);
    return result;
  }

  @Override
  public boolean isOpaqueCube() {
    return false;
  }

  public final class PainterTemplate extends BasicPainterTemplate {

    public PainterTemplate() {
      super(BlockTravelAnchor.this);
    }

    @Override
    public ResultStack[] getCompletedResult(float chance, MachineRecipeInput... inputs) {
      ItemStack paintSource = MachineRecipeInput.getInputForSlot(1, inputs);
      if(paintSource == null) {
        return new ResultStack[0];
      }
      return new ResultStack[] { new ResultStack(createItemStackForSourceBlock(Block.getBlockFromItem(paintSource.getItem()), paintSource.getItemDamage())) };
    }
  }

  @Override
  public int getFacadeMetadata(IBlockAccess world, int x, int y, int z, int side) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof TileTravelAnchor) {
      return ((TileTravelAnchor) te).getSourceBlockMetadata();
    }
    return 0;
  }

  @Override
  public Block getFacade(IBlockAccess world, int x, int y, int z, int side) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof TileTravelAnchor) {
      return ((TileTravelAnchor) te).getSourceBlock();
    }
    return this;
  }
}
