package storage.dao;

import model.Item;

// Pairs a DB-generated item_id with its Item object.
// Replaces the old ItemLog.ItemEntry - same contract, lives in dao package.
public class ItemEntry {
    private final int  id;
    private final Item item;

    public ItemEntry(int id, Item item) {
        this.id   = id;
        this.item = item;
    }

    public int  getId()   { return id;   }
    public Item getItem() { return item; }
}