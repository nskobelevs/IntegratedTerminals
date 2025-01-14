package org.cyclops.integratedterminals.inventory.container;

import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.cyclopscore.client.gui.ScreenFactorySafe;
import org.cyclops.cyclopscore.config.extendedconfig.GuiConfig;
import org.cyclops.cyclopscore.inventory.container.ContainerTypeData;
import org.cyclops.integratedterminals.IntegratedTerminals;
import org.cyclops.integratedterminals.client.gui.container.ContainerScreenTerminalStorageCraftingPlan;

/**
 * Config for {@link ContainerTerminalStorageCraftingPlanItem}.
 * @author rubensworks
 */
public class ContainerTerminalStorageCraftingPlanItemConfig extends GuiConfig<ContainerTerminalStorageCraftingPlanItem> {

    public ContainerTerminalStorageCraftingPlanItemConfig() {
        super(IntegratedTerminals._instance,
                "part_terminal_storage_crafting_plan_item",
                eConfig -> new ContainerTypeData<>(ContainerTerminalStorageCraftingPlanItem::new));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public <U extends Screen & MenuAccess<ContainerTerminalStorageCraftingPlanItem>> MenuScreens.ScreenConstructor<ContainerTerminalStorageCraftingPlanItem, U> getScreenFactory() {
        // Does not compile when simplified with lambdas
        return new ScreenFactorySafe<>(new MenuScreens.ScreenConstructor<ContainerTerminalStorageCraftingPlanItem, ContainerScreenTerminalStorageCraftingPlan<Pair<InteractionHand, Integer>, ContainerTerminalStorageCraftingPlanItem>>() {
            @Override
            public ContainerScreenTerminalStorageCraftingPlan<Pair<InteractionHand, Integer>, ContainerTerminalStorageCraftingPlanItem> create(ContainerTerminalStorageCraftingPlanItem p_create_1_, Inventory p_create_2_, Component p_create_3_) {
                return new ContainerScreenTerminalStorageCraftingPlan<>(p_create_1_, p_create_2_, p_create_3_);
            }
        });
    }

}
