package com.example.chia_hsuanhsieh.independentstudyproject;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

public class HashTable extends ArrayList< List<Element> > {

    private String topPath;
    private String tablePath;
    private int bucket_size = 128;
    private HashTable scarceHashTable;
    private long total_size = 0;

    public HashTable(String topPath, String tablePath) {
        super();
        this.topPath = topPath;
        this.tablePath = tablePath;
        for(int i=0; i<bucket_size; i++) {
            List<Element> temp = new LinkedList<Element>();
            this.add(temp);
        }
    }

    public int placeNonexistentFile(Element element) {
        int elementIndex = ((int) element.getName().charAt(0)-' ') % this.size();
        for (int i=0; i<this.get(elementIndex).size(); i++) {
            if (this.get(elementIndex).get(i).getName().compareTo(element.getName()) == 0) {
                return (-1-i);
            } else if (this.get(elementIndex).get(i).getName().compareTo(element.getName()) > 0) {
                return i;
            }
        }
        return this.get(elementIndex).size();
    }

    public void addFile(File file_add) {
        Element element = new Element(this.topPath, file_add);
        if (placeNonexistentFile(element) >= 0) {
            int elementIndex = ((int) element.getName().charAt(0) - ' ') % this.size();
            if (this.get(elementIndex).isEmpty()) {
                this.get(elementIndex).add(element);
            } else {
                for (int i=0; i<this.get(elementIndex).size(); i++) {
                    if (this.get(elementIndex).get(i).getName().compareTo(element.getName()) > 0) {
                        this.get(elementIndex).add(i, element);
                        break;
                    }
                    if (i == this.get(elementIndex).size()-1) {
                        this.get(elementIndex).add(element);
                        break;
                    }
                }
            }
            total_size = total_size + element.getSize();
        }
    }

    public void addSubHashTable(HashTable subTable, File file) {
        int fileIndex = ((int) file.getName().charAt(0)-' ') % this.size();
        this.get(fileIndex).get(this.get(fileIndex).size()-1).setHashTable(subTable);
        total_size = total_size + subTable.getTotalSize();
    }

    public void removeFile(File file) {
        Element element = new Element(this.topPath, file);
        int elementIndex = ((int)file.getName().charAt(0)-' ') % this.size();
        if (placeNonexistentFile(element) < 0) {
            this.get(elementIndex).remove(element);
        }
    }

    public void printTable() {
        System.out.println("print Table for " + tablePath);
        for (int i=0; i<this.size(); i++) {
            if (!this.get(i).isEmpty()) {
                System.out.print("["+i+"] : ");
                for (int j=0; j<this.get(i).size(); j++) {
                    System.out.print(this.get(i).get(j).getName() + "  " + this.get(i).get(j).getSize() + " -> ");
                }
                System.out.println("null");
            }
        }
        System.out.println("subTable!!!!");
        for (int i=0; i<this.size(); i++) {
            if (!this.get(i).isEmpty()) {
                for (int j=0; j<this.get(i).size(); j++) {
                    if (this.get(i).get(j).isDirectory()) {
                        this.get(i).get(j).getHashTable().printTable();
                    }
                }
            }
        }
    }

    public String getTablePath() {
        return tablePath;
    }

    public  HashTable getScarceHashTable() {
        return scarceHashTable;
    }

    public void compare(HashTable serverHashTable) {
        scarceHashTable = new HashTable(this.topPath, this.tablePath);
        for (int i=0; i<serverHashTable.size(); i++) {
            for (int j=0; j<serverHashTable.get(i).size(); j++) {
                if (serverHashTable.get(i).get(j).isDirectory()) {
                    boolean subTableExist = false;
                    int k;
                    for (k=0; k<this.get(i).size(); k++) {
                        if (this.get(i).get(k).isDirectory()) {
                            if (this.get(i).get(k).getName().compareTo(serverHashTable.get(i).get(j).getName()) == 0) {
                                subTableExist = true;
                                break;
                            } else if (this.get(i).get(k).getName().compareTo(serverHashTable.get(i).get(j).getName()) > 0) {
                                break;
                            }
                        }
                    }
                    if (subTableExist) {
                        this.get(i).get(k).getHashTable().compare(serverHashTable.get(i).get(j).getHashTable());
                        this.scarceHashTable.get(i).add(this.get(i).get(k));
                        this.scarceHashTable.get(i).get(this.scarceHashTable.get(i).size()-1).setHashTable(this.get(i).get(k).getHashTable().scarceHashTable);
                    } else {
                        this.scarceHashTable.get(i).add(serverHashTable.get(i).get(j));
                        this.scarceHashTable.total_size = this.scarceHashTable.total_size + serverHashTable.get(i).get(j).getHashTable().getTotalSize();
                    }
                } else {
                    int position = placeNonexistentFile(serverHashTable.get(i).get(j));
                    if (position >= 0) {
                        this.scarceHashTable.get(i).add(serverHashTable.get(i).get(j));
                        this.scarceHashTable.total_size = this.scarceHashTable.total_size + serverHashTable.get(i).get(j).getSize();
                    }
                }
            }
        }
    }

    public long getTotalSize() {
        return total_size;
    }

}