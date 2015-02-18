package crazypants.enderio.machine.transceiver.gui;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

import crazypants.enderio.config.Configs;
import crazypants.enderio.gui.ITabPanel;
import crazypants.enderio.gui.IconEIO;
import crazypants.enderio.gui.ToggleButtonEIO;
import crazypants.enderio.machine.PacketItemBuffer;
import crazypants.enderio.machine.power.PowerDisplayUtil;
import crazypants.enderio.network.PacketHandler;
import crazypants.gui.GuiToolTip;
import crazypants.render.ColorUtil;
import crazypants.render.RenderUtil;
import crazypants.util.Lang;

public class GeneralTab implements ITabPanel {

  private static final int SEND_BAR_OFFSET = 13;
  ContainerTransceiver container;
  GuiTransceiver parent;
  GuiToolTip sendPowerBarTT;
  
  ToggleButtonEIO bufferSizeB;

  public GeneralTab(GuiTransceiver guiTransceiver) {
    parent = guiTransceiver;    
    container = parent.getContainer();

    int x = parent.getXSize() - 5 - 16;
    int y = 43;
    bufferSizeB = new ToggleButtonEIO(parent, 4327, x, y, IconEIO.ITEM_SINGLE, IconEIO.ITEM_STACK);
    bufferSizeB.setSelectedToolTip(Lang.localize("gui.machine.bufferingstacks"));
    bufferSizeB.setUnselectedToolTip(Lang.localize("gui.machine.bufferingsingle"));
    bufferSizeB.setSelected(parent.getTransciever().isBufferStacks());
    
    sendPowerBarTT = new GuiToolTip(new Rectangle(parent.getPowerX() + SEND_BAR_OFFSET, parent.getPowerY(), parent.getPowerWidth(), parent.getPowerHeight()), "") {
      @Override
      protected void updateText() {        
        text.clear();
        if(parent.renderPowerBar()) {
          updateSendPowerBarTooltip(text);
        }
      }      
    };    
    parent.addToolTip(sendPowerBarTT);
  }

  @Override
  public void onGuiInit(int x, int y, int width, int height) {
    container.setPlayerInventoryVisible(true);
    container.setBufferSlotsVisible(true);
    bufferSizeB.onGuiInit();
  }

  @Override
  public void deactivate() {   
    container.setPlayerInventoryVisible(false);
    container.setBufferSlotsVisible(false);
    bufferSizeB.detach();
  }

  @Override
  public IconEIO getIcon() {
    return IconEIO.IO_CONFIG_UP;
  }

  @Override
  public void render(float par1, int par2, int par3) {
    int top = parent.getGuiTop();
    int left = parent.getGuiLeft();
    
    GL11.glColor3f(1, 1, 1);
    
    //Inventory
    RenderUtil.bindTexture("enderio:textures/gui/transceiver.png");
    Point invRoot = container.getPlayerInventoryOffset();
    parent.drawTexturedModalRect(left + invRoot.x - 1, top + invRoot.y - 1, 24, 180, 162, 76);  
    
    invRoot = container.getItemInventoryOffset();
    parent.drawTexturedModalRect(left + invRoot.x - 1, top + invRoot.y - 1, 24, 180, 72, 36);
    parent.drawTexturedModalRect(left + invRoot.x - 1 + (18 * 4) + container.getItemBufferSpacing(), top + invRoot.y - 1, 24, 180, 72, 36);
            
    FontRenderer fr = parent.getFontRenderer();
    String sendTxt = Lang.localize("gui.send");    
    int x = left + invRoot.x + 36 - fr.getStringWidth(sendTxt)/2;
    int y = top + invRoot.y - fr.FONT_HEIGHT - 3;
    fr.drawStringWithShadow(sendTxt, x, y, ColorUtil.getRGB(Color.WHITE));
    String recText = Lang.localize("gui.receive");
    x = left + invRoot.x + 72 + container.getItemBufferSpacing() + 36 - fr.getStringWidth(recText)/2;
    fr.drawStringWithShadow(recText, x, y, ColorUtil.getRGB(Color.WHITE));
    
    //Highlights
    parent.renderSlotHighlights();    
    
    //Power    
    RenderUtil.bindTexture("enderio:textures/gui/transceiver.png");
    GL11.glColor3f(1, 1, 1);
    
    x = left + parent.getPowerX() - 1;
    y = top + parent.getPowerY() - 1;
    int maxHeight = parent.getPowerHeight();
    
    parent.drawTexturedModalRect(x, y, 233, 196, 12, maxHeight + 2);
    parent.drawTexturedModalRect(x + SEND_BAR_OFFSET, y, 233, 196, 12, maxHeight + 2);
    
    int totalPixelHeight = parent.getTransciever().getEnergyStoredScaled(maxHeight * 2);
    int fillHeight = Math.min(totalPixelHeight,maxHeight);
    
    int fillY = y + 1 + parent.getPowerHeight() - fillHeight;
    x += 1;
    parent.drawTexturedModalRect(x, fillY, parent.getPowerU(), parent.getPowerV(), parent.getPowerWidth(), fillHeight);
    
    fillHeight = Math.max(0, totalPixelHeight - maxHeight);
    fillY = y + 1 + parent.getPowerHeight() - fillHeight;
    parent.drawTexturedModalRect(x + SEND_BAR_OFFSET, fillY, parent.getPowerU() - 25, parent.getPowerV(), parent.getPowerWidth(), fillHeight);
    
  }

  public void updatePowerBarTooltip(List<String> text) {
    text.add(Lang.localize("gui.machine.localbuffer"));
    text.add(Lang.localize("gui.machine.upkeep") + " " + PowerDisplayUtil.formatPowerPerTick(parent.getPowerOutputValue()));    
    int maxEnergy = parent.getTransciever().getCapacitor().getMaxEnergyStored()/2;
    int energyStored = Math.min(parent.getTransciever().getEnergyStored(), maxEnergy);       
    text.add(PowerDisplayUtil.formatStoredPower(energyStored, maxEnergy));    
  }
  
  private void updateSendPowerBarTooltip(List<String> text) {
    text.add(Lang.localize("gui.machine.sendReceivebuffer"));
    text.add(Lang.localize("itemGasConduit.tooltip.maxIo") + " " + PowerDisplayUtil.formatPowerPerTick(Configs.transceiverMaxIoRF));
    int maxEnergy = parent.getTransciever().getCapacitor().getMaxEnergyStored()/2;
    int energyStored = Math.max(0, parent.getTransciever().getEnergyStored() - maxEnergy);
    text.add(PowerDisplayUtil.formatStoredPower(energyStored, maxEnergy));    
  }
  
  
  @Override
  public void actionPerformed(GuiButton guiButton) {
    if(guiButton == bufferSizeB) {
      parent.getTransciever().setBufferStacks(bufferSizeB.isSelected());
      PacketHandler.INSTANCE.sendToServer(new PacketItemBuffer(parent.getTransciever()));
    }
  }

  @Override
  public void mouseClicked(int x, int y, int par3) {    
  }
  
  @Override
  public void updateScreen() {   
  }

  @Override
  public void keyTyped(char par1, int par2) {    
  }



}
