package crazypants.enderio.machine.crafter;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.opengl.GL11;

import crazypants.enderio.config.Configs;
import crazypants.enderio.gui.IconEIO;
import crazypants.enderio.gui.ToggleButtonEIO;
import crazypants.enderio.machine.IItemBuffer;
import crazypants.enderio.machine.PacketItemBuffer;
import crazypants.enderio.machine.gui.GuiPoweredMachineBase;
import crazypants.enderio.machine.power.PowerDisplayUtil;
import crazypants.enderio.network.PacketHandler;
import crazypants.render.RenderUtil;
import crazypants.util.Lang;

public class GuiCrafter extends GuiPoweredMachineBase<TileCrafter>  {

  private ToggleButtonEIO bufferSizeB;

  public GuiCrafter(InventoryPlayer par1InventoryPlayer, TileCrafter te) {
    super(te, new ContainerCrafter(par1InventoryPlayer, te));
    xSize = getXSize();

    int x = getXSize() - 5 - 16;
    int y = 43;
    bufferSizeB = new ToggleButtonEIO(this, 4327, x, y, IconEIO.ITEM_SINGLE, IconEIO.ITEM_STACK);
    bufferSizeB.setSelectedToolTip(Lang.localize("gui.machine.bufferingstacks"));
    bufferSizeB.setUnselectedToolTip(Lang.localize("gui.machine.bufferingsingle"));
    bufferSizeB.setSelected(te.isBufferStacks());
  }

  @Override
  public void initGui() {
    super.initGui();
    bufferSizeB.onGuiInit();
  }

  @Override
  protected void actionPerformed(GuiButton b) {
    super.actionPerformed(b);
    if(b == bufferSizeB) {
      getTileEntity().setBufferStacks(bufferSizeB.isSelected());
      PacketHandler.INSTANCE.sendToServer(new PacketItemBuffer(getTileEntity()));
    }
  }

  @Override
  protected boolean showRecipeButton() {
    return false;
  }

  @Override
  public int getXSize() {
    return 219;
  }
  
  @Override
  protected int getPowerU() {
    return 220;
  }

  @Override
  protected int getPowerX() {    
    return 9;
  }  
  
  @Override
  protected void updatePowerBarTooltip(List<String> text) {
    text.add(PowerDisplayUtil.formatPower(Configs.crafterRfPerCraft) + " " + PowerDisplayUtil.abrevation()
        + " " + Lang.localize("gui.machine.percraft"));
    super.updatePowerBarTooltip(text);
  }

  @Override
  protected void drawGuiContainerBackgroundLayer(float par1, int par2, int par3) {
    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    RenderUtil.bindTexture("enderio:textures/gui/crafter.png");
    int sx = (width - xSize) / 2;
    int sy = (height - ySize) / 2;

    drawTexturedModalRect(sx, sy, 0, 0, xSize, ySize);    

    super.drawGuiContainerBackgroundLayer(par1, par2, par3);
  }
  
}
