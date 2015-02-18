package crazypants.enderio.machine.transceiver.render;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import crazypants.enderio.EnderIO;
import crazypants.enderio.config.Configs;
import crazypants.enderio.machine.AbstractMachineBlock;
import crazypants.enderio.machine.IoMode;
import crazypants.enderio.machine.transceiver.TileTransceiver;
import crazypants.render.BoundingBox;
import crazypants.render.CubeRenderer;
import crazypants.render.CustomCubeRenderer;
import crazypants.render.CustomRenderBlocks;
import crazypants.render.IRenderFace;
import crazypants.render.IconUtil;
import crazypants.render.RenderUtil;
import crazypants.vecmath.Vertex;

public class TransceiverRenderer extends TileEntitySpecialRenderer implements IItemRenderer {

  private IModel model;

  private BoundingBox bb;

  private boolean adjustForItem = false;

  public TransceiverRenderer() {
    float scale = 0.7f;
    if(Configs.useAlternateTesseractModel) {
      model = new TransceiverModelAlt();
      scale = 0.8f;
      adjustForItem = true;
    } else {
      model = new TransceiverModel();
    }
    bb = BoundingBox.UNIT_CUBE.scale(scale, scale, scale);
  }

  @Override
  public void renderTileEntityAt(TileEntity te, double x, double y, double z, float tick) {

    TileTransceiver trans = (TileTransceiver) te;

    GL11.glEnable(GL12.GL_RESCALE_NORMAL);

    float f = trans.getWorldObj().getBlockLightValue(te.xCoord, te.yCoord, te.zCoord);
    int l = trans.getWorldObj().getLightBrightnessForSkyBlocks(te.xCoord, te.yCoord, te.zCoord, 0);
    int l1 = l % 65536;
    int l2 = l / 65536;
    Tessellator.instance.setColorOpaque_F(f, f, f);
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) l1, (float) l2);

    model.render(trans, x, y, z);
    if(trans.isActive()) {
      renderPower(te.getWorldObj(), x, y, z, true);
    }
    GL11.glDisable(GL12.GL_RESCALE_NORMAL);

    GL11.glPushMatrix();
    GL11.glTranslatef((float) x, (float) y, (float) z);
    GL11.glColor3f(1, 1, 1);
    GL11.glDisable(GL11.GL_LIGHTING);
    Tessellator.instance.startDrawingQuads();
    Tessellator.instance.setColorOpaque_F(f, f, f);
    

    RenderUtil.bindBlockTexture();
    CustomRenderBlocks rb = new CustomRenderBlocks(te.getWorldObj());
    double scale = 0.88;
    BoundingBox pushPullBounds = BoundingBox.UNIT_CUBE.scale(scale, scale, scale);
    BoundingBox disabledBounds = BoundingBox.UNIT_CUBE.scale(1.01, 1.01, 1.01);
    
    for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
      IoMode mode = trans.getIoMode(dir);
      if(mode != null) {
        if(mode == IoMode.DISABLED) {
          rb.setRenderBounds(disabledBounds.minX, disabledBounds.minY, disabledBounds.minZ, 
              disabledBounds.maxX, disabledBounds.maxY, disabledBounds.maxZ);
        } else {
          rb.setRenderBounds(pushPullBounds.minX, pushPullBounds.minY, pushPullBounds.minZ, 
              pushPullBounds.maxX, pushPullBounds.maxY, pushPullBounds.maxZ);      
        }
        IIcon icon = EnderIO.blockTransceiver.getOverlayIconForMode(mode);
        if(icon != null) {          
          rb.doDefaultRenderFace(dir, EnderIO.blockTransceiver, 0, 0, 0, icon);
        }
      }
    }

    Tessellator.instance.draw();
    GL11.glEnable(GL11.GL_LIGHTING);
    GL11.glPopMatrix();

  }

  @Override
  public boolean handleRenderType(ItemStack item, ItemRenderType type) {
    return true;
  }

  @Override
  public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
    return true;
  }

  @Override
  public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
    if(adjustForItem) {
      switch (type) {
      case ENTITY:
        renderItem(0f, 0f, 0f);
        return;
      case EQUIPPED:
      case EQUIPPED_FIRST_PERSON:
        renderItem(0f, 1f, 1f);
        return;
      case INVENTORY:
        renderItem(0f, 0f, 0f);
        return;
      default:
        renderItem(0f, 0f, 0f);
        return;
      }
    } else {
      renderItem(0, 0, 0);
    }
  }

  private void renderPower(World world, double x, double y, double z, boolean isActive) {

    GL11.glPushMatrix();
    GL11.glTranslatef((float) x, (float) y, (float) z);

    RenderUtil.bindBlockTexture();
    IIcon icon = EnderIO.blockHyperCube.getPortalIcon();

    Tessellator tessellator = Tessellator.instance;
    tessellator.startDrawingQuads();

    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    if(!isActive) {
      GL11.glColor4f(0, 1, 1, 0.5f);
    } else {
      GL11.glColor4f(1, 1, 1, 1f);
    }
    CubeRenderer.render(bb, icon);
    tessellator.draw();

    GL11.glPopMatrix();

    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_BLEND);
  }

  private void renderItem(float x, float y, float z) {
    GL11.glPushMatrix();
    GL11.glTranslatef(x, y, z);
    model.render();
    GL11.glPopMatrix();
  }

  //  private class OverlayRenderer implements IRenderFace {
  //
  //    @Override
  //    public void renderFace(CustomRenderBlocks rb, ForgeDirection face, Block par1Block, double x, double y, double z, IIcon texture, List<Vertex> refVertices,
  //        boolean translateToXyz) {
  //
  //      ccr.getCustomRenderBlocks().doDefaultRenderFace(face,par1Block,x,y,z,texture);
  //      if(curEnt != null && par1Block instanceof AbstractMachineBlock) {
  //        IoMode mode = curEnt.getIoMode(face);
  //        IIcon tex = ((AbstractMachineBlock)par1Block).getOverlayIconForMode(mode);
  //        if(tex != null) {
  //          ccr.getCustomRenderBlocks().doDefaultRenderFace(face,par1Block,x,y,z, tex);
  //        }
  //      }
  //
  //    }
  //
  //  }

}
