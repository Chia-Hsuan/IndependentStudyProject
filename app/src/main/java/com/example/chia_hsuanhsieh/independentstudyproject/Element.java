package com.example.chia_hsuanhsieh.independentstudyproject;

import java.io.File;
import java.io.Serializable;

public class Element implements Serializable {

    private String path;
    private String name;
    private long size;
    private HashTable hashTable;

    public Element(String topPath, File file) {
        this.path = file.getPath();
        this.name = file.getName();
        if (file.isDirectory()) {
            hashTable = new HashTable(topPath, this.path);
        } else {
            this.size = file.length();
        }
    }

    public boolean isDirectory() {
        if (hashTable != null) {
            return true;
        } else {
            return false;
        }
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public void setHashTable(HashTable table) {
        this.hashTable = table;
    }

    public HashTable getHashTable() {
        return hashTable;
    }

}