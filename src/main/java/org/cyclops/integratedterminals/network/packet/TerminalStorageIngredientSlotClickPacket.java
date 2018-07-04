package org.cyclops.integratedterminals.network.packet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.commoncapabilities.api.ingredient.IIngredientSerializer;
import org.cyclops.commoncapabilities.api.ingredient.IngredientComponent;
import org.cyclops.cyclopscore.network.CodecField;
import org.cyclops.cyclopscore.network.PacketCodec;
import org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorage;
import org.cyclops.integratedterminals.inventory.container.TerminalClickType;
import org.cyclops.integratedterminals.inventory.container.TerminalStorageTabIngredientComponentServer;

/**
 * Packet for sending a storage slot click event from client to server.
 * @author rubensworks
 *
 */
public class TerminalStorageIngredientSlotClickPacket<T> extends PacketCodec {

    @CodecField
    private String ingredientName;
    @CodecField
    private int clickType;
    @CodecField
    private int channel;
    @CodecField
    private NBTTagCompound hoveringStorageInstanceData;
    @CodecField
    private int hoveredPlayerSlot;
    @CodecField
    private NBTTagCompound activeStorageInstanceData;

    public TerminalStorageIngredientSlotClickPacket() {

    }

    public TerminalStorageIngredientSlotClickPacket(IngredientComponent<T, ?> component, TerminalClickType clickType,
                                                    int channel, T hoveringStorageInstance,
                                                    int hoveredPlayerSlot, T activeStorageInstance) {
        this.clickType = clickType.ordinal();
        this.ingredientName = component.getName().toString();
        this.channel = channel;
        this.hoveringStorageInstanceData = new NBTTagCompound();
        IIngredientSerializer<T, ?> serializer = getComponent().getSerializer();
        this.hoveringStorageInstanceData.setTag("i", serializer.serializeInstance(hoveringStorageInstance));
        this.hoveredPlayerSlot = hoveredPlayerSlot;
        this.activeStorageInstanceData = new NBTTagCompound();
        this.activeStorageInstanceData.setTag("i", serializer.serializeInstance(activeStorageInstance));
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void actionClient(World world, EntityPlayer player) {

    }

    @Override
    public void actionServer(World world, EntityPlayerMP player) {
        if(player.openContainer instanceof ContainerTerminalStorage) {
            ContainerTerminalStorage container = ((ContainerTerminalStorage) player.openContainer);
            TerminalStorageTabIngredientComponentServer<T, ?> tab = (TerminalStorageTabIngredientComponentServer<T, ?>)
                    container.getTabServer(getComponent());
            IIngredientSerializer<T, ?> serializer = getComponent().getSerializer();
            T hoveringStorageInstance = serializer.deserializeInstance(this.hoveringStorageInstanceData.getTag("i"));
            T activeInstance = serializer.deserializeInstance(this.activeStorageInstanceData.getTag("i"));
            tab.handleStorageSlotClick(player, getClickType(), getChannel(), hoveringStorageInstance, hoveredPlayerSlot, activeInstance);
        }
    }

    public TerminalClickType getClickType() {
        return TerminalClickType.values()[this.clickType];
    }

    public IngredientComponent<T, ?> getComponent() {
        IngredientComponent<T, ?> ingredientComponent = (IngredientComponent<T, ?>) IngredientComponent.REGISTRY.getValue(new ResourceLocation(this.ingredientName));
        if (ingredientComponent == null) {
            throw new IllegalArgumentException("No ingredient component with the given name was found: " + ingredientName);
        }
        return ingredientComponent;
    }

    public int getChannel() {
        return channel;
    }

}