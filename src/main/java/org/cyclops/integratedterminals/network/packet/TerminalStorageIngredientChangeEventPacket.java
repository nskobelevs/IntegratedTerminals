package org.cyclops.integratedterminals.network.packet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.ingredient.collection.IIngredientCollection;
import org.cyclops.cyclopscore.ingredient.collection.IngredientArrayList;
import org.cyclops.cyclopscore.ingredient.collection.IngredientCollections;
import org.cyclops.cyclopscore.network.CodecField;
import org.cyclops.cyclopscore.network.PacketCodec;
import org.cyclops.integrateddynamics.api.ingredient.IIngredientComponentStorageObservable;
import org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorage;
import org.cyclops.integratedterminals.inventory.container.TerminalStorageTabIngredientComponentClient;

/**
 * Packet for sending a storage change event from server to client.
 * @author rubensworks
 *
 */
public class TerminalStorageIngredientChangeEventPacket extends PacketCodec {

    @CodecField
    private NBTTagCompound changeData;
	@CodecField
	private int channel;

    public TerminalStorageIngredientChangeEventPacket() {

    }

    public TerminalStorageIngredientChangeEventPacket(IIngredientComponentStorageObservable.StorageChangeEvent<?, ?> event) {
		IIngredientComponentStorageObservable.Change changeType = event.getChangeType();
		IIngredientCollection<?, ?> instances = event.getInstances();
		NBTTagCompound serialized = IngredientCollections.serialize(instances);
		serialized.setInteger("changeType", changeType.ordinal());
		this.changeData = serialized;
		this.channel = event.getChannel();
    }

	@Override
	public boolean isAsync() {
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void actionClient(World world, EntityPlayer player) {
		if(player.openContainer instanceof ContainerTerminalStorage) {
			ContainerTerminalStorage container = ((ContainerTerminalStorage) player.openContainer);
			IIngredientComponentStorageObservable.Change changeType = IIngredientComponentStorageObservable.Change.values()[changeData.getInteger("changeType")];
			IngredientArrayList ingredients = IngredientCollections.deserialize(changeData);
			TerminalStorageTabIngredientComponentClient<?, ?> tab = (TerminalStorageTabIngredientComponentClient<?, ?>) container.getTabClient(ingredients.getComponent());
			tab.onChange(channel, changeType, ingredients);
			container.refreshChannelStrings();
		}
	}

	@Override
	public void actionServer(World world, EntityPlayerMP player) {

	}
	
}