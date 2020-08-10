package org.cyclops.integratedterminals.client.gui.container;

import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import org.cyclops.cyclopscore.client.gui.component.button.ButtonText;
import org.cyclops.cyclopscore.client.gui.container.ContainerScreenExtended;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integratedterminals.Reference;
import org.cyclops.integratedterminals.api.terminalstorage.crafting.ITerminalCraftingPlan;
import org.cyclops.integratedterminals.client.gui.container.component.GuiCraftingPlan;
import org.cyclops.integratedterminals.core.client.gui.CraftingOptionGuiData;
import org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorageCraftingPlan;
import org.cyclops.integratedterminals.network.packet.TerminalStorageIngredientOpenPacket;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * A gui for previewing a crafting plan.
 * @author rubensworks
 */
public class ContainerScreenTerminalStorageCraftingPlan extends ContainerScreenExtended<ContainerTerminalStorageCraftingPlan> {

    @Nullable
    private GuiCraftingPlan guiCraftingPlan;

    private ITerminalCraftingPlan craftingPlan;

    public ContainerScreenTerminalStorageCraftingPlan(ContainerTerminalStorageCraftingPlan container, PlayerInventory inventory, ITextComponent title) {
        super(container, inventory, title);
    }

    @Override
    protected ResourceLocation constructGuiTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/gui/crafting_plan.png");
    }

    @Override
    public int getBaseXSize() {
        return 256;
    }

    @Override
    public int getBaseYSize() {
        return 222;
    }

    @Override
    public void init() {
        super.init();

        if (this.craftingPlan != null) {
            this.children.clear();
            this.guiCraftingPlan = new GuiCraftingPlan(this, this.craftingPlan, guiLeft, guiTop, 9, 18, 10);
            this.children.add(this.guiCraftingPlan);
        } else {
            this.guiCraftingPlan = null;
        }

        ButtonText button;
        this.buttons.clear();
        addButton(button = new ButtonText(guiLeft + 95, guiTop + 198, 50, 20,
                        L10NHelpers.localize("gui.integratedterminals.terminal_storage.step.craft"),
                        TextFormatting.BOLD + L10NHelpers.localize("gui.integratedterminals.terminal_storage.step.craft"),
                        createServerPressable(ContainerTerminalStorageCraftingPlan.BUTTON_START, (b) -> {}),
                        true));
        button.active = this.guiCraftingPlan != null && this.guiCraftingPlan.isValid();
    }

    @Override
    public boolean keyPressed(int typedChar, int keyCode, int modifiers) {
        if (this.guiCraftingPlan != null && this.guiCraftingPlan.isValid()
                && (typedChar == GLFW.GLFW_KEY_ENTER || typedChar == GLFW.GLFW_KEY_KP_ENTER)) {
            ((Button) this.buttons.get(0)).onPress();
            return true;
        }
        return super.keyPressed(typedChar, keyCode, modifiers);
    }

    private void returnToTerminalStorage() {
        CraftingOptionGuiData craftingOptionGuiData = getContainer().getCraftingOptionGuiData();
        TerminalStorageIngredientOpenPacket.send(craftingOptionGuiData.getPos(), craftingOptionGuiData.getSide(),
                craftingOptionGuiData.getTabName(), craftingOptionGuiData.getChannel());
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        super.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
        if (this.guiCraftingPlan != null) {
            guiCraftingPlan.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
        } else {
            drawCenteredString(font, L10NHelpers.localize("gui.integratedterminals.terminal_storage.step.crafting_plan_calculating"),
                    guiLeft + getBaseXSize() / 2, guiTop + 23, 16777215);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
        if (this.guiCraftingPlan != null) {
            guiCraftingPlan.drawGuiContainerForegroundLayer(mouseX, mouseY);
        }
    }

    @Override
    protected void drawCurrentScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawCurrentScreen(mouseX, mouseY, partialTicks);
        if (this.guiCraftingPlan != null) {
            guiCraftingPlan.render(mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public void onUpdate(int valueId, CompoundNBT value) {
        super.onUpdate(valueId, value);

        if (getContainer().getCraftingPlanNotifierId() == valueId) {
            this.craftingPlan = getContainer().getCraftingOptionGuiData().getCraftingOption().getHandler().deserializeCraftingPlan(value);
            this.init();
        }
    }
}