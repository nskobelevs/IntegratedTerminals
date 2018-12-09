package org.cyclops.integratedterminals.core.terminalstorage;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import gnu.trove.map.TIntLongMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.commoncapabilities.api.ingredient.IIngredientMatcher;
import org.cyclops.commoncapabilities.api.ingredient.IngredientComponent;
import org.cyclops.cyclopscore.client.gui.image.Images;
import org.cyclops.cyclopscore.helper.GuiHelpers;
import org.cyclops.cyclopscore.helper.Helpers;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.cyclopscore.helper.RenderHelpers;
import org.cyclops.cyclopscore.helper.StringHelpers;
import org.cyclops.cyclopscore.ingredient.collection.IIngredientListMutable;
import org.cyclops.cyclopscore.ingredient.collection.IngredientArrayList;
import org.cyclops.cyclopscore.ingredient.collection.diff.IngredientCollectionDiff;
import org.cyclops.cyclopscore.ingredient.collection.diff.IngredientCollectionDiffHelpers;
import org.cyclops.integrateddynamics.api.ingredient.IIngredientComponentStorageObservable;
import org.cyclops.integrateddynamics.api.network.IPositionedAddonsNetwork;
import org.cyclops.integrateddynamics.api.part.PartPos;
import org.cyclops.integratedterminals.IntegratedTerminals;
import org.cyclops.integratedterminals.api.ingredient.IIngredientComponentTerminalStorageHandler;
import org.cyclops.integratedterminals.api.ingredient.IIngredientInstanceSorter;
import org.cyclops.integratedterminals.api.terminalstorage.ITerminalButton;
import org.cyclops.integratedterminals.api.terminalstorage.ITerminalStorageTabClient;
import org.cyclops.integratedterminals.api.terminalstorage.ITerminalStorageTabCommon;
import org.cyclops.integratedterminals.api.terminalstorage.TerminalClickType;
import org.cyclops.integratedterminals.api.terminalstorage.crafting.ITerminalCraftingOption;
import org.cyclops.integratedterminals.api.terminalstorage.event.TerminalStorageTabClientLoadButtonsEvent;
import org.cyclops.integratedterminals.api.terminalstorage.event.TerminalStorageTabClientSearchFieldUpdateEvent;
import org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerConfig;
import org.cyclops.integratedterminals.client.gui.container.GuiTerminalStorage;
import org.cyclops.integratedterminals.core.client.gui.CraftingOptionGuiData;
import org.cyclops.integratedterminals.core.client.gui.ExtendedGuiHandler;
import org.cyclops.integratedterminals.core.terminalstorage.button.TerminalButtonSort;
import org.cyclops.integratedterminals.core.terminalstorage.crafting.HandlerWrappedTerminalCraftingOption;
import org.cyclops.integratedterminals.core.terminalstorage.query.IIngredientQuery;
import org.cyclops.integratedterminals.core.terminalstorage.slot.TerminalStorageSlotIngredient;
import org.cyclops.integratedterminals.core.terminalstorage.slot.TerminalStorageSlotIngredientCraftingOption;
import org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorage;
import org.cyclops.integratedterminals.network.packet.TerminalStorageIngredientOpenCraftingJobAmountGuiPacket;
import org.cyclops.integratedterminals.network.packet.TerminalStorageIngredientOpenCraftingPlanGuiPacket;
import org.cyclops.integratedterminals.network.packet.TerminalStorageIngredientSlotClickPacket;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A client-side storage terminal ingredient tab.
 * @param <T> The instance type.
 * @param <M> The matching condition parameter.
 * @author rubensworks
 */
public class TerminalStorageTabIngredientComponentClient<T, M>
        implements ITerminalStorageTabClient<TerminalStorageSlotIngredient<T, M>> {

    static {
        MinecraftForge.EVENT_BUS.register(TerminalStorageTabIngredientComponentClient.class);
    }

    private final ResourceLocation name;
    protected final IngredientComponent<T, M> ingredientComponent;
    private final IIngredientComponentTerminalStorageHandler<T, M> ingredientComponentViewHandler;
    private final ItemStack icon;
    protected final ContainerTerminalStorage container;
    private final List<ITerminalButton<?, ?, ?>> buttons;

    private final TIntObjectMap<List<InstanceWithMetadata<T>>> ingredientsViews;
    private final TIntObjectMap<List<InstanceWithMetadata<T>>> filteredIngredientsViews;
    private final Int2ObjectMap<Collection<HandlerWrappedTerminalCraftingOption<T>>> craftingOptions;

    private final TIntLongMap maxQuantities;
    private final TIntLongMap totalQuantities;
    private boolean enabled;
    private int activeSlotId;
    private int activeSlotQuantity;
    private int activeChannel;

    @SubscribeEvent
    public static void onToolTip(ItemTooltipEvent event) {
        // If this tab is active, render the quantity in all player inventory item tooltips.
        if (event.getEntityPlayer() != null && event.getEntityPlayer().openContainer instanceof ContainerTerminalStorage) {
            ContainerTerminalStorage container = (ContainerTerminalStorage) event.getEntityPlayer().openContainer;
            ITerminalStorageTabClient<?> tab = container.getTabsClient().get(container.getSelectedTab());
            if (tab instanceof TerminalStorageTabIngredientComponentClient) {
                IIngredientComponentTerminalStorageHandler handler = ((TerminalStorageTabIngredientComponentClient) tab).ingredientComponentViewHandler;
                Object instance = handler.getInstance(event.getItemStack());
                if (!(instance instanceof ItemStack)) {
                    handler.addQuantityTooltip(event.getToolTip(), instance);
                }
            }
        }
    }

    public TerminalStorageTabIngredientComponentClient(ContainerTerminalStorage container, ResourceLocation name,
                                                       IngredientComponent<?, ?> ingredientComponent) {
        this.name = name;
        this.ingredientComponent = (IngredientComponent<T, M>) ingredientComponent;
        this.ingredientComponentViewHandler = Objects.requireNonNull(this.ingredientComponent.getCapability(IngredientComponentTerminalStorageHandlerConfig.CAPABILITY));
        this.icon = ingredientComponentViewHandler.getIcon();
        this.container = container;

        List<ITerminalButton<?, ?, ?>> buttons = Lists.newArrayList();
        loadButtons(buttons);
        TerminalStorageTabClientLoadButtonsEvent event = new TerminalStorageTabClientLoadButtonsEvent(container, this, buttons);
        MinecraftForge.EVENT_BUS.post(event);
        this.buttons = event.getButtons();

        this.ingredientsViews = new TIntObjectHashMap<>();
        this.filteredIngredientsViews = new TIntObjectHashMap<>();
        this.craftingOptions = new Int2ObjectOpenHashMap<>();

        this.maxQuantities = new TIntLongHashMap();
        this.totalQuantities = new TIntLongHashMap();
        this.enabled = false;
        resetActiveSlot();

    }

    protected void loadButtons(List<ITerminalButton<?, ?, ?>> buttons) {
        // Add all sorting buttons
        for (IIngredientInstanceSorter<T> instanceSorter : ingredientComponentViewHandler.getInstanceSorters()) {
            buttons.add(new TerminalButtonSort<>(instanceSorter, container.getGuiState(), this));
        }
    }

    public IngredientComponent<T, M> getIngredientComponent() {
        return ingredientComponent;
    }

    @Override
    public ResourceLocation getName() {
        return this.name;
    }

    @Override
    public ItemStack getIcon() {
        return this.icon;
    }

    @Override
    public List<String> getTooltip() {
        return Lists.newArrayList(L10NHelpers.localize("gui.integratedterminals.terminal_storage.storage_name",
                L10NHelpers.localize(this.ingredientComponent.getUnlocalizedName())));
    }

    @Override
    public String getInstanceFilter(int channel) {
        if (container.getGuiState().hasSearch(getName().toString(), channel)) {
            return container.getGuiState().getSearch(getName().toString(), channel);
        }
        return "";
    }

    public void resetFilteredIngredientsViews(int channel) {
        filteredIngredientsViews.remove(channel);
    }

    @Override
    public void setInstanceFilter(int channel, String filter) {
        TerminalStorageTabClientSearchFieldUpdateEvent event = new TerminalStorageTabClientSearchFieldUpdateEvent(this, filter);
        MinecraftForge.EVENT_BUS.post(event);
        filter = event.getSearchString();
        resetFilteredIngredientsViews(channel);
        container.getGuiState().setSearch(getName().toString(), channel, filter.toLowerCase(Locale.ENGLISH));
    }

    public List<InstanceWithMetadata<T>> getRawUnfilteredIngredientsView(int channel) {
        List<InstanceWithMetadata<T>> ingredientsView = ingredientsViews.get(channel);
        if (ingredientsView == null) {
            ingredientsView = Lists.newArrayList();
            ingredientsViews.put(channel, ingredientsView);
        }
        return ingredientsView;
    }

    @Nullable
    public Collection<HandlerWrappedTerminalCraftingOption<T>> getCraftingOptions(int channel) {
        return craftingOptions.get(channel);
    }

    public List<InstanceWithMetadata<T>> getUnfilteredIngredientsView(int channel) {
        Collection<HandlerWrappedTerminalCraftingOption<T>> craftingOptions = getCraftingOptions(channel);
        if (craftingOptions == null) {
            return getRawUnfilteredIngredientsView(channel);
        } else {
            List<InstanceWithMetadata<T>> enrichedIngredients = Lists.newArrayList();
            enrichedIngredients.addAll(getRawUnfilteredIngredientsView(channel));
            // Add all crafting option outputs
            for (HandlerWrappedTerminalCraftingOption<T> craftingOption : craftingOptions) {
                for (T output : getUniqueCraftingOptionOutputs(craftingOption.getCraftingOption())) {
                    enrichedIngredients.add(new InstanceWithMetadata<>(output, craftingOption));
                }
            }
            return enrichedIngredients;
        }
    }

    protected Collection<T> getUniqueCraftingOptionOutputs(ITerminalCraftingOption<T> craftingOption) {
        TreeSet<T> uniqueOutputs = Sets.newTreeSet(ingredientComponent.getMatcher());
        Iterator<T> it = craftingOption.getOutputs();
        while (it.hasNext()) {
            uniqueOutputs.add(it.next());
        }
        return uniqueOutputs;
    }

    protected List<InstanceWithMetadata<T>> getFilteredIngredientsView(int channel) {
        List<InstanceWithMetadata<T>> ingredientsView = filteredIngredientsViews.get(channel);
        if (ingredientsView == null) {
            ingredientsView = getUnfilteredIngredientsView(channel);

            // Filter
            ingredientsView = Lists.newArrayList(
                    this.transformIngredientsView(ingredientsView.stream())
                            .filter(im -> IIngredientQuery.parse(ingredientComponent, getInstanceFilter(channel)).test(im.getInstance()))
                            .collect(Collectors.toList()));

            // Sort
            Comparator<T> sorter = getInstanceSorter();
            if (sorter != null) {
                ingredientsView.sort((o1, o2) -> sorter.compare(o1.getInstance(), o2.getInstance()));
            }

            filteredIngredientsViews.put(channel, ingredientsView);
        }
        return ingredientsView;
    }

    protected Stream<InstanceWithMetadata<T>> transformIngredientsView(Stream<InstanceWithMetadata<T>> ingredientStream) {
        return ingredientStream;
    }

    @Override
    public List<TerminalStorageSlotIngredient<T, M>> getSlots(int channel, int offset, int limit) {
        List<InstanceWithMetadata<T>> ingredients = getFilteredIngredientsView(channel);
        int size = ingredients.size();
        if (offset >= size) {
            return Lists.newArrayList();
        }
        return ingredients.subList(offset, Math.min(offset + limit, size)).stream()
                .map(instanceWithMetadata -> {
                    T instance = instanceWithMetadata.getInstance();
                    HandlerWrappedTerminalCraftingOption<T> craftingOption = instanceWithMetadata.getCraftingOption();
                    if (craftingOption == null) {
                        return new TerminalStorageSlotIngredient<>(ingredientComponentViewHandler, instance);
                    } else {
                        return new TerminalStorageSlotIngredientCraftingOption<>(ingredientComponentViewHandler, instance, craftingOption);
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    public Optional<T> getSlotInstance(int channel, int index) {
        return getSlot(channel, index).map(TerminalStorageSlotIngredient::getInstance);
    }

    public Optional<TerminalStorageSlotIngredient<T, M>> getSlot(int channel, int index) {
        if (index >= 0) {
            List<TerminalStorageSlotIngredient<T, M>> lastSlots = getSlots(channel, index, 1);
            if (!lastSlots.isEmpty()) {
                return Optional.of(lastSlots.get(0));
            }
        }
        return Optional.empty();
    }

    @Override
    public int getSlotCount(int channel) {
        return getFilteredIngredientsView(channel).size();
    }

    @Override
    public String getStatus(int channel) {
        return String.format("%,d / %,d", getTotalQuantity(channel), getMaxQuantity(channel));
    }

    /**
     * Receiver an ingredients change event.
     * @param channel A channel id.
     * @param changeType A change type.
     * @param ingredients A list of changed ingredients.
     * @param enabled If this tab is enabled.
     */
    public synchronized void onChange(int channel, IIngredientComponentStorageObservable.Change changeType,
                                      IngredientArrayList<T, M> ingredients, boolean enabled) {
        this.enabled = enabled || this.craftingOptions.containsKey(channel);

        // Remember the selected instance, as this change event might change its position or quantity.
        // This is handled at the end of this method.
        Optional<T> lastInstance = getSlotInstance(channel, this.activeSlotId);

        // Apply the change to the wildcard channel as well
        if (channel != IPositionedAddonsNetwork.WILDCARD_CHANNEL) {
            onChange(IPositionedAddonsNetwork.WILDCARD_CHANNEL, changeType, ingredients, enabled);
        }

        // Calculate quantity-diff
        long quantity = 0;
        IIngredientMatcher<T, M> matcher = ingredients.getComponent().getMatcher();
        for (T ingredient : ingredients) {
            quantity += matcher.getQuantity(ingredient);
        }
        if (changeType != IIngredientComponentStorageObservable.Change.ADDITION) {
            quantity = -quantity;
        }
        long newQuantity = totalQuantities.get(channel) + quantity;
        totalQuantities.put(channel, newQuantity);

        // Apply diff
        List<InstanceWithMetadata<T>> rawPersistedIngredients = getRawUnfilteredIngredientsView(channel);
        IIngredientListMutable<T, M> persistedIngredients = new IngredientArrayList<>(ingredientComponent,
                rawPersistedIngredients
                .stream()
                .map(InstanceWithMetadata::getInstance)
                .collect(Collectors.toList()));
        IngredientCollectionDiff<T, M> diff = new IngredientCollectionDiff<>(
                changeType == IIngredientComponentStorageObservable.Change.ADDITION ? ingredients : null,
                changeType == IIngredientComponentStorageObservable.Change.DELETION ? ingredients : null,
                false);
        IngredientCollectionDiffHelpers.applyDiff(ingredientComponent, diff, persistedIngredients);
        rawPersistedIngredients.clear();
        for (T persistedIngredient : persistedIngredients) {
            rawPersistedIngredients.add(new InstanceWithMetadata<>(persistedIngredient, null));
        }

        // Persist changes
        resetFilteredIngredientsViews(channel);

        // Update the active instance by searching for its new position in the slots
        // If this becomes a performance bottleneck, we could search _around_ the previous position.
        updateActiveInstance(lastInstance, channel);
    }

    protected void updateActiveInstance(Optional<T> lastInstance, int channel) {
        if (lastInstance.isPresent() && this.activeChannel == channel) {
            this.activeSlotId = findActiveSlotId(channel, lastInstance.get());
            Optional<T> slotIngredient = getSlotInstance(channel, this.activeSlotId);
            this.activeSlotQuantity = slotIngredient
                    .map(t -> Math.min(this.activeSlotQuantity, Helpers.castSafe(this.ingredientComponent.getMatcher().getQuantity(t))))
                    .orElse(0);
        }
    }

    public synchronized void addCraftingOptions(int channel, List<HandlerWrappedTerminalCraftingOption<T>> craftingOptions) {
        // Remember the selected instance, as this change event might change its position or quantity.
        // This is handled at the end of this method.
        Optional<T> lastInstance = getSlotInstance(channel, this.activeSlotId);

        // Apply the change to the wildcard channel as well
        if (channel != IPositionedAddonsNetwork.WILDCARD_CHANNEL) {
            addCraftingOptions(IPositionedAddonsNetwork.WILDCARD_CHANNEL, craftingOptions);
        }

        this.enabled = true;
        Collection<HandlerWrappedTerminalCraftingOption<T>> existingOptions = this.craftingOptions.get(channel);
        if (existingOptions == null) {
            this.craftingOptions.put(channel, craftingOptions);
        } else {
            existingOptions.addAll(craftingOptions);
        }

        // Persist changes
        resetFilteredIngredientsViews(channel);

        // Update the active instance by searching for its new position in the slots
        // If this becomes a performance bottleneck, we could search _around_ the previous position.
        updateActiveInstance(lastInstance, channel);
    }

    protected int findActiveSlotId(int channel, T instance) {
        IIngredientMatcher<T, M> matcher = this.ingredientComponent.getMatcher();
        int newActiveSlot = 0;
        M matchCondition = matcher.getExactMatchNoQuantityCondition();
        List<TerminalStorageSlotIngredient<T, M>> slots = getSlots(channel, 0, Integer.MAX_VALUE);
        for (TerminalStorageSlotIngredient<T, M> slot : slots) {
            T ingredient = slot.getInstance();
            if (matcher.matches(ingredient, instance, matchCondition)) {
                return newActiveSlot;
            }
            newActiveSlot++;
        }
        return -1;
    }

    /**
     * Called by the server when a (remainder) active storage ingredient needs to be set.
     * @param channel The channel.
     * @param activeInstance The active instance.
     */
    public synchronized void handleActiveIngredientUpdate(int channel, T activeInstance) {
        IIngredientMatcher<T, M> matcher = this.ingredientComponent.getMatcher();
        if (!matcher.isEmpty(activeInstance)) {
            this.activeChannel = channel;
            this.activeSlotId = findActiveSlotId(channel, activeInstance);
            Optional<T> slotIngredient = getSlotInstance(channel, this.activeSlotId);
            this.activeSlotQuantity += slotIngredient
                    .map(t -> Helpers.castSafe(matcher.getQuantity(activeInstance)))
                    .orElse(0);
        }
    }

    /**
     * Get the total maximum allowed quantity in the given channel.
     * @param channel A channel id.
     * @return The max quantity.
     */
    public long getMaxQuantity(int channel) {
        // Take the sum of all channels when requesting wildcard channel
        if (channel == IPositionedAddonsNetwork.WILDCARD_CHANNEL) {
            return Arrays.stream(getChannels()).mapToLong(this::getMaxQuantity).sum();
        }
        return maxQuantities.get(channel);
    }

    /**
     * Set the max quantity in the given channel.
     * @param channel A channel id.
     * @param maxQuantity The new max quantity.
     */
    public void setMaxQuantity(int channel, long maxQuantity) {
        this.maxQuantities.put(channel, maxQuantity);
    }

    /**
     * Get the total instance quantities in the given channel.
     * @param channel A channel id.
     * @return The total quantity.
     */
    public long getTotalQuantity(int channel) {
        return totalQuantities.get(channel);
    }

    @Override
    public int[] getChannels() {
        int[] channels = maxQuantities.keys();
        Arrays.sort(channels);
        return channels;
    }

    @Override
    public void resetActiveSlot() {
        this.activeSlotId = -1;
        this.activeSlotQuantity = 0;
        this.activeChannel = -2;
    }

    @Override
    public boolean handleClick(Container container, int channel, int hoveringStorageSlot, int mouseButton,
                               boolean hasClickedOutside, boolean hasClickedInStorage, int hoveredContainerSlot) {
        this.activeChannel = channel;

        IIngredientMatcher<T, M> matcher = ingredientComponent.getMatcher();
        Optional<TerminalStorageSlotIngredient<T, M>> hoveringStorageSlotObject = getSlot(channel, hoveringStorageSlot);
        Optional<T> hoveringStorageInstance = hoveringStorageSlotObject.map(TerminalStorageSlotIngredient::getInstance);
        boolean validHoveringStorageSlot = hoveringStorageInstance.isPresent();
        boolean isCraftingOption = hoveringStorageSlotObject.isPresent() && hoveringStorageSlotObject.get() instanceof TerminalStorageSlotIngredientCraftingOption;
        IIngredientComponentTerminalStorageHandler<T, M> viewHandler = ingredientComponent.getCapability(IngredientComponentTerminalStorageHandlerConfig.CAPABILITY);
        boolean shift = (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));

        EntityPlayer player = Minecraft.getMinecraft().player;
        boolean initiateCraftingOption = false;
        if (mouseButton == 0 || mouseButton == 1 || mouseButton == 2) {
            TerminalClickType clickType = null;
            long moveQuantity = this.activeSlotQuantity;
            long movePlayerQuantity = 0;
            boolean reset = false; // So that a reset occurs after the packet is sent
            if (validHoveringStorageSlot && player.inventory.getItemStack().isEmpty() && activeSlotId < 0) {
                if (shift) {
                    // Quick move max quantity from storage to player
                    clickType = TerminalClickType.STORAGE_QUICK_MOVE;
                } else {
                    if (isCraftingOption) {
                        // Craft
                        initiateCraftingOption = true;
                    } else {
                        // Pick up
                        this.activeSlotId = hoveringStorageSlot;
                        this.activeSlotQuantity = Math.min((int) ingredientComponent.getMatcher()
                                        .getQuantity(hoveringStorageInstance.orElse(matcher.getEmptyInstance())),
                                viewHandler.getInitialInstanceMovementQuantity());
                        if (mouseButton == 1) {
                            this.activeSlotQuantity = (int) Math.ceil((double) this.activeSlotQuantity / 2);
                        } else if (mouseButton == 2) {
                            this.activeSlotQuantity = 1;
                        }
                    }
                }
            } else if (hoveredContainerSlot >= 0 && !container.getSlot(hoveredContainerSlot).getStack().isEmpty() && shift) {
                // Quick move max quantity from player to storage
                clickType = TerminalClickType.PLAYER_QUICK_MOVE;
            } else if (hasClickedInStorage && !player.inventory.getItemStack().isEmpty()) {
                // Move into storage
                clickType = TerminalClickType.PLAYER_PLACE_STORAGE;
                if (mouseButton == 0) {
                    movePlayerQuantity = viewHandler.getActivePlayerStackQuantity(player.inventory);
                } else if (mouseButton == 1) {
                    movePlayerQuantity = viewHandler.getIncrementalInstanceMovementQuantity();
                } else {
                    movePlayerQuantity = (int) Math.ceil((double) viewHandler.getActivePlayerStackQuantity(player.inventory) / 2);
                }
                viewHandler.drainActivePlayerStackQuantity(player.inventory, movePlayerQuantity);
                resetActiveSlot();
            } else if (activeSlotId >= 0) {
                // We have a storage slot selected
                if (hasClickedOutside) {
                    // Throw
                    clickType = TerminalClickType.STORAGE_PLACE_WORLD;
                    reset = true;
                } else if (hoveredContainerSlot >= 0) {
                    // Insert into player inventory
                    clickType = TerminalClickType.STORAGE_PLACE_PLAYER;
                    if (mouseButton == 0) {
                        reset = true;
                        moveQuantity = this.activeSlotQuantity;
                    } else if (mouseButton == 1) {
                        moveQuantity = viewHandler.getIncrementalInstanceMovementQuantity();
                    } else {
                        moveQuantity = (int) Math.ceil((double) this.activeSlotQuantity / 2);
                    }
                    this.activeSlotQuantity -= moveQuantity;
                } else if (hasClickedInStorage) {
                    if (mouseButton == 0 && this.activeSlotId == hoveringStorageSlot) {
                        // Increase the active quantity
                        this.activeSlotQuantity = (int) Math.min(ingredientComponent.getMatcher().getQuantity(hoveringStorageInstance.get()),
                                this.activeSlotQuantity + (shift ? viewHandler.getInitialInstanceMovementQuantity()
                                        : viewHandler.getIncrementalInstanceMovementQuantity()));
                    } else if (mouseButton == 1) {
                        // Decrease active quantity
                        this.activeSlotQuantity = Math.max(0, this.activeSlotQuantity - (shift ? viewHandler.getInitialInstanceMovementQuantity()
                                : viewHandler.getIncrementalInstanceMovementQuantity()));
                        if (this.activeSlotQuantity == 0) {
                            activeSlotId = -1;
                        }
                    } else {
                        // Deselect slot
                        resetActiveSlot();
                    }
                } else {
                    // Deselect slot
                    resetActiveSlot();
                }

                if (moveQuantity == 0) {
                    activeSlotId = -1;
                }
            }
            if (initiateCraftingOption) {
                ContainerTerminalStorage containerTerminalStorage = ((ContainerTerminalStorage) container);
                PartPos pos = containerTerminalStorage.getTarget().getCenter();
                CraftingOptionGuiData<T, M> craftingOptionData = new CraftingOptionGuiData<>(pos.getPos().getBlockPos(), pos.getSide(),
                        ingredientComponent, this.getName().toString(), channel, ((TerminalStorageSlotIngredientCraftingOption<T, M>) hoveringStorageSlotObject.get()).getCraftingOption(), 1);
                IntegratedTerminals._instance.getGuiHandler().setTemporaryData(ExtendedGuiHandler.CRAFTING_OPTION,
                        Pair.of(((ContainerTerminalStorage) container).getTarget().getCenter().getSide(), craftingOptionData)); // Pass the side as extra data to the gui
                if (shift) {
                    IntegratedTerminals._instance.getPacketHandler().sendToServer(
                            new TerminalStorageIngredientOpenCraftingPlanGuiPacket<>(craftingOptionData));
                } else {
                    IntegratedTerminals._instance.getPacketHandler().sendToServer(
                            new TerminalStorageIngredientOpenCraftingJobAmountGuiPacket<>(craftingOptionData));
                }
            } else if (clickType != null) {
                T activeInstance = matcher.getEmptyInstance();
                if (activeSlotId >= 0) {
                    activeInstance = matcher.withQuantity(getSlots(channel, activeSlotId, 1).get(0).getInstance(), moveQuantity);
                }
                IntegratedTerminals._instance.getPacketHandler().sendToServer(new TerminalStorageIngredientSlotClickPacket<>(
                        this.getName().toString(), ingredientComponent, clickType, channel,
                        hoveringStorageInstance.orElse(matcher.getEmptyInstance()),
                        hoveredContainerSlot, movePlayerQuantity, activeInstance));
                if (reset) {
                    resetActiveSlot();
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public int getActiveSlotId() {
        return this.activeSlotId;
    }

    @Override
    public int getActiveSlotQuantity() {
        return this.activeSlotQuantity;
    }

    @Override
    public List<ITerminalButton<?, ?, ?>> getButtons() {
        return this.buttons;
    }

    @Nullable
    public Comparator<T> getInstanceSorter() {
        Comparator<T> sorter = null;

        // Chain all effective sorters from buttons of type TerminalButtonSort
        for (ITerminalButton<?, ?, ?> button : this.buttons) {
            if (button instanceof TerminalButtonSort) {
                Comparator<T> partSorter = ((TerminalButtonSort<T>) button).getEffectiveSorter();
                if (partSorter != null) {
                    if (sorter == null) {
                        sorter = partSorter;
                    } else {
                        sorter = sorter.thenComparing(partSorter);
                    }
                }
            }
        }

        if (sorter != null) {
            // Make comparators 0-equals-safe
            sorter = sorter.thenComparing(ingredientComponent.getMatcher());
        }

        return sorter;
    }

    @Override
    public void onCommonSlotRender(GuiContainer gui, GuiTerminalStorage.DrawLayer layer, float partialTick,
                                   int x, int y, int mouseX, int mouseY, int slot, ITerminalStorageTabCommon tabCommon) {
        TerminalStorageTabIngredientComponentCommon tab = (TerminalStorageTabIngredientComponentCommon) tabCommon;

        if (slot >= tab.getVariableSlotNumberStart() && slot < tab.getVariableSlotNumberEnd()) {
            List<L10NHelpers.UnlocalizedString> errors = Lists.newArrayList();
            errors.addAll(tab.getGlobalErrors());
            errors.addAll(tab.getLocalErrors(slot));

            if (!errors.isEmpty()) {
                if (layer == GuiTerminalStorage.DrawLayer.BACKGROUND) {
                    Images.ERROR.draw(gui, x + 2, y + 2);
                } else {
                    if (RenderHelpers.isPointInRegion(x, y, GuiHelpers.SLOT_SIZE, GuiHelpers.SLOT_SIZE, mouseX, mouseY)) {
                        GuiHelpers.drawTooltip(gui, errors.stream()
                                .map(L10NHelpers.UnlocalizedString::localize)
                                .map(s -> StringHelpers.splitLines(s, L10NHelpers.MAX_TOOLTIP_LINE_LENGTH,
                                        TextFormatting.RED.toString()))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()), x - gui.getGuiLeft() + 10, y - gui.getGuiTop());
                    }
                }
            }
        }
    }

    public static class InstanceWithMetadata<T> {

        private final T instance;
        @Nullable
        private final HandlerWrappedTerminalCraftingOption<T> craftingOption;

        public InstanceWithMetadata(T instance, @Nullable HandlerWrappedTerminalCraftingOption<T> craftingOption) {
            this.instance = instance;
            this.craftingOption = craftingOption;
        }

        public T getInstance() {
            return instance;
        }

        @Nullable
        public HandlerWrappedTerminalCraftingOption<T> getCraftingOption() {
            return craftingOption;
        }
    }

}
