package dev.wuason.storagemechanic.storages;

import dev.wuason.mechanics.Mechanics;
import dev.wuason.mechanics.utils.AdventureUtils;
import dev.wuason.storagemechanic.Managers;
import dev.wuason.storagemechanic.StorageMechanic;
import dev.wuason.storagemechanic.actions.Action;
import dev.wuason.storagemechanic.actions.events.ClickStorageItemInterfaceAction;
import dev.wuason.storagemechanic.actions.events.EventEnum;
import dev.wuason.storagemechanic.api.events.storage.ClickPageStorageEvent;
import dev.wuason.storagemechanic.data.DataManager;
import dev.wuason.storagemechanic.data.SaveCause;
import dev.wuason.storagemechanic.items.ItemInterface;
import dev.wuason.storagemechanic.items.ItemInterfaceManager;
import dev.wuason.storagemechanic.items.properties.ActionItemProperties;
import dev.wuason.storagemechanic.items.properties.CleanItemProperties;
import dev.wuason.storagemechanic.items.properties.PlaceHolderItemProperties;
import dev.wuason.storagemechanic.items.properties.SearchItemProperties;
import dev.wuason.storagemechanic.storages.config.StorageConfig;
import dev.wuason.storagemechanic.storages.config.StorageSoundConfig;
import dev.wuason.storagemechanic.storages.inventory.StorageInventory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class StorageManager implements Listener {
    private StorageMechanic core;
    private Map<String, Storage> storageMap = new HashMap<>();
    private Map<UUID, WaitingInputData> waitingInput = new ConcurrentHashMap<>();
    private DataManager dataManager;
    private Managers managers;

    public StorageManager(StorageMechanic core, DataManager dataManager, Managers managers) {
        this.core = core;
        this.dataManager = dataManager;
        this.managers = managers;
    }

    public Storage createStorage(String storageIdConfig, StorageOriginContext storageOriginContext) {
        if(core.getManagers().getStorageConfigManager().existsStorageConfig(storageIdConfig)){
            Storage storage = new Storage(storageIdConfig,storageOriginContext);
            storageMap.put(storage.getId(), storage);
            return storage;
        }
        return null;
    }
    public Storage createStorage(String storageIdConfig, UUID id, StorageOriginContext storageOriginContext) {
        if(core.getManagers().getStorageConfigManager().existsStorageConfig(storageIdConfig)){
            Storage storage = new Storage(storageIdConfig,id,storageOriginContext);
            storageMap.put(storage.getId(), storage);
            return storage;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event){
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof StorageInventory) {
            StorageInventory storageInventory = (StorageInventory) holder;
            Storage storage = storageInventory.getStorage();
            StorageConfig storageConfig = core.getManagers().getStorageConfigManager().getStorageConfigById(storage.getStorageIdConfig());
            DragItemCheck(storage, storageInventory, event, storageConfig);
            if(storageConfig.isStorageBlockItemEnabled()){
                DragItemBlocked(storage, storageInventory, event, storageConfig);
            }
            if(storageConfig.isStorageItemsWhiteListEnabled() || storageConfig.isStorageItemsBlackListEnabled()) {
                DragItemCheckList(storage, storageInventory, event, storageConfig);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if(event.getClickedInventory() == null) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof StorageInventory) {
            StorageInventory storageInventory = (StorageInventory) holder;
            Storage storage = storageInventory.getStorage();
            StorageConfig storageConfig = core.getManagers().getStorageConfigManager().getStorageConfigById(storage.getStorageIdConfig());
            //SOUNDS
            if(storageConfig.isStorageSoundEnabled()){
                for(StorageSoundConfig soundConfig : storageConfig.getStorageSounds()){
                    if(soundConfig.getType().equals(StorageSoundConfig.Type.CLICK)){
                        if(soundConfig.getPagesToSlots().containsKey(storageInventory.getPage())){
                            if(soundConfig.getPagesToSlots().get(storageInventory.getPage()).size()>0){
                                if(event.getClickedInventory() != null && !event.getClickedInventory().getType().equals(InventoryType.PLAYER)) {
                                    if (soundConfig.getPagesToSlots().get(storageInventory.getPage()).contains(storageInventory.getPage())) {
                                        ((Player) event.getWhoClicked()).playSound(event.getWhoClicked().getLocation(), soundConfig.getSound(), soundConfig.getVolume(), soundConfig.getPitch());
                                    }
                                }
                            }
                            else {
                                ((Player)event.getWhoClicked()).playSound(event.getWhoClicked().getLocation(),soundConfig.getSound(), soundConfig.getVolume(),soundConfig.getPitch());
                            }
                        }
                    }
                }
            }
            if(event.getCurrentItem() != null && event.getClickedInventory().getType().equals(InventoryType.PLAYER) && event.getClick().isShiftClick()){
                ShiftClickItemCheck(storage, storageInventory, event, storageConfig);
                if(storageConfig.isStorageBlockItemEnabled()) {
                    if(storageInventory.getInventory().firstEmpty() != -1 && event.getCurrentItem() != null){

                        ShiftClickItemBlocked(storage,storageInventory,event,storageConfig);

                    }
                }
                if(storageConfig.isStorageItemsWhiteListEnabled() || storageConfig.isStorageItemsBlackListEnabled()) {
                    if(storageInventory.getInventory().firstEmpty() != -1 && event.getCurrentItem() != null){

                        ShiftClickItemCheckList(storage,storageInventory,event,storageConfig);

                    }
                }
            }
            //ITEMS INTERFACES & Item CheckList
            if (event.getClickedInventory() != null && !event.getClickedInventory().getType().equals(InventoryType.PLAYER)) {
                // Cancel event if clicked item is an interface item
                ItemStack clickedItem = event.getCurrentItem();
                ItemInterfaceManager itemInterfaceManager = core.getManagers().getItemInterfaceManager();
                if (clickedItem != null && (itemInterfaceManager.isItemInterface(clickedItem) || itemInterfaceManager.isItemInterfaceWithPlaceHolderItem(clickedItem))) {
                    event.setCancelled(true);
                    ItemInterface itemInterface = core.getManagers().getItemInterfaceManager().getItemInterfaceByItemStack(clickedItem);
                    if(itemInterface == null) itemInterface = core.getManagers().getItemInterfaceManager().getItemInterfaceByItemStackPlaceholder(clickedItem);
                    ClickItemInterface(storage,storageInventory,event,storageConfig,itemInterface);
                }
                ClickItemCheck(storage, storageInventory, event, storageConfig);
                if(storageConfig.isStorageBlockItemEnabled()){
                    ClickItemBlocked(storage, storageInventory, event, storageConfig);
                }
                if(storageConfig.isStorageItemsWhiteListEnabled() || storageConfig.isStorageItemsBlackListEnabled()) {
                    ClickItemCheckList(storage, storageInventory, event, storageConfig);
                }
            }
            //Events
            core.getManagers().getActionManager().callActionsEvent(EventEnum.CLICK_STORAGE_ITEM,storage,(Player) event.getWhoClicked(), event , storageInventory);
            ClickPageStorageEvent clickPageStorageEvent = new ClickPageStorageEvent(event,storageInventory);
            Bukkit.getPluginManager().callEvent(clickPageStorageEvent);

        }
    }
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof StorageInventory) {
            StorageInventory storageInventory = (StorageInventory) holder;
            Storage storage = storageInventory.getStorage();
            StorageConfig storageConfig = core.getManagers().getStorageConfigManager().getStorageConfigById(storage.getStorageIdConfig());

            //events
            core.getManagers().getActionManager().callActionsEvent(EventEnum.OPEN_STORAGE_PAGE,storage, (Player) event.getPlayer(), event, storageInventory);

            //SOUNDS
            if(storageConfig.isStorageSoundEnabled()){
                for(StorageSoundConfig soundConfig : storageConfig.getStorageSounds()){
                    if(soundConfig.getType().equals(StorageSoundConfig.Type.OPEN)){
                        if(soundConfig.getPagesToSlots().containsKey(storageInventory.getPage())){
                            ((Player)event.getPlayer()).playSound(event.getPlayer().getLocation(),soundConfig.getSound(), soundConfig.getVolume(),soundConfig.getPitch());
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof StorageInventory) {
            StorageInventory storageInventory = (StorageInventory) holder;
            Storage storage = storageInventory.getStorage();
            StorageConfig storageConfig = core.getManagers().getStorageConfigManager().getStorageConfigById(storage.getStorageIdConfig());

            Object[] objects = {event, storageInventory};

            //events
            core.getManagers().getActionManager().callActionsEvent(EventEnum.CLOSE_STORAGE_PAGE,storage, (Player) event.getPlayer(), objects);

            //SAVE STORAGE
            storage.closeStorage(storageInventory.getPage(), (Player) event.getPlayer());

            //SOUNDS
            if(storageConfig.isStorageSoundEnabled()){
                for(StorageSoundConfig soundConfig : storageConfig.getStorageSounds()){
                    if(soundConfig.getType().equals(StorageSoundConfig.Type.CLOSE)){
                        if(soundConfig.getPagesToSlots().containsKey(storageInventory.getPage())){
                            ((Player)event.getPlayer()).playSound(event.getPlayer().getLocation(),soundConfig.getSound(), soundConfig.getVolume(),soundConfig.getPitch());
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (waitingInput.containsKey(playerId)) {
            event.getPlayer().sendMessage(AdventureUtils.deserializeLegacy(core.getConfigDocumentYaml().getString("messages.storage.search_page_enter"),null));
            event.setCancelled(true);
            try {
                int pageNumber = Integer.parseInt(event.getMessage());
                WaitingInputData data = waitingInput.get(playerId);
                Storage storage = data.getStorage();
                if(pageNumber>0 && pageNumber <= storage.getStorageConfig().getPages()){
                    Bukkit.getScheduler().runTask(core,()-> storage.openStorage(event.getPlayer(), (pageNumber - 1)));
                }
                else {
                    event.getPlayer().sendMessage(AdventureUtils.deserializeLegacy(core.getConfigDocumentYaml().getString("messages.storage.search_page_invalid"),null));
                }
            } catch (NumberFormatException e) {
                event.getPlayer().sendMessage(AdventureUtils.deserializeLegacy(core.getConfigDocumentYaml().getString("messages.storage.search_page_invalid"),null));
            }
            waitingInput.remove(playerId);
            event.setCancelled(true);
        }
    }


    //CHECK ITEM
    public void ClickItemCheck(Storage storage,StorageInventory storageInventory,InventoryClickEvent event,StorageConfig storageConfig){
        HumanEntity humanEntity = event.getWhoClicked();
        ItemStack cursor = event.getCursor();

        if(cursor != null && !cursor.getType().equals(Material.AIR)){

            //ITEM STORAGE
            if(managers.getItemStorageManager().isItemStorage(cursor)){
                String[] src = managers.getItemStorageManager().getDataFromItemStack(cursor).split(":");
                if(src[1].equals(storage.getId())){
                    event.setCancelled(true);
                    return;
                }
                if(!managers.getItemStorageConfigManager().getItemStorageConfig(src[0]).getItemStoragePropertiesConfig().isStorageable()){
                    event.setCancelled(true);
                    return;
                }
            }
            //FURNITURE STORAGE
            if(managers.getFurnitureStorageManager().isShulker(cursor)){
                String[] src = managers.getFurnitureStorageManager().getShulkerData(cursor).split(":");
                managers.getFurnitureStorageConfigManager().findFurnitureStorageConfigById(src[1]).ifPresent(furnitureStorageC -> {
                    if(!furnitureStorageC.getFurnitureStorageProperties().isStorageable()){
                        event.setCancelled(true);
                        return;
                    }
                });
            }
            //BLOCK STORAGE
            if(managers.getBlockStorageManager().isShulker(cursor)){
                String[] src = managers.getBlockStorageManager().getShulkerData(cursor).split(":");
                managers.getBlockStorageConfigManager().findBlockStorageConfigById(src[1]).ifPresent(blockStorageC -> {
                    if(!blockStorageC.getBlockStorageProperties().isStorageable()){
                        event.setCancelled(true);
                        return;
                    }
                });
            }

        }
    }
    public void ShiftClickItemCheck(Storage storage,StorageInventory storageInventory,InventoryClickEvent event,StorageConfig storageConfig){

        HumanEntity humanEntity = event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = storageInventory.getInventory().firstEmpty();

        if(!clickedItem.getType().equals(Material.AIR)){

            //ITEM STORAGE
            if(managers.getItemStorageManager().isItemStorage(clickedItem)){
                String[] src = managers.getItemStorageManager().getDataFromItemStack(clickedItem).split(":");
                if(src[1].equals(storage.getId())){
                    event.setCancelled(true);
                    return;
                }
                if(!managers.getItemStorageConfigManager().getItemStorageConfig(src[0]).getItemStoragePropertiesConfig().isStorageable()){
                    event.setCancelled(true);
                    return;
                }
            }

            //FURNITURE STORAGE
            if(managers.getFurnitureStorageManager().isShulker(clickedItem)){
                String[] src = managers.getFurnitureStorageManager().getShulkerData(clickedItem).split(":");
                managers.getFurnitureStorageConfigManager().findFurnitureStorageConfigById(src[1]).ifPresent(furnitureStorageC -> {
                    if(!furnitureStorageC.getFurnitureStorageProperties().isStorageable()){
                        event.setCancelled(true);
                        return;
                    }
                });
            }
            //BLOCK STORAGE
            if(managers.getBlockStorageManager().isShulker(clickedItem)){
                String[] src = managers.getBlockStorageManager().getShulkerData(clickedItem).split(":");
                managers.getBlockStorageConfigManager().findBlockStorageConfigById(src[1]).ifPresent(blockStorageC -> {
                    if(!blockStorageC.getBlockStorageProperties().isStorageable()){
                        event.setCancelled(true);
                        return;
                    }
                });
            }
        }

    }
    public void DragItemCheck(Storage storage,StorageInventory storageInventory,InventoryDragEvent event,StorageConfig storageConfig){

        HumanEntity humanEntity = event.getWhoClicked();
        ItemStack cursor = event.getCursor();
        if(cursor == null){
            cursor = (ItemStack) (event.getNewItems().values().toArray())[0];
        }
        if(cursor != null && !cursor.getType().equals(Material.AIR)){

            //ITEM STORAGE
            if(managers.getItemStorageManager().isItemStorage(cursor)){
                String[] src = managers.getItemStorageManager().getDataFromItemStack(cursor).split(":");
                if(src[1].equals(storage.getId())){
                    event.setCancelled(true);
                    return;
                }
                if(!managers.getItemStorageConfigManager().getItemStorageConfig(src[0]).getItemStoragePropertiesConfig().isStorageable()){
                    event.setCancelled(true);
                    return;
                }
            }

            //FURNITURE STORAGE
            if(managers.getFurnitureStorageManager().isShulker(cursor)){
                String[] src = managers.getFurnitureStorageManager().getShulkerData(cursor).split(":");
                managers.getFurnitureStorageConfigManager().findFurnitureStorageConfigById(src[1]).ifPresent(furnitureStorageC -> {
                    if(!furnitureStorageC.getFurnitureStorageProperties().isStorageable()){
                        event.setCancelled(true);
                        return;
                    }
                });
            }
            //BLOCK STORAGE
            if(managers.getBlockStorageManager().isShulker(cursor)){
                String[] src = managers.getBlockStorageManager().getShulkerData(cursor).split(":");
                managers.getBlockStorageConfigManager().findBlockStorageConfigById(src[1]).ifPresent(blockStorageC -> {
                    if(!blockStorageC.getBlockStorageProperties().isStorageable()){
                        event.setCancelled(true);
                        return;
                    }
                });
            }

        }

    }


    public void ClickItemCheckList(Storage storage,StorageInventory storageInventory,InventoryClickEvent event,StorageConfig storageConfig){

        HumanEntity humanEntity = event.getWhoClicked();
        ItemStack cursor = event.getCursor();

        if(cursor != null && !cursor.getType().equals(Material.AIR)){

            if(storageConfig.isStorageItemsWhiteListEnabled()){
                if(!storage.isItemInList(cursor,event.getSlot(),storageInventory.getPage(), Storage.ListType.WHITELIST,storageConfig)){
                    AdventureUtils.playerMessage(storageConfig.getWhiteListMessage(), (Player) humanEntity);
                    event.setCancelled(true);
                }
            }
            if(storageConfig.isStorageItemsBlackListEnabled()){
                if(storage.isItemInList(cursor,event.getSlot(),storageInventory.getPage(), Storage.ListType.BLACKLIST,storageConfig)){
                    AdventureUtils.playerMessage(storageConfig.getBlackListMessage(), (Player) humanEntity);
                    event.setCancelled(true);
                }
            }

        }

    }
    public void ShiftClickItemCheckList(Storage storage,StorageInventory storageInventory,InventoryClickEvent event,StorageConfig storageConfig){

        HumanEntity humanEntity = event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = storageInventory.getInventory().firstEmpty();

        if(!clickedItem.getType().equals(Material.AIR)){

            if(storageConfig.isStorageItemsWhiteListEnabled()){
                if(!storage.isItemInList(clickedItem,slot,storageInventory.getPage(), Storage.ListType.WHITELIST,storageConfig)){
                    AdventureUtils.playerMessage(storageConfig.getWhiteListMessage(), (Player) humanEntity);
                    event.setCancelled(true);
                }
            }
            if(storageConfig.isStorageItemsBlackListEnabled()){
                if(storage.isItemInList(clickedItem,slot,storageInventory.getPage(), Storage.ListType.BLACKLIST,storageConfig)){
                    AdventureUtils.playerMessage(storageConfig.getBlackListMessage(), (Player) humanEntity);
                    event.setCancelled(true);
                }
            }

        }

    }

    public void DragItemCheckList(Storage storage,StorageInventory storageInventory,InventoryDragEvent event,StorageConfig storageConfig){

        HumanEntity humanEntity = event.getWhoClicked();
        ItemStack cursor = event.getCursor();
        if(cursor == null){
            cursor = (ItemStack) (event.getNewItems().values().toArray())[0];
        }
        if(cursor != null && !cursor.getType().equals(Material.AIR)){

            if(storageConfig.isStorageItemsWhiteListEnabled()){

                for(Integer s : event.getRawSlots()){
                    if(!storage.isItemInList(cursor,s,storageInventory.getPage(), Storage.ListType.WHITELIST,storageConfig)){
                        AdventureUtils.playerMessage(storageConfig.getWhiteListMessage(), (Player) humanEntity);
                        event.setCancelled(true);
                    }
                }
            }
            if(storageConfig.isStorageItemsBlackListEnabled()){

                for(Integer s : event.getRawSlots()){
                    if(storage.isItemInList(cursor,s,storageInventory.getPage(), Storage.ListType.BLACKLIST,storageConfig)){
                        AdventureUtils.playerMessage(storageConfig.getBlackListMessage(), (Player) humanEntity);
                        event.setCancelled(true);
                    }
                }

            }

        }

    }

    public void ClickItemBlocked(Storage storage,StorageInventory storageInventory,InventoryClickEvent event,StorageConfig storageConfig){

        HumanEntity humanEntity = event.getWhoClicked();
        ItemStack cursor = event.getCursor();

        if(cursor != null && !cursor.getType().equals(Material.AIR)){

            if(storageConfig.isStorageBlockItemEnabled()){
                ArrayList<Object> objects = storage.isBlocked(event.getSlot(), storageInventory.getPage(),storageConfig);
                if((boolean) objects.get(0)){
                    if(objects.get(1) != null){
                        AdventureUtils.playerMessage((String) objects.get(1), (Player) humanEntity);
                    }
                    event.setCancelled(true);
                }
            }
        }

    }

    public void ShiftClickItemBlocked(Storage storage,StorageInventory storageInventory,InventoryClickEvent event,StorageConfig storageConfig){

        HumanEntity humanEntity = event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = storageInventory.getInventory().firstEmpty();

        if(!clickedItem.getType().equals(Material.AIR)){

            if(storageConfig.isStorageBlockItemEnabled()){
                ArrayList<Object> objects = storage.isBlocked(slot, storageInventory.getPage(),storageConfig);
                if((boolean) objects.get(0)){
                    if(objects.get(1) != null){
                        AdventureUtils.playerMessage((String) objects.get(1), (Player) humanEntity);
                    }
                    event.setCancelled(true);
                }
            }

        }

    }
    public void DragItemBlocked(Storage storage,StorageInventory storageInventory,InventoryDragEvent event,StorageConfig storageConfig){

        HumanEntity humanEntity = event.getWhoClicked();
        ItemStack cursor = event.getCursor();
        if(cursor == null){
            cursor = (ItemStack) (event.getNewItems().values().toArray())[0];
        }
        if(cursor != null && !cursor.getType().equals(Material.AIR)){

            if(storageConfig.isStorageBlockItemEnabled()){

                for(Integer s : event.getRawSlots()){
                    ArrayList<Object> objects = storage.isBlocked(s, storageInventory.getPage(),storageConfig);
                    if((boolean) objects.get(0)){
                        if(objects.get(1) != null){
                            AdventureUtils.playerMessage((String) objects.get(1), (Player) humanEntity);
                        }
                        event.setCancelled(true);
                    }
                }
            }

        }

    }
    private void ClickItemInterface(Storage storage,StorageInventory storageInventory,InventoryClickEvent event,StorageConfig storageConfig,ItemInterface itemInterface){
        switch (itemInterface.getItemInterfaceType()){
            case ACTION -> {
                Player player = (Player) event.getWhoClicked();
                ActionItemProperties actionItemProperties = (ActionItemProperties) itemInterface.getProperties();
                ClickStorageItemInterfaceAction eventAction = new ClickStorageItemInterfaceAction(event,itemInterface,storageInventory);
                Bukkit.getScheduler().runTaskAsynchronously(core, () -> {
                    Action action = core.getManagers().getActionManager().createAction(storage, actionItemProperties.getActionId(), player, eventAction);
                    action.execute();
                });
            }
            case PLACEHOLDER -> {
                Player player = (Player) event.getWhoClicked();
                PlaceHolderItemProperties placeHolderItemProperties = (PlaceHolderItemProperties) itemInterface.getProperties();
                ItemStack clickedItem = event.getCurrentItem();
                ItemMeta clickedItemMeta = clickedItem.getItemMeta();
                PersistentDataContainer persistentDataContainer = clickedItemMeta.getPersistentDataContainer();
                if(persistentDataContainer.has(PlaceHolderItemProperties.NAMESPACED_KEY, PersistentDataType.STRING)){
                    if(event.getCursor() != null && !event.getCursor().getType().equals(Material.AIR)) {
                        ItemStack itemCursor = event.getCursor();
                        if(!clickedItem.getType().equals(itemCursor.getType())) return;
                        ItemStack itemClickedClone = clickedItem.clone();
                        ItemMeta itemMetaClone = itemClickedClone.getItemMeta();
                        itemMetaClone.getPersistentDataContainer().remove(PlaceHolderItemProperties.NAMESPACED_KEY);
                        itemMetaClone.getPersistentDataContainer().remove(PlaceHolderItemProperties.NAMESPACED_KEY_INTERFACE);
                        itemClickedClone.setItemMeta(itemMetaClone);
                        if(!itemClickedClone.isSimilar(itemCursor)) return;
                        if(itemClickedClone.getMaxStackSize()==itemClickedClone.getAmount() || itemCursor.getAmount()>itemClickedClone.getMaxStackSize()) return;
                        if((itemClickedClone.getAmount() + itemCursor.getAmount())>=itemClickedClone.getMaxStackSize()){
                            clickedItem.setAmount(itemClickedClone.getMaxStackSize());
                            if(((itemClickedClone.getAmount() + itemCursor.getAmount()) - itemClickedClone.getMaxStackSize())==0) player.setItemOnCursor(null);
                            else itemCursor.setAmount(((itemClickedClone.getAmount() + itemCursor.getAmount()) - itemClickedClone.getMaxStackSize()));
                            return;
                        }
                        player.setItemOnCursor(null);
                        clickedItem.setAmount((itemClickedClone.getAmount() + itemCursor.getAmount()));
                        return;
                    };
                    if(event.getClick().isRightClick() && clickedItem.getAmount()>1){
                        int amount = clickedItem.getAmount() / 2;
                        clickedItem.setAmount(clickedItem.getAmount() - amount);
                        ItemStack item = clickedItem.clone();
                        ItemMeta itemMeta = item.getItemMeta();
                        PersistentDataContainer itemPersistentDataContainer = itemMeta.getPersistentDataContainer();
                        itemPersistentDataContainer.remove(PlaceHolderItemProperties.NAMESPACED_KEY);
                        itemPersistentDataContainer.remove(PlaceHolderItemProperties.NAMESPACED_KEY_INTERFACE);
                        item.setItemMeta(itemMeta);
                        item.setAmount(amount);
                        player.setItemOnCursor(item);
                        return;
                    }
                    ItemStack item = clickedItem.clone();
                    ItemStack itemInterfaceCloned = itemInterface.getItemStack();
                    clickedItem.setType(itemInterfaceCloned.getType());
                    clickedItem.setItemMeta(itemInterfaceCloned.getItemMeta());
                    clickedItem.setAmount(itemInterfaceCloned.getAmount());
                    ItemMeta itemMeta = item.getItemMeta();
                    PersistentDataContainer itemPersistentDataContainer = itemMeta.getPersistentDataContainer();
                    itemPersistentDataContainer.remove(PlaceHolderItemProperties.NAMESPACED_KEY);
                    itemPersistentDataContainer.remove(PlaceHolderItemProperties.NAMESPACED_KEY_INTERFACE);
                    item.setItemMeta(itemMeta);
                    player.setItemOnCursor(item);
                    return;
                }
                if(event.getCursor() == null || event.getCursor().getType().equals(Material.AIR)) return;
                ItemStack itemCursor = event.getCursor();
                String itemId = Mechanics.getInstance().getManager().getAdapterManager().getAdapterID(itemCursor);
                if(placeHolderItemProperties.isWhitelistEnabled() && !placeHolderItemProperties.getWhitelistItems().contains(itemId)) return;
                if(placeHolderItemProperties.isBlacklistEnabled() && placeHolderItemProperties.getWhitelistItems().contains(itemId)) return;
                clickedItem.setType(itemCursor.getType());
                clickedItem.setAmount(itemCursor.getAmount());
                ItemMeta itemMeta = itemCursor.getItemMeta();
                itemMeta.getPersistentDataContainer().set(PlaceHolderItemProperties.NAMESPACED_KEY,PersistentDataType.STRING,itemId);
                itemMeta.getPersistentDataContainer().set(PlaceHolderItemProperties.NAMESPACED_KEY_INTERFACE,PersistentDataType.STRING, itemInterface.getId());
                clickedItem.setItemMeta(itemMeta);
                player.setItemOnCursor(null);
            }
            case CLEAN_ITEM -> {
                Player player = (Player) event.getWhoClicked();
                CleanItemProperties cleanItemProperties = (CleanItemProperties) itemInterface.getProperties();
                for(int page : cleanItemProperties.getPages()){
                    for(int slot : cleanItemProperties.getSlots()){
                        storage.clearSlotWithRestrictions(page,slot);
                    }
                }
            }
            case SEARCH_ITEM -> {
                Player player = (Player) event.getWhoClicked();
                SearchItemProperties properties = (SearchItemProperties) itemInterface.getProperties();
                core.getManagers().getInventoryManager().getSearchItemInventoryManager().openSearchItemMenu(player,properties.getInvId(),properties.getInvResultId(),storage,properties.getSearchType());
            }
            case SEARCH_PAGE -> {
                Player player = (Player) event.getWhoClicked();
                player.closeInventory();
                player.sendMessage(AdventureUtils.deserializeLegacy(core.getConfigDocumentYaml().getString("messages.storage.search_page_enter"),null));
                UUID playerId = player.getUniqueId();
                waitingInput.put(playerId, new WaitingInputData(storage, storageInventory.getPage()));

                AtomicLong timer = new AtomicLong(0);
                Bukkit.getScheduler().runTaskTimerAsynchronously(core, task -> {
                    if (timer.incrementAndGet() >= 20 * 10 || !waitingInput.containsKey(playerId)) {
                        waitingInput.remove(playerId);
                        task.cancel();
                    }
                }, 0L, 3L);
            }
            case NEXT_PAGE -> {
                if(storageInventory.getPage()<(storageConfig.getPages() - 1)){
                    storage.openStorage((Player) event.getWhoClicked(),storageInventory.getPage() + 1);
                }
            }
            case BACK_PAGE -> {
                if(storageInventory.getPage() > 0){
                    storage.openStorage((Player) event.getWhoClicked(),storageInventory.getPage() - 1);
                }
            }
            default ->{

            }
        }
    }

    public Storage getStorage(String id) {
        if(storageMap.containsKey(id)) return storageMap.get(id);
        if(dataManager.getStorageManagerData().existStorageData(id)) return loadStorage(id);
        return null;
    }

    public void removeStorageFromInputWait(String id){
        for(Map.Entry<UUID,WaitingInputData> entry : waitingInput.entrySet()){
            if(id.equals(entry.getValue().getStorage().getId())) waitingInput.remove(entry.getKey());
        }
    }

    public void removeStorage(String id) {
        removeStorageFromInputWait(id);
        if(storageMap.containsKey(id)) storageMap.remove(id);
        if(dataManager.getStorageManagerData().existStorageData(id)) dataManager.getStorageManagerData().removeStorageData(id);
    }

    public boolean storageExists(String id) {
        if(storageMap.containsKey(id)) return true;
        if(dataManager.getStorageManagerData().existStorageData(id)) return true;
        return false;
    }
    //DATA
    public void saveStorageNoRemove(Storage storage){
        dataManager.getStorageManagerData().saveStorageData(storage);
    }
    public void saveStorage(Storage storage, SaveCause saveCause){
        String id = storage.getId();
        if(storage.getStorageConfig().getStorageProperties().isTempStorage()) {
            storageMap.remove(id);
            return;
        }
        if(storageMap.containsKey(id)) storageMap.remove(id);
        if(saveCause.equals(SaveCause.NORMAL_SAVE)){
            Bukkit.getScheduler().runTask(core, storage::closeAllInventory);
        }
        dataManager.getStorageManagerData().saveStorageData(storage);
    }
    public Storage loadStorage(String id){
        if(!storageMap.containsKey(id)){
            Storage storage = dataManager.getStorageManagerData().loadStorageData(id);
            if(storage == null) return null;
            storageMap.put(storage.getId(),storage);
            return storage;
        }
        return null;
    }
    public void saveAllStorages(){
        for(Storage storage : storageMap.values()){
            saveStorageNoRemove(storage);
        }
    }
    public void stop(){
        while(!storageMap.isEmpty()){
            Storage storage = storageMap.values().stream().toList().get(0);
            saveStorage(storage,SaveCause.STOPPING_SAVE);
        }
    }

    public Map<String, Storage> getStorageMap() {
        return storageMap;
    }
}