package utils.io;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Leaf {
    private String title;
    private String url;
    private Instant addDate;
    private Instant lastModified;
    private List<Leaf> children;
    private boolean isFolder;
    private String dataCover;

    public Leaf() {
        this.children = new ArrayList<>();
    }

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public Instant getAddDate() { return addDate; }
    public void setAddDate(Instant addDate) { this.addDate = addDate; }
    
    public Instant getLastModified() { return lastModified; }
    public void setLastModified(Instant lastModified) { this.lastModified = lastModified; }
    
    public List<Leaf> getChildren() { return children; }
    public void addChild(Leaf child) { this.children.add(child); }
    
    public boolean isFolder() { return isFolder; }
    public void setFolder(boolean folder) { isFolder = folder; }
    
    public String getDataCover() { return dataCover; }
    public void setDataCover(String dataCover) { this.dataCover = dataCover; }
} 