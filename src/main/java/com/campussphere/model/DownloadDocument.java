package com.campussphere.model;

import java.util.List;

public record DownloadDocument(String title,String reference,List<Entry> entries,String note) {
    public DownloadDocument { entries=List.copyOf(entries); }
    public record Entry(String label,String value) { }
}
